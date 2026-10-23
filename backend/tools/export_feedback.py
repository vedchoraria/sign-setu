"""Exports consented feedback labels as JSON Lines for the model pipeline.

Usage:
    DATABASE_URL=... python tools/export_feedback.py                  # every label, to stdout
    DATABASE_URL=... python tools/export_feedback.py --out labels.jsonl
    DATABASE_URL=... python tools/export_feedback.py --since 2026-10-01

One object per line, oldest first: id, at, predicted, outcome, confirmed, model_version.
The device id is deliberately left out, so a training set cannot be tied back to a phone.
The tool never changes or deletes a label; stdout carries the rows and the count goes to stderr.
"""
import argparse
import json
import os
import sys
from datetime import UTC, datetime
from typing import TextIO

from sqlalchemy import select
from sqlalchemy.orm import Session

from signsetu_gateway.db import Feedback, make_engine


def export(database_url: str, out: TextIO, since: datetime | None = None) -> int:
    if since is not None and since.tzinfo is None:
        since = since.replace(tzinfo=UTC)
    engine = make_engine(database_url)
    try:
        with Session(engine) as db:
            query = select(Feedback).order_by(Feedback.id)
            if since is not None:
                query = query.where(Feedback.created_at >= since)
            count = 0
            for row in db.scalars(query):
                out.write(json.dumps({
                    "id": row.id,
                    "at": row.created_at.isoformat(),
                    "predicted": row.predicted,
                    "outcome": row.outcome,
                    "confirmed": row.confirmed,
                    "model_version": row.model_version,
                }, ensure_ascii=False) + "\n")
                count += 1
            return count
    finally:
        engine.dispose()


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Export consented feedback labels as JSON Lines.")
    parser.add_argument("--out", help="write here instead of stdout")
    parser.add_argument("--since", type=datetime.fromisoformat, metavar="DATE", help="only labels created on or after this UTC date")
    args = parser.parse_args(argv)
    url = os.environ.get("DATABASE_URL")
    if not url:
        print("set DATABASE_URL in the environment", file=sys.stderr)
        return 2
    if args.out:
        with open(args.out, "w", encoding="utf-8", newline="\n") as f:
            count = export(url, f, args.since)
    else:
        count = export(url, sys.stdout, args.since)
    print(f"exported {count} labels", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
