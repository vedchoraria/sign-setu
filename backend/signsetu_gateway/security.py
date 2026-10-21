import hashlib
import secrets
import time
from collections import defaultdict, deque
from typing import Annotated, Any

import jwt
from fastapi import Depends, Header, HTTPException, Request
from jwt import PyJWKClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from .db import Audit, Device

Authorization = Annotated[str, Header()]
MAX_TRACKED_KEYS = 10_000


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

    def check(self, key: str) -> None:
        now = time.monotonic()
        if len(self.hits) > MAX_TRACKED_KEYS:
            self.hits = defaultdict(deque, {k: q for k, q in self.hits.items() if q and now - q[-1] <= 60})
        q = self.hits[key]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= self.per_minute:
            raise HTTPException(429, "rate limit")
        q.append(now)


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
    state.limiter.check(dev.id)
    return dev


def reviewer_auth(request: Request, authorization: Authorization = "") -> dict[str, Any]:
    """A Keycloak access token whose realm role includes the reviewer role; keys come from the issuer's JWKS."""
    s = request.app.state.settings
    token = bearer(authorization)
    if not token or not s.oidc_issuer:
        raise HTTPException(401, "token required")
    try:
        key = request.app.state.jwks(s.oidc_issuer).get_signing_key_from_jwt(token).key
        claims = jwt.decode(token, key, algorithms=["RS256", "ES256"], audience=s.oidc_audience, issuer=s.oidc_issuer)
    except jwt.PyJWTError:
        raise HTTPException(401, "invalid token") from None
    if s.reviewer_role not in claims.get("realm_access", {}).get("roles", []):
        raise HTTPException(403, "reviewer role required")
    return claims


def default_jwks(issuer: str) -> PyJWKClient:
    return PyJWKClient(f"{issuer.rstrip('/')}/protocol/openid-connect/certs", cache_keys=True)


DeviceDep = Annotated[Device, Depends(device_auth)]
