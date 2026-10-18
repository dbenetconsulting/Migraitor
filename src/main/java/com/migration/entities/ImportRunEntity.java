package com.migration.entities;

import javax.persistence.*;
import java.time.LocalDateTime;

/** v2: one export/import of source documents or target folders (table import_run). */
@Entity
@Table(name = "import_run")
public class ImportRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long runId;
    @Column(name = "migtype", length = 40)
    private String migType;
    /** SOURCE or TARGET. */
    @Column(name = "role", length = 10)
    private String role;
    /** LIVEREPORT or CSV. */
    @Column(name = "origin", length = 20)
    private String origin;
    /** RUNNING, DONE, ERROR, MATCHED. */
    @Column(name = "status", length = 20)
    private String status;
    @Column(name = "row_count")
    private Integer rowCount = 0;
    @Column(name = "message", length = 2000)
    private String message;
    @Column(name = "username", length = 200)
    private String userName;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public Long getRunId() { return runId; }
    public String getMigType() { return migType; }
    public void setMigType(String migType) { this.migType = migType; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null || message.length() <= 2000 ? message : message.substring(0, 2000); }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
