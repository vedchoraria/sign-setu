import importlib.util
from pathlib import Path

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from signsetu_gateway.db import Audit, Device, Feedback, make_engine, normalise_url

spec = importlib.util.spec_from_file_location("migrate_db", Path(__file__).parent.parent / "tools" / "migrate_db.py")
migrate_db = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate_db)


def test_host_style_postgres_urls_use_the_installed_driver():
    assert normalise_url("postgres://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalise_url("postgresql://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalise_url("postgresql+psycopg://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalise_url("sqlite:///gateway.db") == "sqlite:///gateway.db"


def test_persistence_can_be_required_and_then_sqlite_is_refused():
    make_engine("sqlite://")
    with pytest.raises(RuntimeError, match="REQUIRE_PERSISTENT_DB"):
        make_engine("sqlite://", require_persistent=True)


def seed(url: str) -> None:
    with Session(make_engine(url)) as db:
        db.add(Device(id="d1", token_hash="t1", code_hash="c1"))
        db.add(Device(id="d2", token_hash="t2", code_hash="c2", revoked=True))
        db.flush()
        db.add(Feedback(id=7, device_id="d1", predicted="water", outcome="confirmed"))
        db.add(Audit(id=41, device_id="d1", action="enroll"))
        db.commit()


def test_migration_copies_every_row_and_keeps_ids_and_flags(tmp_path):
    src, dst = f"sqlite:///{tmp_path}/a.db", f"sqlite:///{tmp_path}/b.db"
    seed(src)
    assert migrate_db.migrate(src, dst) == {"devices": 2, "feedback": 1, "audit": 1}
    with Session(make_engine(dst)) as db:
        assert {d.id: d.revoked for d in db.scalars(select(Device))} == {"d1": False, "d2": True}
        assert db.scalar(select(Feedback.id)) == 7
        assert db.scalar(select(Audit.id)) == 41


def test_a_dry_run_writes_nothing_and_a_used_target_is_refused(tmp_path):
    src, dst = f"sqlite:///{tmp_path}/a.db", f"sqlite:///{tmp_path}/b.db"
    seed(src)
    assert migrate_db.migrate(src, dst, dry_run=True) == {"devices": 2, "feedback": 1, "audit": 1}
    with Session(make_engine(dst)) as db:
        assert db.scalar(select(Device)) is None
    migrate_db.migrate(src, dst)
    with pytest.raises(SystemExit, match="already holds rows"):
        migrate_db.migrate(src, dst)
