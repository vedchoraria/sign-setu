"""Copies devices, feedback and audit rows between gateway databases, keeping ids; URLs come from the environment so
credentials stay out of shell history. Usage is in backend/README.md."""
import os
import sys

from sqlalchemy import func, select
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
                next_id = func.coalesce(select(func.max(table.id)).scalar_subquery(), 1)
                dst.execute(select(func.setval(func.pg_get_serial_sequence(table.__tablename__, "id"), next_id)))
            dst.commit()
        return wanted


if __name__ == "__main__":
    try:
        result = migrate(os.environ["SOURCE_URL"], os.environ["TARGET_URL"], "--dry-run" in sys.argv)
    except KeyError as missing:
        raise SystemExit(f"set {missing.args[0]} in the environment") from None
    print(("would copy " if "--dry-run" in sys.argv else "copied ") + ", ".join(f"{n} {t}" for t, n in result.items()))
