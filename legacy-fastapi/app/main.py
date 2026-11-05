"""Small research-oriented recruiting workflow demo; not a production ATS."""
from __future__ import annotations

import os
import re
import sqlite3
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field, field_validator

from .db import STAGES, audit, initialize, now_utc, seed_synthetic_data, session

ROOT = Path(__file__).resolve().parent.parent
NEXT_STAGES = {
    "applied": ["screening", "rejected"],
    "screening": ["interview", "rejected"],
    "interview": ["offer", "rejected"],
    "offer": ["hired", "rejected"],
    "hired": [],
    "rejected": [],
}


class SanitizedModel(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)


class JobCreate(SanitizedModel):
    title: str = Field(min_length=2, max_length=100)
    team: str = Field(min_length=2, max_length=80)
    location: str = Field(min_length=2, max_length=100)


class JobStatus(SanitizedModel):
    status: Literal["open", "closed"]


class ApplicationCreate(SanitizedModel):
    full_name: str = Field(min_length=2, max_length=100)
    email: str = Field(min_length=5, max_length=254)
    job_id: int = Field(gt=0)

    @field_validator("email")
    @classmethod
    def validate_email(cls, value: str) -> str:
        # Demo-level format validation, not deliverability verification.
        if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", value):
            raise ValueError("Enter a valid email address")
        return value.lower()


class StageChange(SanitizedModel):
    stage: Literal["applied", "screening", "interview", "offer", "hired", "rejected"]
    expected_version: int = Field(ge=1)


def serialize(row: sqlite3.Row) -> dict:
    return dict(row)


def create_app(database_path: str | Path | None = None, seed_demo: bool | None = None) -> FastAPI:
    path = str(database_path or os.getenv("RMS_DB_PATH", ROOT / "data" / "recruitment.db"))
    initialize(path)
    if seed_demo if seed_demo is not None else os.getenv("RMS_SEED_DEMO", "1") == "1":
        seed_synthetic_data(path)

    api = FastAPI(title="Recruitment Management System Demo", version="1.0.0",
                  description="Synthetic-data workflow demonstration; not a production ATS.")
    api.state.database_path = path
    api.mount("/static", StaticFiles(directory=ROOT / "app" / "static"), name="static")
    api.mount("/portfolio", StaticFiles(directory=ROOT / "portfolio", html=True), name="portfolio")

    @api.get("/", include_in_schema=False)
    def index():
        return FileResponse(ROOT / "app" / "static" / "index.html")

    @api.get("/api/health")
    def health():
        with session(path) as db:
            db.execute("SELECT 1").fetchone()
        return {"status": "ok", "dataset": "synthetic_demo"}

    @api.get("/api/jobs")
    def list_jobs():
        with session(path) as db:
            records = db.execute("""SELECT j.*,
                COUNT(a.id) AS application_count
                FROM jobs j LEFT JOIN applications a ON a.job_id=j.id
                GROUP BY j.id ORDER BY j.id""").fetchall()
        return [serialize(row) for row in records]

    @api.post("/api/jobs", status_code=201)
    def create_job(payload: JobCreate):
        timestamp = now_utc()
        with session(path) as db:
            cur = db.execute("INSERT INTO jobs(title,team,location,created_at) VALUES (?,?,?,?)",
                             (payload.title, payload.team, payload.location, timestamp))
            audit(db, "job_created", "job", cur.lastrowid)
            return {"id": cur.lastrowid, "title": payload.title, "team": payload.team,
                    "location": payload.location, "status": "open"}

    @api.patch("/api/jobs/{job_id}/status")
    def update_job(job_id: int, payload: JobStatus):
        with session(path) as db:
            job = db.execute("SELECT status FROM jobs WHERE id=?", (job_id,)).fetchone()
            if not job:
                raise HTTPException(404, "Job not found")
            if job["status"] != payload.status:
                db.execute("UPDATE jobs SET status=? WHERE id=?", (payload.status, job_id))
                audit(db, "job_status_changed", "job", job_id, details=f"{job['status']} -> {payload.status}")
        return {"id": job_id, "status": payload.status}

    @api.get("/api/applications")
    def list_applications(
        search: str = Query(default="", max_length=120),
        job_id: int | None = Query(default=None, gt=0),
        stage: str | None = None,
    ):
        if stage and stage not in STAGES:
            raise HTTPException(422, "Unknown stage")
        where, args = [], []
        if search.strip():
            # Escape wildcard characters: a search term is literal text, never SQL syntax.
            value = search.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            where.append("(c.full_name LIKE ? ESCAPE '\\' OR j.title LIKE ? ESCAPE '\\')")
            args += [f"%{value}%", f"%{value}%"]
        if job_id is not None:
            where.append("a.job_id=?")
            args.append(job_id)
        if stage:
            where.append("a.stage=?")
            args.append(stage)
        condition = " WHERE " + " AND ".join(where) if where else ""
        with session(path) as db:
            rows = db.execute("""SELECT a.id, a.job_id, a.candidate_id, a.stage,
                a.version, a.created_at, a.updated_at, c.full_name,
                c.email, j.title AS job_title, j.team, j.status AS job_status
                FROM applications a
                JOIN candidates c ON c.id=a.candidate_id
                JOIN jobs j ON j.id=a.job_id""" + condition +
                " ORDER BY a.updated_at DESC, a.id DESC LIMIT 500", args).fetchall()
        return [{**serialize(row), "allowed_next": NEXT_STAGES[row["stage"]]} for row in rows]

    @api.get("/api/applications/{application_id}")
    def application_detail(application_id: int):
        """Read one application with its own event history (no scoring or ranking)."""
        with session(path) as db:
            record = db.execute("""SELECT a.id, a.job_id, a.candidate_id, a.stage,
                a.version, a.created_at, a.updated_at, c.full_name,
                c.email, j.title AS job_title, j.team, j.location, j.status AS job_status
                FROM applications a JOIN candidates c ON c.id=a.candidate_id
                JOIN jobs j ON j.id=a.job_id WHERE a.id=?""", (application_id,)).fetchone()
            if record is None:
                raise HTTPException(404, "Application not found")
            history = db.execute("""SELECT id, event_type, from_stage, to_stage, details, created_at
                FROM audit_events WHERE entity_type='application' AND entity_id=?
                ORDER BY id DESC""", (application_id,)).fetchall()
        return {**serialize(record), "allowed_next": NEXT_STAGES[record["stage"]],
                "history": [serialize(event) for event in history]}

    @api.post("/api/applications", status_code=201)
    def create_application(payload: ApplicationCreate):
        timestamp = now_utc()
        with session(path) as db:
            job = db.execute("SELECT status FROM jobs WHERE id=?", (payload.job_id,)).fetchone()
            if not job:
                raise HTTPException(404, "Job not found")
            if job["status"] != "open":
                raise HTTPException(409, "This job is closed to new applications")
            candidate = db.execute("SELECT id FROM candidates WHERE email=? COLLATE NOCASE",
                                   (payload.email,)).fetchone()
            if candidate:
                candidate_id = candidate["id"]
            else:
                cur = db.execute("INSERT INTO candidates(full_name,email,created_at) VALUES (?,?,?)",
                                 (payload.full_name, payload.email, timestamp))
                candidate_id = cur.lastrowid
            existing = db.execute("SELECT id FROM applications WHERE candidate_id=? AND job_id=?",
                                  (candidate_id, payload.job_id)).fetchone()
            if existing:
                raise HTTPException(409, "This candidate already applied to this job")
            cur = db.execute("""INSERT INTO applications
                (candidate_id, job_id, stage, version, created_at, updated_at)
                VALUES (?,?,'applied',1,?,?)""", (candidate_id, payload.job_id, timestamp, timestamp))
            audit(db, "application_created", "application", cur.lastrowid, to_stage="applied")
            return {"id": cur.lastrowid, "candidate_id": candidate_id, "job_id": payload.job_id,
                    "stage": "applied", "version": 1}

    @api.patch("/api/applications/{application_id}/stage")
    def update_stage(application_id: int, payload: StageChange):
        with session(path) as db:
            existing = db.execute("SELECT stage, version FROM applications WHERE id=?",
                                  (application_id,)).fetchone()
            if not existing:
                raise HTTPException(404, "Application not found")
            if existing["version"] != payload.expected_version:
                raise HTTPException(409, "Record changed; refresh and try again")
            if payload.stage not in NEXT_STAGES[existing["stage"]]:
                raise HTTPException(409, f"Invalid transition from {existing['stage']} to {payload.stage}")
            result = db.execute("""UPDATE applications SET stage=?, version=version+1, updated_at=?
                WHERE id=? AND version=?""",
                (payload.stage, now_utc(), application_id, payload.expected_version))
            if result.rowcount != 1:
                raise HTTPException(409, "Concurrent update; refresh and try again")
            audit(db, "application_stage_changed", "application", application_id,
                  from_stage=existing["stage"], to_stage=payload.stage)
        return {"id": application_id, "stage": payload.stage, "version": payload.expected_version + 1}

    @api.get("/api/metrics")
    def metrics():
        with session(path) as db:
            jobs = db.execute("SELECT COUNT(*) total, SUM(status='open') open_count FROM jobs").fetchone()
            candidates = db.execute("SELECT COUNT(*) total FROM candidates").fetchone()["total"]
            total = db.execute("SELECT COUNT(*) total FROM applications").fetchone()["total"]
            rows = db.execute("SELECT stage, COUNT(*) AS count FROM applications GROUP BY stage").fetchall()
            by_job = db.execute("""SELECT j.id, j.title, COUNT(a.id) AS count
                FROM jobs j LEFT JOIN applications a ON a.job_id=j.id
                GROUP BY j.id ORDER BY count DESC, j.id""").fetchall()
        counts = {stage: 0 for stage in STAGES}
        counts.update({row["stage"]: row["count"] for row in rows})
        return {"jobs": jobs["total"], "open_jobs": jobs["open_count"] or 0,
                "candidates": candidates, "applications": total,
                "active_applications": sum(counts[s] for s in ("applied", "screening", "interview", "offer")),
                "stage_counts": counts,
                "by_job": [serialize(row) for row in by_job],
                "note": "Snapshot counts from synthetic demo records; not conversion rates or research findings."}

    @api.get("/api/audit")
    def audit_events(limit: int = Query(default=40, ge=1, le=200)):
        with session(path) as db:
            rows = db.execute("SELECT * FROM audit_events ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
        return [serialize(row) for row in rows]

    return api


app = create_app()
