import os
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    database_url: str = field(default_factory=lambda: os.environ.get("DATABASE_URL", "sqlite:///gateway.db"))
    model_dir: Path = field(default_factory=lambda: Path(os.environ.get("MODEL_DIR", "models")))
    # SHA-256 hex of each valid one-time enrolment code, issued by the department with the device.
    enroll_hashes: frozenset[str] = field(default_factory=lambda: frozenset(filter(None, os.environ.get("ENROLL_HASHES", "").split(","))))
    assist_url: str = field(default_factory=lambda: os.environ.get("ASSIST_URL", ""))
    assist_key: str = field(default_factory=lambda: os.environ.get("ASSIST_KEY", ""))
    assist_model: str = field(default_factory=lambda: os.environ.get("ASSIST_MODEL", ""))
    oidc_issuer: str = field(default_factory=lambda: os.environ.get("OIDC_ISSUER", ""))
    oidc_audience: str = field(default_factory=lambda: os.environ.get("OIDC_AUDIENCE", "signsetu-portal"))
    reviewer_role: str = "reviewer"
    assist_max_chars: int = 20_000
    rate_per_minute: int = 30
