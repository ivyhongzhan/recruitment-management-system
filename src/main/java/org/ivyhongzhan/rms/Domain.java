package org.ivyhongzhan.rms;

import jakarta.persistence.*;
import java.time.Instant;

enum Stage { applied, screening, interview, offer, hired, rejected }

@Entity @Table(name="jobs")
class Job {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=100) String title;
    @Column(nullable=false,length=80) String team;
    @Column(nullable=false,length=100) String location;
    @Column(nullable=false,length=10) String status="open";
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected Job() {}
    Job(String title,String team,String location) { this.title=title; this.team=team; this.location=location; this.createdAt=Instant.now(); }
}

@Entity @Table(name="candidates", uniqueConstraints=@UniqueConstraint(name="uq_candidates_email",columnNames="email"))
class Candidate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="full_name",nullable=false,length=100) String fullName;
    @Column(nullable=false,length=254) String email;
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected Candidate() {}
    Candidate(String name,String email) { this.fullName=name; this.email=email.toLowerCase(); this.createdAt=Instant.now(); }
}

@Entity @Table(name="applications", uniqueConstraints=@UniqueConstraint(name="uq_application_candidate_job",columnNames={"candidate_id","job_id"}))
class ApplicationRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="candidate_id",nullable=false) Candidate candidate;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="job_id",nullable=false) Job job;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Stage stage;
    @Version @Column(nullable=false) Long version=1L;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected ApplicationRecord() {}
    ApplicationRecord(Candidate candidate,Job job) { this.candidate=candidate; this.job=job; this.stage=Stage.applied; this.version=1L; this.createdAt=Instant.now(); this.updatedAt=this.createdAt; }
}

@Entity @Table(name="audit_events")
class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="event_type",nullable=false,length=80) String eventType;
    @Column(name="entity_type",nullable=false,length=40) String entityType;
    @Column(name="entity_id") Long entityId;
    @Column(name="from_stage",length=20) String fromStage;
    @Column(name="to_stage",length=20) String toStage;
    @Column(nullable=false,length=1000) String details="";
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected AuditEvent() {}
    AuditEvent(String type,String entity,Long id,String from,String to,String details) { this.eventType=type; this.entityType=entity; this.entityId=id; this.fromStage=from; this.toStage=to; this.details=details==null?"":details; this.createdAt=Instant.now(); }
}
