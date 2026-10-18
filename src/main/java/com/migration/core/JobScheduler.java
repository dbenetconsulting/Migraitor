package com.migration.core;

import com.migration.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Starts scheduled jobs when their start date arrives. Can be switched on and off from the UI. */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final JobService jobService;
    private final AtomicBoolean active;

    public JobScheduler(JobService jobService, MigrationProperties props) {
        this.jobService = jobService;
        this.active = new AtomicBoolean(props.getScheduler().isEnabled());
    }

    @Scheduled(fixedDelayString = "${migration.scheduler.interval-ms:300000}", initialDelay = 30_000)
    public void tick() {
        if (!active.get()) return;
        try {
            jobService.startDueJobs();
        } catch (Exception e) {
            log.error("Scheduler error", e);
        }
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean value) {
        active.set(value);
        log.info("Scheduler {}", value ? "started" : "stopped");
    }
}
