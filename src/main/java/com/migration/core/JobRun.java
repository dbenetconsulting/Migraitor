package com.migration.core;

import com.migration.target.TargetSystem;

import java.util.concurrent.atomic.AtomicBoolean;

/** What a running job shares with the documents it processes. */
public final class JobRun {

    public final long jobId;
    public final String userName;
    /** 1 = job only, 2 = each step, 3 = each step with URLs, 4 = errors only (same as v1). */
    public final int logLevel;
    public final TargetSystem target;
    public final AtomicBoolean abort = new AtomicBoolean(false);

    public JobRun(long jobId, String userName, int logLevel, TargetSystem target) {
        this.jobId = jobId;
        this.userName = userName;
        this.logLevel = logLevel;
        this.target = target;
    }

    boolean logSteps() {
        return logLevel == 2 || logLevel == 3;
    }
}
