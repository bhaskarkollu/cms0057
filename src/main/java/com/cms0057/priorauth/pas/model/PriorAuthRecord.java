package com.cms0057.priorauth.pas.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "prior_auth_records")
public class PriorAuthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String claimId;

    @Column(nullable = false)
    private String patientId;

    private String practitionerId;
    private String organizationId;
    private String coverageId;
    private String serviceCode;
    private String serviceDescription;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant decidedAt;

    @Column(nullable = false)
    private Instant slaDeadline;

    private Boolean slaCompliant;
    private String priorAuthNumber;

    @Column(length = 2000)
    private String notes;

    private String questionnaireResponseRef;

    public PriorAuthRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClaimId() { return claimId; }
    public void setClaimId(String v) { this.claimId = v; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String v) { this.patientId = v; }

    public String getPractitionerId() { return practitionerId; }
    public void setPractitionerId(String v) { this.practitionerId = v; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String v) { this.organizationId = v; }

    public String getCoverageId() { return coverageId; }
    public void setCoverageId(String v) { this.coverageId = v; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String v) { this.serviceCode = v; }

    public String getServiceDescription() { return serviceDescription; }
    public void setServiceDescription(String v) { this.serviceDescription = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant v) { this.submittedAt = v; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant v) { this.decidedAt = v; }

    public Instant getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(Instant v) { this.slaDeadline = v; }

    public Boolean getSlaCompliant() { return slaCompliant; }
    public void setSlaCompliant(Boolean v) { this.slaCompliant = v; }

    public String getPriorAuthNumber() { return priorAuthNumber; }
    public void setPriorAuthNumber(String v) { this.priorAuthNumber = v; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }

    public String getQuestionnaireResponseRef() { return questionnaireResponseRef; }
    public void setQuestionnaireResponseRef(String v) { this.questionnaireResponseRef = v; }
}
