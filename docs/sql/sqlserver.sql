-- =============================================================================
-- Migraitor - SQL Server schema. Idempotent: run it on a new database or on a
-- v1 database (it only creates what is missing and never drops anything).
-- =============================================================================

-- ---------- v1 tables (unchanged names and columns) ----------
IF OBJECT_ID('jobs') IS NULL
CREATE TABLE jobs (
    job_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_name        VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    date_start      DATETIME2,
    date_end        DATETIME2,
    total_items     INT NOT NULL,
    processed_items INT NOT NULL DEFAULT 0,
    migtype         VARCHAR(255),
    username        VARCHAR(255),
    log_level       INT
);

IF OBJECT_ID('migration') IS NULL
CREATE TABLE migration (
    mig_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
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
    date_mig        DATETIME2
);

IF OBJECT_ID('cs_metadata') IS NULL
CREATE TABLE cs_metadata (
    mig_id              BIGINT PRIMARY KEY,
    doc_id              INT,
    metadata            TEXT,
    date_of_termination VARCHAR(255)
);

IF OBJECT_ID('sapalnodep_metadata') IS NULL
CREATE TABLE sapalnodep_metadata (
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

IF OBJECT_ID('smartdoctypes') IS NULL
CREATE TABLE smartdoctypes (
    smart_doc_type            VARCHAR(255) PRIMARY KEY,
    standard_classid          INT,
    standard_classid_nickname VARCHAR(255),
    rm_classification         INT
);

-- ---------- v2 columns on existing tables ----------
IF COL_LENGTH('migration', 'hash_source') IS NULL ALTER TABLE migration ADD hash_source VARCHAR(64);
IF COL_LENGTH('migration', 'error_detail') IS NULL ALTER TABLE migration ADD error_detail VARCHAR(2000);
IF COL_LENGTH('sapalnodep_metadata', 'status') IS NULL ALTER TABLE sapalnodep_metadata ADD status VARCHAR(255);

-- Claiming pending documents (UPDATE TOP ... READPAST) and the job screens rely on these.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_migration_status') CREATE INDEX ix_migration_status ON migration(status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_migration_job_status') CREATE INDEX ix_migration_job_status ON migration(job_id, status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cs_metadata_doc') CREATE INDEX ix_cs_metadata_doc ON cs_metadata(doc_id);

-- ---------- v2 tables ----------
IF OBJECT_ID('migration_event') IS NULL
CREATE TABLE migration_event (
    event_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_id      BIGINT,
    mig_id      BIGINT,
    step        VARCHAR(40),
    outcome     VARCHAR(40),
    message     VARCHAR(2000),
    duration_ms BIGINT,
    created_at  DATETIME2
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_event_job') CREATE INDEX ix_event_job ON migration_event(job_id, event_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_event_doc') CREATE INDEX ix_event_doc ON migration_event(mig_id);

IF OBJECT_ID('cs_connection') IS NULL
CREATE TABLE cs_connection (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    migtype       VARCHAR(40) NOT NULL,
    role          VARCHAR(10) NOT NULL,
    base_url      VARCHAR(500) NOT NULL,
    edition       VARCHAR(10) NOT NULL,
    username      VARCHAR(200),
    password_enc  VARCHAR(1000),
    use_proxy     BIT,
    livereport_id BIGINT,
    updated_at    DATETIME2,
    CONSTRAINT uq_cs_connection UNIQUE (migtype, role)
);

IF OBJECT_ID('import_run') IS NULL
CREATE TABLE import_run (
    run_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    migtype     VARCHAR(40),
    role        VARCHAR(10),
    origin      VARCHAR(20),
    status      VARCHAR(20),
    row_count   INT,
    message     VARCHAR(2000),
    username    VARCHAR(200),
    started_at  DATETIME2,
    finished_at DATETIME2
);

IF OBJECT_ID('src_document') IS NULL
CREATE TABLE src_document (
    id               BIGINT IDENTITY(1,1) PRIMARY KEY,
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
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_src_run_status') CREATE INDEX ix_src_run_status ON src_document(run_id, match_status, id);

IF OBJECT_ID('tgt_folder') IS NULL
CREATE TABLE tgt_folder (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    run_id        BIGINT NOT NULL,
    employee_id   VARCHAR(100),
    employee_name VARCHAR(500),
    folder_id     BIGINT,
    folder_name   VARCHAR(500),
    parent_name   VARCHAR(500)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_tgt_run') CREATE INDEX ix_tgt_run ON tgt_folder(run_id);
