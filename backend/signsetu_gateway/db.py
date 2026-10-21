from datetime import UTC, datetime

from sqlalchemy import Boolean, DateTime, Engine, ForeignKey, String, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy.pool import StaticPool


def now() -> datetime:
    return datetime.now(UTC)


class Base(DeclarativeBase):
    pass


class Device(Base):
    __tablename__ = "devices"
    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    # Unique so each enrolment code works once.
    code_hash: Mapped[str | None] = mapped_column(String(64), unique=True, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)


class Feedback(Base):
    """A reading's label outcome only: never landmarks, pictures or statements."""
    __tablename__ = "feedback"
    id: Mapped[int] = mapped_column(primary_key=True)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    predicted: Mapped[str] = mapped_column(String(64))
    outcome: Mapped[str] = mapped_column(String(16))
    confirmed: Mapped[str | None] = mapped_column(String(64), nullable=True)
    model_version: Mapped[str] = mapped_column(String(32), default="")


class Audit(Base):
    """What was done and by which device; no subject data."""
    __tablename__ = "audit"
    id: Mapped[int] = mapped_column(primary_key=True)
    ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    device_id: Mapped[str] = mapped_column(String(32), default="")
    action: Mapped[str] = mapped_column(String(32))


def make_engine(url: str) -> Engine:
    kw = {"connect_args": {"check_same_thread": False}, "poolclass": StaticPool} if url.startswith("sqlite") else {}
    engine = create_engine(url, **kw)
    Base.metadata.create_all(engine)
    return engine
