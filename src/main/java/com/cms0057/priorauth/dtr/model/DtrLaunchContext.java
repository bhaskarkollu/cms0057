package com.cms0057.priorauth.dtr.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DtrLaunchContext {

    @JsonProperty("patientId") private String patientId;
    @JsonProperty("encounterId") private String encounterId;
    @JsonProperty("serviceRequestId") private String serviceRequestId;
    @JsonProperty("coverageId") private String coverageId;
    @JsonProperty("questionnaireId") private String questionnaireId;
    @JsonProperty("priorAuthRequired") private Boolean priorAuthRequired;
    @JsonProperty("serviceCode") private String serviceCode;

    public String getPatientId() { return patientId; }
    public String getEncounterId() { return encounterId; }
    public String getServiceRequestId() { return serviceRequestId; }
    public String getCoverageId() { return coverageId; }
    public String getQuestionnaireId() { return questionnaireId; }
    public Boolean getPriorAuthRequired() { return priorAuthRequired; }
    public String getServiceCode() { return serviceCode; }
}
