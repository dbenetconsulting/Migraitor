package com.migration.entities;

import javax.persistence.*;

/** v2: raw row of the target export, one folder per employee (table tgt_folder). */
@Entity
@Table(name = "tgt_folder")
public class TargetFolderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(name = "employee_id", length = 100)
    private String employeeId;
    @Column(name = "employee_name", length = 500)
    private String employeeName;
    @Column(name = "folder_id")
    private Long folderId;
    @Column(name = "folder_name", length = 500)
    private String folderName;
    @Column(name = "parent_name", length = 500)
    private String parentName;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
}
