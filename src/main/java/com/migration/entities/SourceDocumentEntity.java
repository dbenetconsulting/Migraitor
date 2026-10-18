package com.migration.entities;

import javax.persistence.*;

/** v2: raw row of the source export (table src_document). */
@Entity
@Table(name = "src_document")
public class SourceDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(name = "data_id")
    private Long dataId;
    @Column(name = "employee_id", length = 100)
    private String employeeId;
    @Column(name = "name", length = 500)
    private String name;
    @Column(name = "doc_type", length = 200)
    private String docType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "mime_type", length = 200)
    private String mimeType;
    @Column(name = "create_date", length = 40)
    private String createDate;
    @Column(name = "folder_name", length = 500)
    private String folderName;
    @Column(name = "parent_id")
    private Long parentId;
    @Column(name = "termination_date", length = 40)
    private String terminationDate;
    /** NEW, MATCHED, NO_EMPLOYEE, NO_FOLDER, AMBIGUOUS_FOLDER, ALREADY_STAGED, INVALID. */
    @Column(name = "match_status", length = 30)
    private String matchStatus = "NEW";
    @Column(name = "mig_id")
    private Long migId;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getDataId() { return dataId; }
    public void setDataId(Long dataId) { this.dataId = dataId; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getCreateDate() { return createDate; }
    public void setCreateDate(String createDate) { this.createDate = createDate; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getTerminationDate() { return terminationDate; }
    public void setTerminationDate(String terminationDate) { this.terminationDate = terminationDate; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public Long getMigId() { return migId; }
    public void setMigId(Long migId) { this.migId = migId; }
}
