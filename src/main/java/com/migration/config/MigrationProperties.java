package com.migration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "migration")
public class MigrationProperties {

    /** Database engine of the migration database: sqlserver or postgresql. */
    private String dbType = "sqlserver";
    /** Protocol used to build Content Server URLs from the secrets file (https:// or http://). */
    private String protocol = "https://";
    /** Accept self-signed certificates on Content Server. Never enable in production. */
    private boolean trustSelfSigned = false;
    /** Parallel documents per job. */
    private int threads = 2;
    /** Parallel jobs. */
    private int maxParallelJobs = 2;
    /** OTCS tickets are renewed after this age (OTCS default timeout is 30 min). */
    private Duration ticketTtl = Duration.ofMinutes(20);
    private Duration httpConnectTimeout = Duration.ofSeconds(60);
    private Duration httpRequestTimeout = Duration.ofMinutes(15);
    /** Max renames tried when the name already exists in the target folder. */
    private int maxNameAttempts = 25;
    /** Working folder for temporary files while a document is in transit. Empty = system temp. */
    private String workDir = "";
    /**
     * Debug only: when set, every downloaded/converted document is copied here.
     * Contains personal data. Leave empty in production.
     */
    private String debugDumpDir = "";

    private final Scheduler scheduler = new Scheduler();

    public static class Scheduler {
        private boolean enabled = false;
        private long intervalMs = 300_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public boolean isTrustSelfSigned() { return trustSelfSigned; }
    public void setTrustSelfSigned(boolean trustSelfSigned) { this.trustSelfSigned = trustSelfSigned; }
    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
    public int getMaxParallelJobs() { return maxParallelJobs; }
    public void setMaxParallelJobs(int maxParallelJobs) { this.maxParallelJobs = maxParallelJobs; }
    public Duration getTicketTtl() { return ticketTtl; }
    public void setTicketTtl(Duration ticketTtl) { this.ticketTtl = ticketTtl; }
    public Duration getHttpConnectTimeout() { return httpConnectTimeout; }
    public void setHttpConnectTimeout(Duration httpConnectTimeout) { this.httpConnectTimeout = httpConnectTimeout; }
    public Duration getHttpRequestTimeout() { return httpRequestTimeout; }
    public void setHttpRequestTimeout(Duration httpRequestTimeout) { this.httpRequestTimeout = httpRequestTimeout; }
    public int getMaxNameAttempts() { return maxNameAttempts; }
    public void setMaxNameAttempts(int maxNameAttempts) { this.maxNameAttempts = maxNameAttempts; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public String getDebugDumpDir() { return debugDumpDir; }
    public void setDebugDumpDir(String debugDumpDir) { this.debugDumpDir = debugDumpDir; }
    public Scheduler getScheduler() { return scheduler; }
}
