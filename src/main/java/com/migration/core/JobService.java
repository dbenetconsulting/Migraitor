package com.migration.core;

import com.migration.config.MigrationProperties;
import com.migration.entities.JobEntity;
import com.migration.entities.MigrationEntity;
import com.migration.entities.SmartTypesEntity;
import com.migration.target.TargetConnectors;
import com.migration.repositories.JobRepository;
import com.migration.repositories.MigrationRepository;
import com.migration.repositories.SmartTypesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Starts, schedules, aborts and retries jobs. Replaces the job part of v1's MigrationServiceImpl.
 * A job reserves its documents, processes them in parallel and ends FINALIZED or ABORTED; the
 * outcome of every document is in its own status.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobs;
    private final MigrationRepository migrations;
    private final SmartTypesRepository smartTypes;
    private final MigrationTypes types;
    private final BatchClaimer claimer;
    private final DocumentProcessor processor;
    private final TargetConnectors targets;
    private final MigrationProperties props;
    private final ThreadPoolTaskExecutor jobExecutor;
    private final Map<Long, JobRun> running = new ConcurrentHashMap<>();

    public JobService(JobRepository jobs, MigrationRepository migrations, SmartTypesRepository smartTypes,
                      MigrationTypes types, BatchClaimer claimer, DocumentProcessor processor,
                      TargetConnectors targets, MigrationProperties props) {
        this.jobs = jobs;
        this.migrations = migrations;
        this.smartTypes = smartTypes;
        this.types = types;
        this.claimer = claimer;
        this.processor = processor;
        this.targets = targets;
        this.props = props;
        this.jobExecutor = new ThreadPoolTaskExecutor();
        jobExecutor.setCorePoolSize(Math.max(1, props.getMaxParallelJobs()));
        jobExecutor.setMaxPoolSize(Math.max(1, props.getMaxParallelJobs()));
        jobExecutor.setThreadNamePrefix("job-");
        jobExecutor.initialize();
    }

    // ------------------------------------------------------------------ commands

    public JobEntity start(String name, String migType, int numItems, int logLevel, String user) {
        JobEntity job = newJob(name, migType, numItems, logLevel, user, JobStatus.INITIATED, LocalDateTime.now());
        submit(job.getJobId(), false);
        return job;
    }

    public JobEntity schedule(String name, String migType, int numItems, int logLevel, String user, LocalDateTime start) {
        return newJob(name, migType, numItems, logLevel, user, JobStatus.PENDING, start == null ? LocalDateTime.now() : start);
    }

    /** Stops picking new documents; the ones in progress finish their current step. */
    public boolean abort(long jobId) {
        JobRun run = running.get(jobId);
        if (run != null) {
            run.abort.set(true);
            log.info("Abort requested for job {}", jobId);
            return true;
        }
        JobEntity job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job " + jobId + " not found"));
        if (JobStatus.PENDING.name().equals(job.getStatus())) {
            job.setStatus(JobStatus.ABORTED.name());
            job.setEndDate(LocalDateTime.now());
            jobs.save(job);
            return true;
        }
        return false;
    }

    /** Re-processes the job's documents that ended in a retryable error. */
    @Transactional
    public int retry(long jobId) {
        if (running.containsKey(jobId)) throw new IllegalStateException("Job " + jobId + " is still running");
        JobEntity job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job " + jobId + " not found"));
        List<MigrationEntity> docs = migrations.findByJob_JobIdAndStatusIn(jobId, DocStatus.retryableNames());
        if (docs.isEmpty()) return 0;
        docs.forEach(d -> d.setStatus(DocStatus.IN_PROGRESS.name()));
        migrations.saveAll(docs);
        job.setStatus(JobStatus.RETRYING.name());
        job.setProcessedItems(0);
        job.setTotalItems(docs.size());
        job.setEndDate(null);
        jobs.save(job);
        submit(jobId, true);
        return docs.size();
    }

    public boolean isRunning(long jobId) {
        return running.containsKey(jobId);
    }

    /** Called by the scheduler: starts scheduled jobs whose start date has passed. */
    public void startDueJobs() {
        for (JobEntity job : jobs.findReadyToRun(JobStatus.PENDING.name(), LocalDateTime.now())) {
            if (jobs.transition(job.getJobId(), JobStatus.PENDING.name(), JobStatus.INITIATED.name()) == 1) {
                log.info("Starting scheduled job {} ({})", job.getJobId(), job.getJobName());
                submit(job.getJobId(), false);
            }
        }
    }

    // ------------------------------------------------------------------ execution

    private JobEntity newJob(String name, String migType, int numItems, int logLevel, String user, JobStatus status, LocalDateTime start) {
        types.get(migType);
        if (numItems < 1) throw new IllegalArgumentException("Number of items must be at least 1");
        JobEntity job = new JobEntity();
        job.setJobName(name == null || name.isBlank() ? "Job" : name.trim());
        job.setMigType(migType);
        job.setTotalItems(numItems);
        job.setProcessedItems(0);
        job.setLogLevel(logLevel < 1 || logLevel > 4 ? 1 : logLevel);
        job.setUserName(user);
        job.setStatus(status.name());
        job.setStartDate(start);
        return jobs.save(job);
    }

    private void submit(long jobId, boolean retry) {
        jobExecutor.execute(() -> runJob(jobId, retry));
    }

    void runJob(long jobId, boolean retry) {
        MDC.put("jobId", String.valueOf(jobId));
        JobEntity job = jobs.findById(jobId).orElseThrow();
        JobRun run = null;
        try {
            MigrationType<?> type = types.get(job.getMigType());
            run = new JobRun(jobId, job.getUserName(), job.getLogLevel() == null ? 1 : job.getLogLevel(),
                    targets.open(type));
            running.put(jobId, run);
            job.setStartDate(LocalDateTime.now());
            if (!retry) {
                int claimed = claimer.claim(jobId, type.metadataTable(), job.getTotalItems());
                job.setTotalItems(claimed);
            }
            jobs.save(job);
            log.info("[JOB {}] '{}' {} started by {}: {} documents", jobId, job.getJobName(), type.code(), job.getUserName(), job.getTotalItems());
            process(run, type);
            job = jobs.findById(jobId).orElseThrow();
            job.setStatus(run.abort.get() ? JobStatus.ABORTED.name() : JobStatus.FINALIZED.name());
        } catch (Exception e) {
            log.error("[JOB {}] failed", jobId, e);
            job = jobs.findById(jobId).orElseThrow();
            job.setStatus(JobStatus.ERROR.name());
        } finally {
            releaseUnprocessed(jobId);
            job.setEndDate(LocalDateTime.now());
            jobs.save(job);
            running.remove(jobId);
            log.info("[JOB {}] {} - processed {}/{}", jobId, job.getStatus(), job.getProcessedItems(), job.getTotalItems());
            MDC.remove("jobId");
        }
    }

    private <M> void process(JobRun run, MigrationType<M> type) throws InterruptedException {
        List<MigrationEntity> docs = migrations.findByJob_JobIdAndStatus(run.jobId, DocStatus.IN_PROGRESS.name());
        if (docs.isEmpty()) return;
        Map<Long, M> meta = type.loadMetadata(docs.stream().map(MigrationEntity::getMigId).collect(Collectors.toList()));
        Map<String, SmartTypesEntity> smart = smartTypes.findBySmartTypeIn(docs.stream()
                        .map(MigrationEntity::getSmartDocType).filter(Objects::nonNull).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SmartTypesEntity::getSmartType, Function.identity()));

        AtomicInteger threadNo = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, props.getThreads()),
                r -> new Thread(r, "job" + run.jobId + "-doc" + threadNo.incrementAndGet()));
        try {
            for (MigrationEntity doc : docs) {
                pool.execute(() -> {
                    if (run.abort.get()) return;
                    MDC.put("jobId", String.valueOf(run.jobId));
                    processor.process(run, type, doc, meta.get(doc.getMigId()), smart.get(doc.getSmartDocType()));
                });
            }
        } finally {
            pool.shutdown();
            while (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                log.debug("[JOB {}] waiting for documents in progress", run.jobId);
            }
        }
    }

    /** Documents reserved but not processed (abort or failure) go back to the queue. */
    private void releaseUnprocessed(long jobId) {
        List<MigrationEntity> left = migrations.findByJob_JobIdAndStatus(jobId, DocStatus.IN_PROGRESS.name());
        for (MigrationEntity d : left) {
            d.setStatus(d.getTargetDocId() == null ? DocStatus.PENDING.name() : DocStatus.ERROR_INTERRUPTED.name());
        }
        if (!left.isEmpty()) {
            migrations.saveAll(left);
            log.info("[JOB {}] {} unprocessed documents released", jobId, left.size());
        }
    }

    /** After a restart nothing is running: fix what the previous process left half done. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverAfterRestart() {
        List<JobEntity> stale = jobs.findByStatusIn(List.of(JobStatus.INITIATED.name(), JobStatus.RETRYING.name()));
        for (JobEntity j : stale) {
            j.setStatus(JobStatus.INTERRUPTED.name());
            j.setEndDate(LocalDateTime.now());
        }
        jobs.saveAll(stale);
        int reset = migrations.resetNotUploaded(DocStatus.IN_PROGRESS.name(), DocStatus.PENDING.name());
        int interrupted = migrations.markUploaded(DocStatus.IN_PROGRESS.name(), DocStatus.ERROR_INTERRUPTED.name());
        if (!stale.isEmpty() || reset + interrupted > 0) {
            log.warn("Recovered after restart: {} jobs interrupted, {} documents back to PENDING, {} marked ERROR_INTERRUPTED",
                    stale.size(), reset, interrupted);
        }
    }

    TaskExecutor executor() {
        return jobExecutor;
    }
}
