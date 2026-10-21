"""Copies devices, feedback and audit rows from one gateway database to another, keeping ids.

Usage: SOURCE_URL=sqlite:///gateway.db TARGET_URL=postgresql://... python tools/migrate_db.py [--dry-run]

The URLs are read from the environment so credentials never reach a shell history or this tool's output.
The target must have no rows yet. Take a copy of the source file before running; the source is only read.
"""
import os
import sys

from sqlalchemy import func, select, text
from sqlalchemy.orm import Session

from signsetu_gateway.db import Audit, Device, Feedback, make_engine

TABLES = (Device, Feedback, Audit)


def counts(session: Session) -> dict[str, int]:
    return {t.__tablename__: session.scalar(select(func.count()).select_from(t)) for t in TABLES}


def migrate(source_url: str, target_url: str, dry_run: bool = False) -> dict[str, int]:
    source, target = make_engine(source_url), make_engine(target_url)
    with Session(source) as src, Session(target) as dst:
        if any(counts(dst).values()):
            raise SystemExit("the target already holds rows; refusing to merge into it")
        wanted = counts(src)
        if dry_run:
            return wanted
        for table in TABLES:
            for row in src.scalars(select(table)):
                values = {c.name: getattr(row, c.name) for c in table.__table__.columns}
                dst.add(table(**values))
            dst.flush()
        if counts(dst) != wanted:
            dst.rollback()
            raise SystemExit("row counts differ after copying; nothing was written")
        dst.commit()
        if target.dialect.name == "postgresql":
            # Rows keep their ids, so each sequence must continue after the highest one.
            for table in (Feedback, Audit):
                name = table.__tablename__
                dst.execute(text(f"SELECT setval(pg_get_serial_sequence('{name}', 'id'), COALESCE((SELECT MAX(id) FROM {name}), 1))"))
            dst.commit()
        return wanted


if __name__ == "__main__":
    try:
        result = migrate(os.environ["SOURCE_URL"], os.environ["TARGET_URL"], "--dry-run" in sys.argv)
    except KeyError as missing:
        raise SystemExit(f"set {missing.args[0]} in the environment") from None
    print(("would copy " if "--dry-run" in sys.argv else "copied ") + ", ".join(f"{n} {t}" for t, n in result.items()))
