package com.migration.entities;

import javax.persistence.*;
import java.time.LocalDateTime;

/** One document to migrate (table migration). Column names are unchanged from v1. */
@Entity
@Table(name = "migration")
public class MigrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mig_id")
    private Long migId;
    @Column(name = "expedientid")
    private String expedientId;
    @Column(name = "expedient_name")
    private String expedientName;
    @Column(name = "folderid")
    private Integer folderId;
    @Column(name = "folder_name")
    private String folderName;
    @Column(name = "filename")
    private String fileName;
    @Column(name = "smart_doc_type")
    private String smartDocType;
    @Column(name = "size")
    private Integer size;
    @Column(name = "mimetype")
    private String mimeType;
    @Column(name = "createdate")
    private String createDate;
    /** SHA-256 of the content uploaded to the target (compared with hash_dest). */
    @Column(name = "hash_orig", length = 64)
    private String hashOrigin;
    @Column(name = "hash_dest", length = 64)
    private String hashTarget;
    @Column(name = "hashcompare", length = 64)
    private String hashCompare;
    /** v2: SHA-256 of the raw source binary; differs from hash_orig only when the content was converted. */
    @Column(name = "hash_source", length = 64)
    private String hashSource;
    @Column(name = "status")
    private String status;
    @ManyToOne
    @JoinColumn(name = "job_id")
    private JobEntity job;
    @Column(name = "doc_id_dest")
    private Integer targetDocId;
    @Column(name = "date_mig")
    private LocalDateTime migrationDate;
    /** v2: summary of what went wrong (or warnings) in the last run. */
    @Column(name = "error_detail", length = 2000)
    private String errorDetail;

    public Long getMigId() { return migId; }
    public void setMigId(Long migId) { this.migId = migId; }
    public String getExpedientId() { return expedientId; }
    public void setExpedientId(String expedientId) { this.expedientId = expedientId; }
    public String getExpedientName() { return expedientName; }
    public void setExpedientName(String expedientName) { this.expedientName = expedientName; }
    public Integer getFolderId() { return folderId; }
    public void setFolderId(Integer folderId) { this.folderId = folderId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSmartDocType() { return smartDocType; }
    public void setSmartDocType(String smartDocType) { this.smartDocType = smartDocType; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getCreateDate() { return createDate; }
    public void setCreateDate(String createDate) { this.createDate = createDate; }
    public String getHashOrigin() { return hashOrigin; }
    public void setHashOrigin(String hashOrigin) { this.hashOrigin = hashOrigin; }
    public String getHashTarget() { return hashTarget; }
    public void setHashTarget(String hashTarget) { this.hashTarget = hashTarget; }
    public String getHashCompare() { return hashCompare; }
    public void setHashCompare(String hashCompare) { this.hashCompare = hashCompare; }
    public String getHashSource() { return hashSource; }
    public void setHashSource(String hashSource) { this.hashSource = hashSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public JobEntity getJob() { return job; }
    public void setJob(JobEntity job) { this.job = job; }
    public Integer getTargetDocId() { return targetDocId; }
    public void setTargetDocId(Integer targetDocId) { this.targetDocId = targetDocId; }
    public LocalDateTime getMigrationDate() { return migrationDate; }
    public void setMigrationDate(LocalDateTime migrationDate) { this.migrationDate = migrationDate; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
}
