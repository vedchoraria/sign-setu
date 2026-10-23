import importlib.util
import io
import json
from datetime import UTC, datetime
from pathlib import Path

import pytest
from sqlalchemy import create_engine, inspect, select
from sqlalchemy.orm import Session

from signsetu_gateway.db import Audit, Device, Feedback, make_engine, normalise_url

spec = importlib.util.spec_from_file_location("migrate_db", Path(__file__).parent.parent / "tools" / "migrate_db.py")
migrate_db = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate_db)

spec = importlib.util.spec_from_file_location("prune_audit", Path(__file__).parent.parent / "tools" / "prune_audit.py")
prune_audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prune_audit)

spec = importlib.util.spec_from_file_location("export_feedback", Path(__file__).parent.parent / "tools" / "export_feedback.py")
export_feedback = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export_feedback)


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


def seed_audit(url: str) -> None:
    with Session(make_engine(url)) as db:
        db.add(Audit(action="old", ts=datetime(2026, 1, 1, tzinfo=UTC)))
        db.add(Audit(action="new", ts=datetime(2026, 10, 1, tzinfo=UTC)))
        db.commit()


def test_the_audit_cutoff_counts_back_from_now_and_refuses_a_future_one():
    now = datetime(2026, 10, 23, tzinfo=UTC)
    assert prune_audit.cutoff(days=90, now=now) == datetime(2026, 7, 25, tzinfo=UTC)
    # A date without a zone is UTC, which is what every stored timestamp is.
    assert prune_audit.cutoff(before=datetime(2026, 1, 1), now=now) == datetime(2026, 1, 1, tzinfo=UTC)
    with pytest.raises(ValueError, match="exactly one"):
        prune_audit.cutoff(now=now)
    with pytest.raises(ValueError, match="future"):
        prune_audit.cutoff(before=datetime(2027, 1, 1, tzinfo=UTC), now=now)
    with pytest.raises(SystemExit):
        prune_audit.parse_args(["--days", "0"])


def test_audit_pruning_is_a_dry_run_until_applied_and_never_touches_feedback(tmp_path):
    url = f"sqlite:///{tmp_path / 'gateway.db'}"
    seed_audit(url)
    with Session(make_engine(url)) as db:
        db.add(Device(id="d1", token_hash="t1", code_hash="c1"))
        db.flush()
        db.add(Feedback(device_id="d1", predicted="water", outcome="confirmed"))
        db.commit()
    before = datetime(2026, 6, 1, tzinfo=UTC)

    assert prune_audit.prune(url, before) == 1
    with Session(make_engine(url)) as db:
        assert {a.action for a in db.scalars(select(Audit))} == {"old", "new"}

    assert prune_audit.prune(url, before, dry_run=False) == 1
    with Session(make_engine(url)) as db:
        # The deletion is audited; the recent row and every feedback label stay.
        assert {a.action for a in db.scalars(select(Audit))} == {"new", "audit.pruned"}
        assert db.scalar(select(Feedback.predicted)) == "water"
    assert prune_audit.prune(url, before, dry_run=False) == 0


def test_feedback_exports_as_jsonl_in_id_order_without_device_ids(tmp_path):
    url = f"sqlite:///{tmp_path / 'gateway.db'}"
    with Session(make_engine(url)) as db:
        db.add(Device(id="d1", token_hash="t1", code_hash="c1"))
        db.flush()
        db.add(Feedback(device_id="d1", predicted="water", outcome="confirmed", model_version="v1"))
        db.add(Feedback(device_id="d1", predicted="water", outcome="corrected", confirmed="river",
                        model_version="v1", created_at=datetime(2026, 1, 1, tzinfo=UTC)))
        db.commit()

    out = io.StringIO()
    assert export_feedback.export(url, out) == 2
    rows = [json.loads(line) for line in out.getvalue().splitlines()]
    assert [r["predicted"] for r in rows] == ["water", "water"]
    assert rows[1]["outcome"] == "corrected" and rows[1]["confirmed"] == "river"
    assert all("device_id" not in r for r in rows)

    recent = io.StringIO()
    assert export_feedback.export(url, recent, since=datetime(2026, 6, 1, tzinfo=UTC)) == 1
    assert [r["outcome"] for r in map(json.loads, recent.getvalue().splitlines())] == ["confirmed"]


def test_a_database_made_before_last_seen_existed_gains_the_column_and_keeps_its_rows(tmp_path):
    url = f"sqlite:///{tmp_path / 'old.db'}"
    old = create_engine(url)
    with old.begin() as conn:
        conn.exec_driver_sql("CREATE TABLE devices (id VARCHAR(32) PRIMARY KEY, token_hash VARCHAR(64) UNIQUE, code_hash VARCHAR(64) UNIQUE, created_at DATETIME, revoked BOOLEAN)")
        conn.exec_driver_sql("INSERT INTO devices VALUES ('d1', 't1', 'c1', '2026-01-01 00:00:00', 0)")
    old.dispose()
    engine = make_engine(url)
    assert "last_seen" in {c["name"] for c in inspect(engine).get_columns("devices")}
    with Session(engine) as db:
        dev = db.get(Device, "d1")
        assert dev.token_hash == "t1" and dev.last_seen is None
    make_engine(url)
