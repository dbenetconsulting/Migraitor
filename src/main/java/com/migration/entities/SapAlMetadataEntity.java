package com.migration.entities;

import javax.persistence.*;

/** SAP ArchiveLink metadata per document (table sapalnodep_metadata). */
@Entity
@Table(name = "sapalnodep_metadata")
public class SapAlMetadataEntity {

    @Id
    @Column(name = "mig_id")
    private Long migId;
    @Column(name = "repository")
    private String repository;
    @Column(name = "loio")
    private String loio;
    @Column(name = "phio")
    private String phio;
    @Column(name = "sap_object")
    private String sapObject;
    @Column(name = "sap_doctype")
    private String sapDocType;
    @Column(name = "date_of_creation")
    private String dateOfCreation;
    @Column(name = "employeeid")
    private String employeeId;
    @Column(name = "firstname")
    private String firstName;
    @Column(name = "lastname")
    private String lastName;
    @Column(name = "date_of_origin")
    private String dateOfOrigin;
    @Column(name = "date_of_termination")
    private String dateOfTermination;
    /** Employee status; "inactive" = managed in SuccessFactors, no termination date in Record Details. */
    @Column(name = "status")
    private String status;

    public Long getMigId() { return migId; }
    public void setMigId(Long migId) { this.migId = migId; }
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
    public String getLoio() { return loio; }
    public void setLoio(String loio) { this.loio = loio; }
    public String getPhio() { return phio; }
    public void setPhio(String phio) { this.phio = phio; }
    public String getSapObject() { return sapObject; }
    public void setSapObject(String sapObject) { this.sapObject = sapObject; }
    public String getSapDocType() { return sapDocType; }
    public void setSapDocType(String sapDocType) { this.sapDocType = sapDocType; }
    public String getDateOfCreation() { return dateOfCreation; }
    public void setDateOfCreation(String dateOfCreation) { this.dateOfCreation = dateOfCreation; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getDateOfOrigin() { return dateOfOrigin; }
    public void setDateOfOrigin(String dateOfOrigin) { this.dateOfOrigin = dateOfOrigin; }
    public String getDateOfTermination() { return dateOfTermination; }
    public void setDateOfTermination(String dateOfTermination) { this.dateOfTermination = dateOfTermination; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
