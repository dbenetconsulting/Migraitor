package com.migration.entities;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;
    @Column(name = "job_name", nullable = false)
    private String jobName;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "date_start")
    private LocalDateTime startDate;
    @Column(name = "date_end")
    private LocalDateTime endDate;
    @Column(name = "total_items", nullable = false)
    private Integer totalItems;
    @Column(name = "processed_items", nullable = false)
    private Integer processedItems = 0;
    @Column(name = "migtype")
    private String migType;
    @Column(name = "username")
    private String userName;
    @Column(name = "log_level")
    private Integer logLevel;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }
    public Integer getProcessedItems() { return processedItems; }
    public void setProcessedItems(Integer processedItems) { this.processedItems = processedItems == null ? 0 : processedItems; }
    public String getMigType() { return migType; }
    public void setMigType(String migType) { this.migType = migType; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Integer getLogLevel() { return logLevel; }
    public void setLogLevel(Integer logLevel) { this.logLevel = logLevel; }
}
