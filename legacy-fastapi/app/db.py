"""SQLite persistence and explicitly synthetic demonstration records."""
from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

STAGES = ("applied", "screening", "interview", "offer", "hired", "rejected")


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


@contextmanager
def session(path: str):
    connection = sqlite3.connect(path, timeout=15)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys=ON")
    connection.execute("PRAGMA busy_timeout=15000")
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def initialize(path: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with session(path) as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS jobs (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                team TEXT NOT NULL,
                location TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'open' CHECK(status IN ('open','closed')),
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS candidates (
                id INTEGER PRIMARY KEY,
                full_name TEXT NOT NULL,
                email TEXT NOT NULL COLLATE NOCASE UNIQUE,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS applications (
                id INTEGER PRIMARY KEY,
                candidate_id INTEGER NOT NULL REFERENCES candidates(id),
                job_id INTEGER NOT NULL REFERENCES jobs(id),
                stage TEXT NOT NULL CHECK(stage IN ('applied','screening','interview','offer','hired','rejected')),
                version INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                UNIQUE(candidate_id, job_id)
            );
            CREATE INDEX IF NOT EXISTS idx_applications_stage ON applications(stage);
            CREATE INDEX IF NOT EXISTS idx_applications_job ON applications(job_id);
            CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY,
                event_type TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id INTEGER,
                from_stage TEXT,
                to_stage TEXT,
                details TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL
            );
        """)


def audit(db: sqlite3.Connection, event_type: str, entity_type: str, entity_id: int | None,
          from_stage: str | None = None, to_stage: str | None = None,
          details: str = "") -> None:
    db.execute("""INSERT INTO audit_events
        (event_type, entity_type, entity_id, from_stage, to_stage, details, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)""",
        (event_type, entity_type, entity_id, from_stage, to_stage, details, now_utc()))


def seed_synthetic_data(path: str) -> bool:
    """Seed ONLY an empty database; values are fictional and not study observations."""
    job_rows = [
        ("Product Design Researcher", "Experience", "Hybrid · New York"),
        ("Data Analyst", "Research Operations", "Remote"),
        ("Frontend Engineer", "Engineering", "Hybrid · Boston"),
        ("Community Operations Associate", "Operations", "Remote"),
    ]
    people = [
        ("Alex Rivera", "alex.rivera@example.test", 1, "screening"),
        ("Morgan Chen", "morgan.chen@example.test", 1, "interview"),
        ("Sam Taylor", "sam.taylor@example.test", 1, "applied"),
        ("Casey Patel", "casey.patel@example.test", 1, "offer"),
        ("Jordan Lee", "jordan.lee@example.test", 2, "applied"),
        ("Riley Kim", "riley.kim@example.test", 2, "screening"),
        ("Avery Thompson", "avery.thompson@example.test", 2, "hired"),
        ("Drew Nguyen", "drew.nguyen@example.test", 2, "rejected"),
        ("Jamie Park", "jamie.park@example.test", 3, "interview"),
        ("Charlie Brown", "charlie.brown@example.test", 3, "screening"),
        ("Taylor Brooks", "taylor.brooks@example.test", 3, "applied"),
        ("Reese Davis", "reese.davis@example.test", 4, "applied"),
        ("Blair Wilson", "blair.wilson@example.test", 4, "interview"),
        ("Skylar James", "skylar.james@example.test", 4, "rejected"),
    ]
    timestamp = now_utc()
    with session(path) as db:
        if db.execute("SELECT EXISTS(SELECT 1 FROM jobs)").fetchone()[0] or db.execute(
                "SELECT EXISTS(SELECT 1 FROM candidates)").fetchone()[0]:
            return False
        for title, team, location in job_rows:
            db.execute("INSERT INTO jobs(title, team, location, created_at) VALUES (?,?,?,?)",
                       (title, team, location, timestamp))
        for name, email, job_id, stage in people:
            cursor = db.execute("INSERT INTO candidates(full_name, email, created_at) VALUES(?,?,?)",
                                (name, email, timestamp))
            db.execute("""INSERT INTO applications
                (candidate_id, job_id, stage, version, created_at, updated_at)
                VALUES (?,?,?,1,?,?)""", (cursor.lastrowid, job_id, stage, timestamp, timestamp))
        audit(db, "synthetic_data_seeded", "system", None,
              details=json.dumps({"fictional_jobs": len(job_rows), "fictional_applications": len(people)}))
    return True
