package com.migration.entities;

import javax.persistence.*;

/** CStoCS metadata per document (table cs_metadata). */
@Entity
@Table(name = "cs_metadata")
public class CsMetadataEntity {

    @Id
    @Column(name = "mig_id")
    private Long migId;
    /** DataID of the document in the source Content Server. */
    @Column(name = "doc_id")
    private Integer docId;
    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;
    @Column(name = "date_of_termination")
    private String dateOfTermination;

    public Long getMigId() { return migId; }
    public void setMigId(Long migId) { this.migId = migId; }
    public Integer getDocId() { return docId; }
    public void setDocId(Integer docId) { this.docId = docId; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getDateOfTermination() { return dateOfTermination; }
    public void setDateOfTermination(String dateOfTermination) { this.dateOfTermination = dateOfTermination; }
}
