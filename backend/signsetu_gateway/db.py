from datetime import UTC, datetime

from sqlalchemy import DDL, Boolean, DateTime, Engine, ForeignKey, String, create_engine, inspect
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
    last_seen: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


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


def normalise_url(url: str) -> str:
    """Hosts hand out postgres:// or postgresql:// URLs; the installed driver is psycopg 3, which SQLAlchemy names explicitly."""
    for scheme in ("postgres://", "postgresql://"):
        if url.startswith(scheme):
            return "postgresql+psycopg://" + url.removeprefix(scheme)
    return url


def is_sqlite(url: str) -> bool:
    return url.startswith("sqlite")


def make_engine(url: str, require_persistent: bool = False) -> Engine:
    url = normalise_url(url)
    if is_sqlite(url) and require_persistent:
        raise RuntimeError("REQUIRE_PERSISTENT_DB is set, so DATABASE_URL must point at PostgreSQL, not a local SQLite file")
    kw = {"connect_args": {"check_same_thread": False}, "poolclass": StaticPool} if is_sqlite(url) else {"pool_pre_ping": True}
    engine = create_engine(url, **kw)
    Base.metadata.create_all(engine)
    add_missing_columns(engine)
    return engine


def add_missing_columns(engine: Engine) -> None:
    """create_all leaves existing tables alone, so a database made by an earlier release gets its new columns here."""
    if "last_seen" not in {c["name"] for c in inspect(engine).get_columns(Device.__tablename__)}:
        column_type = DateTime(timezone=True).compile(dialect=engine.dialect)
        with engine.begin() as conn:
            conn.execute(DDL(f"ALTER TABLE {Device.__tablename__} ADD COLUMN last_seen {column_type}"))
