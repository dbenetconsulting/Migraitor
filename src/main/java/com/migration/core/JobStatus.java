package com.migration.core;

/** Status of a job in table jobs. Same values as v1 plus INTERRUPTED. */
public enum JobStatus {
    /** Scheduled, waiting for its start date. */
    PENDING,
    /** Running. */
    INITIATED,
    RETRYING,
    FINALIZED,
    ABORTED,
    ERROR,
    /** The application stopped while the job was running. */
    INTERRUPTED
}
