CREATE TABLE jobs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  team VARCHAR(80) NOT NULL,
  location VARCHAR(100) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'open',
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT chk_jobs_status CHECK (status IN ('open','closed'))
);
CREATE TABLE candidates (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_candidates_email UNIQUE (email)
);
CREATE TABLE applications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  candidate_id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  stage VARCHAR(20) NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_application_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id),
  CONSTRAINT fk_application_job FOREIGN KEY (job_id) REFERENCES jobs(id),
  CONSTRAINT uq_application_candidate_job UNIQUE (candidate_id, job_id),
  CONSTRAINT chk_application_stage CHECK (stage IN ('applied','screening','interview','offer','hired','rejected')),
  INDEX idx_applications_stage (stage),
  INDEX idx_applications_job (job_id),
  INDEX idx_applications_updated (updated_at)
);
CREATE TABLE audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_type VARCHAR(80) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT NULL,
  from_stage VARCHAR(20) NULL,
  to_stage VARCHAR(20) NULL,
  details VARCHAR(1000) NOT NULL DEFAULT '',
  created_at TIMESTAMP(6) NOT NULL,
  INDEX idx_audit_entity (entity_type, entity_id, id)
);
