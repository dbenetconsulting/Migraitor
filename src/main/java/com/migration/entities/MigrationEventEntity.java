package com.migration.entities;

import javax.persistence.*;
import java.time.LocalDateTime;

/** v2: one step of one document in one job (table migration_event). */
@Entity
@Table(name = "migration_event")
public class MigrationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;
    @Column(name = "job_id")
    private Long jobId;
    @Column(name = "mig_id")
    private Long migId;
    @Column(name = "step", length = 40)
    private String step;
    @Column(name = "outcome", length = 40)
    private String outcome;
    @Column(name = "message", length = 2000)
    private String message;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public MigrationEventEntity() {}

    public MigrationEventEntity(Long jobId, Long migId, String step, String outcome, String message, long durationMs) {
        this.jobId = jobId;
        this.migId = migId;
        this.step = step;
        this.outcome = outcome;
        this.message = message == null ? null : (message.length() > 2000 ? message.substring(0, 2000) : message);
        this.durationMs = durationMs;
        this.createdAt = LocalDateTime.now();
    }

    public Long getEventId() { return eventId; }
    public Long getJobId() { return jobId; }
    public Long getMigId() { return migId; }
    public String getStep() { return step; }
    public String getOutcome() { return outcome; }
    public String getMessage() { return message; }
    public Long getDurationMs() { return durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
