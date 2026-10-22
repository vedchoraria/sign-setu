import os
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    database_url: str = field(default_factory=lambda: os.environ.get("DATABASE_URL", "sqlite:///gateway.db"))
    # Set to 1 where a restart must not lose enrolments: the gateway then refuses to start on SQLite.
    require_persistent_db: bool = field(default_factory=lambda: os.environ.get("REQUIRE_PERSISTENT_DB", "") == "1")
    model_dir: Path = field(default_factory=lambda: Path(os.environ.get("MODEL_DIR", "models")))
    # SHA-256 hex of each valid one-time enrolment code, issued by the department with the device.
    enroll_hashes: frozenset[str] = field(default_factory=lambda: frozenset(filter(None, os.environ.get("ENROLL_HASHES", "").split(","))))
    assist_url: str = field(default_factory=lambda: os.environ.get("ASSIST_URL", ""))
    assist_key: str = field(default_factory=lambda: os.environ.get("ASSIST_KEY", ""))
    assist_model: str = field(default_factory=lambda: os.environ.get("ASSIST_MODEL", ""))
    oidc_issuer: str = field(default_factory=lambda: os.environ.get("OIDC_ISSUER", ""))
    oidc_audience: str = field(default_factory=lambda: os.environ.get("OIDC_AUDIENCE", "signsetu-portal"))
    reviewer_role: str = "reviewer"
    admin_role: str = field(default_factory=lambda: os.environ.get("ADMIN_ROLE", "gateway-admin"))
    # Days a device token stays valid after enrolment; 0 means it never expires.
    token_ttl_days: int = field(default_factory=lambda: int(os.environ.get("TOKEN_TTL_DAYS", "0")))
    assist_max_chars: int = 20_000
    rate_per_minute: int = 30
    # How many reverse proxies in front of the gateway append to X-Forwarded-For; 0 trusts none and uses the socket address.
    trusted_proxy_hops: int = field(default_factory=lambda: int(os.environ.get("TRUSTED_PROXY_HOPS", "0")))
    enroll_per_minute: int = field(default_factory=lambda: int(os.environ.get("ENROLL_PER_MINUTE", "10")))
    # Wrong codes across all callers: a spoofed X-Forwarded-For defeats the per-address limit, not this one. While it is
    # reached every enrolment, valid codes too, waits (at most a minute after the last wrong code).
    enroll_failures_per_minute: int = field(default_factory=lambda: int(os.environ.get("ENROLL_FAILURES_PER_MINUTE", "20")))
