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
    enroll_per_minute: int = 10
    # Wrong codes across all callers: a spoofed X-Forwarded-For defeats the per-address limit, not this one.
    enroll_failures_per_minute: int = 20
    # The app's system prompts open with one of these; anything else is not the app and is refused.
    assist_prompt_openings: tuple[str, ...] = (
        "Translate the sentence into ",
        "Translate this sentence into English.",
        "You are a trauma-informed child-protection police investigator.",
        "Propose 2 to 3 non-leading follow-up questions",
        "You are a gentle, trauma-informed helper",
    )
