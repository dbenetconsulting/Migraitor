package com.migration.entities;

import javax.persistence.*;

/** Document type mapping to RM classification (table smartdoctypes). */
@Entity
@Table(name = "smartdoctypes")
public class SmartTypesEntity {

    @Id
    @Column(name = "smart_doc_type")
    private String smartType;
    @Column(name = "standard_classid")
    private Integer classId;
    @Column(name = "standard_classid_nickname")
    private String classNickname;
    @Column(name = "rm_classification")
    private Integer rmClassification;

    public String getSmartType() { return smartType; }
    public void setSmartType(String smartType) { this.smartType = smartType; }
    public Integer getClassId() { return classId; }
    public void setClassId(Integer classId) { this.classId = classId; }
    public String getClassNickname() { return classNickname; }
    public void setClassNickname(String classNickname) { this.classNickname = classNickname; }
    public Integer getRmClassification() { return rmClassification; }
    public void setRmClassification(Integer rmClassification) { this.rmClassification = rmClassification; }
}
