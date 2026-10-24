"""Deletes audit rows older than the agreed retention; nothing goes without --apply, and a future cutoff is refused.
Usage is in backend/README.md."""
import argparse
import os
import sys
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from signsetu_gateway.db import Audit, make_engine
from signsetu_gateway.security import audit


def cutoff(days: int | None = None, before: datetime | None = None, now: datetime | None = None) -> datetime:
    """The instant before which rows are expired, in UTC."""
    now = now or datetime.now(UTC)
    if (days is None) == (before is None):
        raise ValueError("give exactly one of days or before")
    moment = before if before is not None else now - timedelta(days=days)
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=UTC)
    if moment > now:
        raise ValueError("the cutoff is in the future, which would delete every audit row")
    return moment


def prune(database_url: str, before: datetime, dry_run: bool = True) -> int:
    """Deletes audit rows older than `before` and returns how many expired. A dry run writes nothing."""
    engine = make_engine(database_url)
    try:
        with Session(engine) as db:
            count = db.scalar(select(func.count()).select_from(Audit).where(Audit.ts < before)) or 0
            if dry_run or not count:
                return count
            db.execute(delete(Audit).where(Audit.ts < before))
            # Maintenance that destroys records is itself an auditable action.
            audit(db, "audit.pruned")
            db.commit()
            return count
    finally:
        engine.dispose()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Delete gateway audit rows older than the retention period.")
    which = parser.add_mutually_exclusive_group(required=True)
    which.add_argument("--days", type=int, help="keep this many days of audit rows")
    which.add_argument("--before", type=datetime.fromisoformat, metavar="DATE", help="delete rows older than this UTC date")
    parser.add_argument("--apply", action="store_true", help="delete the rows; without this the tool only reports")
    args = parser.parse_args(argv)
    if args.days is not None and args.days < 1:
        parser.error("--days must be at least 1")
    return args


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    url = os.environ.get("DATABASE_URL")
    if not url:
        print("set DATABASE_URL in the environment", file=sys.stderr)
        return 2
    try:
        moment = cutoff(days=args.days, before=args.before)
    except ValueError as e:
        print(e, file=sys.stderr)
        return 2
    count = prune(url, moment, dry_run=not args.apply)
    verb = "deleted" if args.apply else "would delete"
    print(f"{verb} {count} audit rows older than {moment.isoformat()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
