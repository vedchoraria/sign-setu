import hashlib
import math
import secrets
import threading
import time
from datetime import UTC, datetime, timedelta
from collections import defaultdict, deque
from typing import Annotated, Any

import jwt
from fastapi import Depends, Header, HTTPException, Request
from jwt import PyJWKClient
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from .db import Audit, Device

Authorization = Annotated[str, Header()]
MAX_TRACKED_KEYS = 10_000
LAST_SEEN_RESOLUTION = timedelta(hours=1)


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def new_token() -> str:
    return secrets.token_urlsafe(32)


def audit(db: Session, action: str, device_id: str = "") -> None:
    db.add(Audit(action=action, device_id=device_id))


class RateLimiter:
    # In-process sliding window; per-gateway-instance, move to Redis when run on several replicas.
    def __init__(self, per_minute: int):
        self.per_minute = per_minute
        self.hits: defaultdict[str, deque[float]] = defaultdict(deque)
        # Routes run on a thread pool; the window and the count must change together.
        self.lock = threading.RLock()

    def exceeded(self, key: str) -> bool:
        with self.lock:
            q = self.hits.get(key)
            now = time.monotonic()
            return bool(q) and sum(1 for t in q if now - t <= 60) >= self.per_minute

    def retry_after(self, key: str) -> int:
        """Whole seconds until the oldest hit in the window expires and one more is allowed."""
        with self.lock:
            q = self.hits.get(key)
            return max(1, math.ceil(60 - (time.monotonic() - q[0]))) if q else 1

    def refuse(self, key: str) -> HTTPException:
        return HTTPException(429, "rate limit", headers={"Retry-After": str(self.retry_after(key))})

    def check(self, key: str) -> None:
        with self.lock:
            now = time.monotonic()
            if len(self.hits) > MAX_TRACKED_KEYS:
                self.hits = defaultdict(deque, {k: q for k, q in self.hits.items() if q and now - q[-1] <= 60})
            q = self.hits[key]
            while q and now - q[0] > 60:
                q.popleft()
            if len(q) >= self.per_minute:
                raise self.refuse(key)
            q.append(now)


def client_key(request: Request, trusted_hops: int) -> str:
    """The caller's address. X-Forwarded-For is believed only for the entries that the given number of trusted proxies
    appended on the right; anything further left was written by the caller. With no trusted proxy it is ignored."""
    peer = request.client.host if request.client else "unknown"
    if trusted_hops <= 0:
        return peer
    parts = [p.strip() for p in request.headers.get("x-forwarded-for", "").split(",") if p.strip()]
    return parts[-trusted_hops] if len(parts) >= trusted_hops else peer


def bearer(authorization: str) -> str:
    return authorization.removeprefix("Bearer ").strip()


def device_auth(request: Request, authorization: Authorization = "") -> Device:
    state = request.app.state
    if not (token := bearer(authorization)):
        raise HTTPException(401, "token required")
    with state.sessions() as db:
        dev = db.scalar(select(Device).filter_by(token_hash=sha256(token)))
    if dev is None or dev.revoked:
        raise HTTPException(401, "unknown device")
    ttl = state.settings.token_ttl_days
    if ttl and as_utc(dev.created_at) + timedelta(days=ttl) <= datetime.now(UTC):
        raise HTTPException(401, "token expired")
    state.limiter.check(dev.id)
    touch_last_seen(state, dev)
    return dev


def as_utc(moment: datetime) -> datetime:
    """SQLite hands back timestamps without a zone; every value this gateway stores is UTC."""
    return moment if moment.tzinfo else moment.replace(tzinfo=UTC)


def touch_last_seen(state: Any, dev: Device) -> None:
    """Written at most once per LAST_SEEN_RESOLUTION so authenticating a request is not a database write each time."""
    now = datetime.now(UTC)
    if dev.last_seen is not None and now - as_utc(dev.last_seen) < LAST_SEEN_RESOLUTION:
        return
    with state.sessions() as db:
        db.execute(update(Device).where(Device.id == dev.id).values(last_seen=now))
        db.commit()


def reviewer_auth(request: Request, authorization: Authorization = "") -> dict[str, Any]:
    """A Keycloak access token whose realm role includes the reviewer role; keys come from the issuer's JWKS."""
    return _role_auth(request, authorization, request.app.state.settings.reviewer_role)


def admin_auth(request: Request, authorization: Authorization = "") -> dict[str, Any]:
    """The same check for the gateway administrator role, which can list and revoke devices."""
    return _role_auth(request, authorization, request.app.state.settings.admin_role)


def _role_auth(request: Request, authorization: str, role: str) -> dict[str, Any]:
    s = request.app.state.settings
    token = bearer(authorization)
    if not token or not s.oidc_issuer:
        raise HTTPException(401, "token required")
    try:
        key = request.app.state.jwks(s.oidc_issuer).get_signing_key_from_jwt(token).key
        claims = jwt.decode(token, key, algorithms=["RS256", "ES256"], audience=s.oidc_audience, issuer=s.oidc_issuer)
    except jwt.PyJWTError:
        raise HTTPException(401, "invalid token") from None
    # A token may carry the claim with a null body; that is a refusal, not a server error.
    if role not in (claims.get("realm_access") or {}).get("roles", []):
        raise HTTPException(403, f"{role} role required")
    return claims


def default_jwks(issuer: str) -> PyJWKClient:
    return PyJWKClient(f"{issuer.rstrip('/')}/protocol/openid-connect/certs", cache_keys=True)


DeviceDep = Annotated[Device, Depends(device_auth)]
