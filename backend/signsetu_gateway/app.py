import hashlib
import json
import re
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from typing import Any, Literal

import httpx2
from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from .config import Settings
from .db import Audit, Device, Feedback, make_engine
from .prompts import allowed
from .security import DeviceDep, RateLimiter, admin_auth, audit, client_key, default_jwks, device_auth, new_token, reviewer_auth, sha256

type Assist = Callable[[str, str, int], Awaitable[str]]
NAME = re.compile(r"^[a-z0-9_.-]{1,64}$")
# The largest legitimate body is an assist request with a 1,000,000-character user turn. Two megabytes leaves room
# for JSON escaping without letting a caller make the gateway buffer an unbounded request before Pydantic sees it.
MAX_BODY_BYTES = 2_000_000


class BodyLimitMiddleware:
    """Refuses a body over the cap before routing, including chunked bodies with no content-length."""

    def __init__(self, app: Any, max_bytes: int):
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope: dict, receive: Any, send: Any) -> None:
        if scope["type"] != "http" or scope.get("method") not in ("POST", "PUT", "PATCH"):
            await self.app(scope, receive, send)
            return
        chunks: list[dict] = []
        total = 0
        while True:
            message = await receive()
            if message["type"] != "http.request":
                chunks.append(message)
                break
            total += len(message.get("body", b""))
            if total > self.max_bytes:
                await send({"type": "http.response.start", "status": 413,
                            "headers": [(b"content-type", b"application/json")]})
                await send({"type": "http.response.body", "body": b'{"detail":"too large"}'})
                return
            chunks.append(message)
            if not message.get("more_body"):
                break
        pending = iter(chunks)

        async def replay():
            try:
                return next(pending)
            except StopIteration:
                return await receive()

        await self.app(scope, replay, send)


class EnrollIn(BaseModel):
    code: str = Field(min_length=8, max_length=128)


class AssistIn(BaseModel):
    system: str = Field(max_length=4000)
    # The runtime cap below is configurable and answers 413; this one only stops a megabyte body being parsed.
    user: str = Field(max_length=1_000_000)
    max_tokens: int = Field(default=800, ge=1, le=2000)


class FeedbackIn(BaseModel):
    """Labels only. extra="forbid" rejects landmark, picture or text fields outright."""

    model_config = ConfigDict(extra="forbid")
    consent: Literal[True]
    predicted: str = Field(max_length=64)
    outcome: Literal["confirmed", "corrected", "rejected"]
    confirmed: str | None = Field(default=None, max_length=64)
    model_version: str = Field(default="", max_length=32)

    @model_validator(mode="after")
    def a_correction_says_what_it_was_corrected_to(self):
        # A corrected row without the correction teaches a future model nothing, so it is refused at the edge.
        if self.outcome == "corrected" and not (self.confirmed or "").strip():
            raise ValueError("corrected feedback needs the confirmed label")
        return self


def upstream_assist(s: Settings) -> Assist:
    async def run(system: str, user: str, max_tokens: int) -> str:
        if not (s.assist_url and s.assist_key):
            raise HTTPException(503, "assistant not configured")
        body = {"model": s.assist_model, "temperature": 0.2, "max_tokens": max_tokens,
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}
        try:
            async with httpx2.AsyncClient(timeout=30) as c:
                r = await c.post(s.assist_url, json=body, headers={"Authorization": f"Bearer {s.assist_key}"})
        except httpx2.TimeoutException as e:
            raise HTTPException(504, "assistant timed out") from e
        except httpx2.HTTPError as e:
            raise HTTPException(502, "assistant unreachable") from e
        if r.status_code >= 300:
            raise HTTPException(502, "assistant failed")
        # A reasoning model can spend its whole budget thinking, return no content, or send a malformed body.
        try:
            content = r.json()["choices"][0]["message"].get("content")
        except (ValueError, KeyError, IndexError, TypeError, AttributeError) as e:
            raise HTTPException(502, "assistant returned an invalid response") from e
        answer = (content or "").strip()
        if not answer:
            raise HTTPException(502, "assistant returned nothing")
        return answer
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
    app.state.enroll_failures_all = RateLimiter(s.enroll_failures_global_per_minute)
    app.state.jwks = jwks
    device_only = [Depends(device_auth)]
    app.add_middleware(BodyLimitMiddleware, max_bytes=MAX_BODY_BYTES)

    @app.get("/healthz")
    def healthz():
        # Liveness only: a database blip must not make the host kill a process that is still serving the app.
        return {"ok": True}

    @app.get("/readyz")
    def readyz():
        try:
            with app.state.sessions() as db:
                db.execute(text("SELECT 1"))
        except Exception as e:
            raise HTTPException(503, "database unavailable") from e
        return {"ok": True}

    @app.post("/v1/enroll")
    def enroll(body: EnrollIn, request: Request):
        who = client_key(request, s.trusted_proxy_hops)
        app.state.enroll_limiter.check(who)
        # Wrong codes lock out the address that sent them; a much higher cap across all callers bounds guessing from
        # many addresses without letting one caller lock every officer out.
        for limiter, key in ((app.state.enroll_failures, who), (app.state.enroll_failures_all, "all")):
            if limiter.exceeded(key):
                raise limiter.refuse(key)
        code_hash = sha256(body.code)
        if code_hash not in s.enroll_hashes:
            app.state.enroll_failures.check(who)
            app.state.enroll_failures_all.check("all")
            if app.state.enroll_failures.exceeded(who) or app.state.enroll_failures_all.exceeded("all"):
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
        if not path.is_file():
            return []
        try:
            entries = json.loads(path.read_text())
        except (OSError, ValueError) as e:
            raise HTTPException(500, "model manifest is unreadable") from e
        if not isinstance(entries, list) or not all(isinstance(m, dict) and "name" in m and "sha256" in m for m in entries):
            raise HTTPException(500, "model manifest is invalid")
        return entries

    @app.get("/v1/models", dependencies=device_only)
    def models():
        return {"models": manifest()}

    @app.get("/v1/models/{name}", dependencies=device_only)
    def model_file(name: str, request: Request):
        entry = next((m for m in manifest() if m["name"] == name), None)
        path = s.model_dir / name
        if not NAME.match(name) or entry is None or not path.is_file():
            raise HTTPException(404, "no such model")
        digest = file_sha256(path)
        if digest != entry["sha256"]:
            raise HTTPException(500, "model file does not match its manifest")
        # The hash is the version: a device that already holds this file revalidates without downloading it again.
        etag = f'"{digest}"'
        headers = {"ETag": etag, "Cache-Control": "private, max-age=86400"}
        if request.headers.get("if-none-match") in (etag, "*"):
            return Response(status_code=304, headers=headers)
        # FileResponse answers a Range request with 206, so a download can resume after the network drops.
        return FileResponse(path, media_type="application/octet-stream", headers=headers)

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
    def admin_feedback(limit: int = Query(200, ge=1, le=1000), before: int | None = Query(None, ge=1),
                       outcome: Literal["confirmed", "corrected", "rejected"] | None = None):
        query = select(Feedback)
        if before is not None:
            # Ids only increase, so the oldest id in a page is the cursor for the next one.
            query = query.where(Feedback.id < before)
        if outcome is not None:
            query = query.where(Feedback.outcome == outcome)
        with app.state.sessions() as db:
            rows = db.scalars(query.order_by(Feedback.id.desc()).limit(limit)).all()
            audit(db, "admin.feedback")
            db.commit()
        return [{"id": r.id, "predicted": r.predicted, "outcome": r.outcome, "confirmed": r.confirmed,
                 "model_version": r.model_version, "at": r.created_at.isoformat()} for r in rows]

    @app.get("/v1/admin/audit", dependencies=[Depends(admin_auth)])
    def admin_audit(limit: int = Query(200, ge=1, le=1000), before: int | None = Query(None, ge=1),
                    action: str | None = Query(None, min_length=1, max_length=32),
                    device: str | None = Query(None, max_length=32)):
        """The audit trail is readable by the administrator; it carries no subject data and never a token."""
        query = select(Audit)
        if before is not None:
            query = query.where(Audit.id < before)
        if action is not None:
            query = query.where(Audit.action == action)
        if device is not None:
            query = query.where(Audit.device_id == device)
        with app.state.sessions() as db:
            rows = db.scalars(query.order_by(Audit.id.desc()).limit(limit)).all()
            audit(db, "admin.audit")
            db.commit()
        return [{"id": a.id, "at": a.ts.isoformat(), "device_id": a.device_id, "action": a.action} for a in rows]

    @app.get("/v1/admin/devices", dependencies=[Depends(admin_auth)])
    def admin_devices(limit: int = Query(200, ge=1, le=1000)):
        with app.state.sessions() as db:
            rows = db.scalars(select(Device).order_by(Device.created_at.desc()).limit(limit)).all()
            # The README promises admin reads are audited; listing devices reveals the department's fleet.
            audit(db, "admin.devices")
            db.commit()
        return [{"id": d.id, "created_at": d.created_at.isoformat(), "revoked": d.revoked,
                 # The README promises hour precision; an exact second is more than an administrator needs.
                 "last_seen": d.last_seen.replace(minute=0, second=0, microsecond=0).isoformat() if d.last_seen else None}
                for d in rows]

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
