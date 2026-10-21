import hashlib
import json
import re
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from typing import Literal

import httpx2
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from .config import Settings
from .db import Device, Feedback, make_engine
from .prompts import allowed
from .security import DeviceDep, RateLimiter, admin_auth, audit, client_key, default_jwks, device_auth, new_token, reviewer_auth, sha256

type Assist = Callable[[str, str, int], Awaitable[str]]
NAME = re.compile(r"^[a-z0-9_.-]{1,64}$")


class EnrollIn(BaseModel):
    code: str = Field(min_length=8, max_length=128)


class AssistIn(BaseModel):
    system: str = Field(max_length=4000)
    user: str
    max_tokens: int = Field(default=800, ge=1, le=2000)


class FeedbackIn(BaseModel):
    """Labels only. extra="forbid" rejects landmark, picture or text fields outright."""

    model_config = ConfigDict(extra="forbid")
    consent: Literal[True]
    predicted: str = Field(max_length=64)
    outcome: Literal["confirmed", "corrected", "rejected"]
    confirmed: str | None = Field(default=None, max_length=64)
    model_version: str = Field(default="", max_length=32)


def upstream_assist(s: Settings) -> Assist:
    async def run(system: str, user: str, max_tokens: int) -> str:
        if not (s.assist_url and s.assist_key):
            raise HTTPException(503, "assistant not configured")
        body = {"model": s.assist_model, "temperature": 0.2, "max_tokens": max_tokens,
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}
        async with httpx2.AsyncClient(timeout=30) as c:
            r = await c.post(s.assist_url, json=body, headers={"Authorization": f"Bearer {s.assist_key}"})
        if r.status_code >= 300:
            raise HTTPException(502, "assistant failed")
        # A reasoning model can spend its whole budget thinking and return no content.
        text = (r.json()["choices"][0]["message"].get("content") or "").strip()
        if not text:
            raise HTTPException(502, "assistant returned nothing")
        return text
    return run


def create_app(settings: Settings | None = None, assist: Assist | None = None, jwks=default_jwks) -> FastAPI:
    s = settings or Settings()
    run_assist = assist or upstream_assist(s)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        engine = make_engine(s.database_url, s.require_persistent_db)
        app.state.sessions = sessionmaker(engine, expire_on_commit=False)
        yield
        engine.dispose()

    app = FastAPI(title="Sign Setu gateway", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
    app.state.settings = s
    app.state.limiter = RateLimiter(s.rate_per_minute)
    app.state.enroll_limiter = RateLimiter(s.enroll_per_minute)
    app.state.enroll_failures = RateLimiter(s.enroll_failures_per_minute)
    app.state.jwks = jwks
    device_only = [Depends(device_auth)]

    @app.get("/healthz")
    def healthz():
        return {"ok": True}

    @app.post("/v1/enroll")
    def enroll(body: EnrollIn, request: Request):
        app.state.enroll_limiter.check(client_key(request, s.trusted_proxy_hops))
        if app.state.enroll_failures.exceeded("all"):
            raise app.state.enroll_failures.refuse("all")
        code_hash = sha256(body.code)
        if code_hash not in s.enroll_hashes:
            app.state.enroll_failures.check("all")
            if app.state.enroll_failures.exceeded("all"):
                # The wrong code that reaches the cap. Later requests are refused above without touching the database.
                with app.state.sessions() as db:
                    audit(db, "enroll.locked")
                    db.commit()
            raise HTTPException(403, "bad enrolment code")
        token, dev_id = new_token(), new_token()[:32]
        with app.state.sessions() as db:
            db.add(Device(id=dev_id, token_hash=sha256(token), code_hash=code_hash))
            audit(db, "enroll", dev_id)
            try:
                db.commit()
            except IntegrityError:
                raise HTTPException(403, "bad enrolment code") from None
        return {"device_id": dev_id, "token": token}

    # A model file is hashed once per (size, mtime), not on every download.
    verified: dict[tuple[str, int, int], str] = {}

    def file_sha256(path) -> str:
        st = path.stat()
        key = (str(path), st.st_size, st.st_mtime_ns)
        if key not in verified:
            with path.open("rb") as f:
                verified[key] = hashlib.file_digest(f, "sha256").hexdigest()
        return verified[key]

    def manifest() -> list[dict]:
        path = s.model_dir / "manifest.json"
        return json.loads(path.read_text()) if path.is_file() else []

    @app.get("/v1/models", dependencies=device_only)
    def models():
        return {"models": manifest()}

    @app.get("/v1/models/{name}", dependencies=device_only)
    def model_file(name: str):
        entry = next((m for m in manifest() if m["name"] == name), None)
        path = s.model_dir / name
        if not NAME.match(name) or entry is None or not path.is_file():
            raise HTTPException(404, "no such model")
        if file_sha256(path) != entry["sha256"]:
            raise HTTPException(500, "model file does not match its manifest")
        return FileResponse(path, media_type="application/octet-stream")

    @app.post("/v1/assist")
    async def assist_route(body: AssistIn, dev: DeviceDep):
        if len(body.user) > s.assist_max_chars:
            raise HTTPException(413, "too long")
        if not allowed(body.system):
            raise HTTPException(403, "prompt not allowed")
        text = await run_assist(body.system, body.user, body.max_tokens)
        with app.state.sessions() as db:
            audit(db, "assist", dev.id)  # the text itself is never stored
            db.commit()
        return {"text": text}

    @app.post("/v1/feedback", status_code=201)
    def feedback(body: FeedbackIn, dev: DeviceDep):
        with app.state.sessions() as db:
            db.add(Feedback(device_id=dev.id, **body.model_dump(exclude={"consent"})))
            audit(db, "feedback", dev.id)
            db.commit()
        return {}

    @app.get("/v1/admin/feedback", dependencies=[Depends(reviewer_auth)])
    def admin_feedback(limit: int = 200):
        with app.state.sessions() as db:
            rows = db.scalars(select(Feedback).order_by(Feedback.id.desc()).limit(min(limit, 1000))).all()
            audit(db, "admin.feedback")
            db.commit()
        return [{"predicted": r.predicted, "outcome": r.outcome, "confirmed": r.confirmed,
                 "model_version": r.model_version, "at": r.created_at.isoformat()} for r in rows]

    @app.get("/v1/admin/devices", dependencies=[Depends(admin_auth)])
    def admin_devices():
        with app.state.sessions() as db:
            rows = db.scalars(select(Device).order_by(Device.created_at.desc())).all()
        return [{"id": d.id, "created_at": d.created_at.isoformat(), "revoked": d.revoked} for d in rows]

    @app.post("/v1/admin/devices/{device_id}/revoke", dependencies=[Depends(admin_auth)])
    def admin_revoke(device_id: str):
        with app.state.sessions() as db:
            dev = db.get(Device, device_id)
            if dev is None:
                raise HTTPException(404, "no such device")
            dev.revoked = True
            audit(db, "admin.revoke", device_id)
            db.commit()
        return {}

    return app
