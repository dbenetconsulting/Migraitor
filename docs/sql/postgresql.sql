-- =============================================================================
-- Migraitor - PostgreSQL schema. Idempotent: run it on a new database or on a
-- v1 database (it only creates what is missing and never drops anything).
-- =============================================================================

-- ---------- v1 tables (unchanged names and columns) ----------
CREATE TABLE IF NOT EXISTS jobs (
    job_id          BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    date_start      TIMESTAMP,
    date_end        TIMESTAMP,
    total_items     INT NOT NULL,
    processed_items INT NOT NULL DEFAULT 0,
    migtype         VARCHAR(255),
    username        VARCHAR(255),
    log_level       INT
);

CREATE TABLE IF NOT EXISTS migration (
    mig_id          BIGSERIAL PRIMARY KEY,
    expedientid     VARCHAR(255),
    expedient_name  VARCHAR(255),
    folderid        INT,
    folder_name     VARCHAR(255),
    filename        VARCHAR(255),
    smart_doc_type  VARCHAR(255),
    size            INT,
    mimetype        VARCHAR(255),
    createdate      VARCHAR(255),
    hash_orig       VARCHAR(64),
    hash_dest       VARCHAR(64),
    hashcompare     VARCHAR(64),
    status          VARCHAR(255),
    job_id          BIGINT REFERENCES jobs(job_id),
    doc_id_dest     INT,
    date_mig        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cs_metadata (
    mig_id              BIGINT PRIMARY KEY,
    doc_id              INT,
    metadata            TEXT,
    date_of_termination VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS sapalnodep_metadata (
    mig_id              BIGINT PRIMARY KEY,
    repository          VARCHAR(255),
    loio                VARCHAR(255),
    phio                VARCHAR(255),
    sap_object          VARCHAR(255),
    sap_doctype         VARCHAR(255),
    date_of_creation    VARCHAR(255),
    employeeid          VARCHAR(255),
    firstname           VARCHAR(255),
    lastname            VARCHAR(255),
    date_of_origin      VARCHAR(255),
    date_of_termination VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS smartdoctypes (
    smart_doc_type            VARCHAR(255) PRIMARY KEY,
    standard_classid          INT,
    standard_classid_nickname VARCHAR(255),
    rm_classification         INT
);

-- ---------- v2 columns on existing tables ----------
ALTER TABLE migration ADD COLUMN IF NOT EXISTS hash_source VARCHAR(64);
ALTER TABLE migration ADD COLUMN IF NOT EXISTS error_detail VARCHAR(2000);
ALTER TABLE sapalnodep_metadata ADD COLUMN IF NOT EXISTS status VARCHAR(255);

-- Claiming pending documents (FOR UPDATE SKIP LOCKED) and the job screens rely on these.
CREATE INDEX IF NOT EXISTS ix_migration_status ON migration(status);
CREATE INDEX IF NOT EXISTS ix_migration_job_status ON migration(job_id, status);
CREATE INDEX IF NOT EXISTS ix_cs_metadata_doc ON cs_metadata(doc_id);

-- ---------- v2 tables ----------
CREATE TABLE IF NOT EXISTS migration_event (
    event_id    BIGSERIAL PRIMARY KEY,
    job_id      BIGINT,
    mig_id      BIGINT,
    step        VARCHAR(40),
    outcome     VARCHAR(40),
    message     VARCHAR(2000),
    duration_ms BIGINT,
    created_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_event_job ON migration_event(job_id, event_id);
CREATE INDEX IF NOT EXISTS ix_event_doc ON migration_event(mig_id);

CREATE TABLE IF NOT EXISTS cs_connection (
    id            BIGSERIAL PRIMARY KEY,
    migtype       VARCHAR(40) NOT NULL,
    role          VARCHAR(10) NOT NULL,
    base_url      VARCHAR(500) NOT NULL,
    edition       VARCHAR(10) NOT NULL,
    username      VARCHAR(200),
    password_enc  VARCHAR(1000),
    use_proxy     BOOLEAN,
    livereport_id BIGINT,
    updated_at    TIMESTAMP,
    CONSTRAINT uq_cs_connection UNIQUE (migtype, role)
);

CREATE TABLE IF NOT EXISTS import_run (
    run_id      BIGSERIAL PRIMARY KEY,
    migtype     VARCHAR(40),
    role        VARCHAR(10),
    origin      VARCHAR(20),
    status      VARCHAR(20),
    row_count   INT,
    message     VARCHAR(2000),
    username    VARCHAR(200),
    started_at  TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS src_document (
    id               BIGSERIAL PRIMARY KEY,
    run_id           BIGINT NOT NULL,
    data_id          BIGINT,
    employee_id      VARCHAR(100),
    name             VARCHAR(500),
    doc_type         VARCHAR(200),
    size_bytes       BIGINT,
    mime_type        VARCHAR(200),
    create_date      VARCHAR(40),
    folder_name      VARCHAR(500),
    parent_id        BIGINT,
    termination_date VARCHAR(40),
    match_status     VARCHAR(30),
    mig_id           BIGINT
);
CREATE INDEX IF NOT EXISTS ix_src_run_status ON src_document(run_id, match_status, id);

CREATE TABLE IF NOT EXISTS tgt_folder (
    id            BIGSERIAL PRIMARY KEY,
    run_id        BIGINT NOT NULL,
    employee_id   VARCHAR(100),
    employee_name VARCHAR(500),
    folder_id     BIGINT,
    folder_name   VARCHAR(500),
    parent_name   VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS ix_tgt_run ON tgt_folder(run_id);
