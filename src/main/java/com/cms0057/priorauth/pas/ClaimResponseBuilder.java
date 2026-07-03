package com.cms0057.priorauth.pas;

import com.cms0057.priorauth.pas.model.PriorAuthRecord;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Builds Da Vinci PAS-profiled FHIR ClaimResponse resources from internal PA records.
 *
 * Profile: http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse
 */
@Component
public class ClaimResponseBuilder {

    @Value("${cms0057.compliance.payer-id}")
    private String payerId;

    @Value("${cms0057.compliance.payer-name}")
    private String payerName;

    @Value("${cms0057.compliance.payer-npi}")
    private String payerNpi;

    /** Queued/pending — payer has up to 72h (urgent) or 7 days (standard) per CMS-0057-F. */
    public ClaimResponse buildPendedResponse(Claim claim, PriorAuthRecord record) {
        ClaimResponse response = baseResponse(claim, record);
        response.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
        response.setDisposition("PA request received and pending review. "
            + "Decision required by " + record.getSlaDeadline() + " per CMS-0057-F.");
        response.addExtension()
            .setUrl("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction")
            .setValue(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://codesystem.x12.org/005010/306")
                .setCode("A4").setDisplay("Pended")));
        return response;
    }

    /** Approved with prior authorization number. */
    public ClaimResponse buildApprovedResponse(Claim claim, PriorAuthRecord record) {
        ClaimResponse response = baseResponse(claim, record);
        response.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
        response.setDisposition("Prior Authorization APPROVED. Auth#: " + record.getPriorAuthNumber());
        response.setPreAuthRef(record.getPriorAuthNumber());
        response.addExtension()
            .setUrl("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction")
            .setValue(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://codesystem.x12.org/005010/306")
                .setCode("A1").setDisplay("Certified in total")));
        for (Claim.ItemComponent item : claim.getItem()) {
            response.addItem()
                .setItemSequence(item.getSequence())
                .addAdjudication()
                .setCategory(new CodeableConcept().addCoding(new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/adjudication")
                    .setCode("submitted")));
        }
        return response;
    }

    /** Denied with coded reason. */
    public ClaimResponse buildDeniedResponse(Claim claim, PriorAuthRecord record, String denialReason) {
        ClaimResponse response = baseResponse(claim, record);
        response.setOutcome(ClaimResponse.RemittanceOutcome.ERROR);
        response.setDisposition("Prior Authorization DENIED. Reason: " + denialReason);
        response.addExtension()
            .setUrl("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction")
            .setValue(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://codesystem.x12.org/005010/306")
                .setCode("A3").setDisplay("Not Certified")));
        response.addError().setCode(new CodeableConcept().addCoding(new Coding()
            .setSystem("http://terminology.hl7.org/CodeSystem/adjudication-error")
            .setCode("not-covered").setDisplay("Not Covered")));
        return response;
    }

    /** Partial approval — some items approved, some denied. */
    public ClaimResponse buildPartialResponse(Claim claim, PriorAuthRecord record) {
        ClaimResponse response = baseResponse(claim, record);
        response.setOutcome(ClaimResponse.RemittanceOutcome.PARTIAL);
        response.setDisposition("Prior Authorization PARTIALLY APPROVED. See item-level decisions.");
        response.addExtension()
            .setUrl("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction")
            .setValue(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://codesystem.x12.org/005010/306")
                .setCode("A2").setDisplay("Certified in part")));
        return response;
    }

    private ClaimResponse baseResponse(Claim claim, PriorAuthRecord record) {
        ClaimResponse response = new ClaimResponse();
        response.setId(UUID.randomUUID().toString());
        response.getMeta()
            .addProfile("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse");
        response.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
        response.setType(new CodeableConcept().addCoding(new Coding()
            .setSystem("http://terminology.hl7.org/CodeSystem/claim-type")
            .setCode("professional")));
        response.setUse(ClaimResponse.Use.PREAUTHORIZATION);
        response.setPatient(claim.getPatient());
        response.setCreated(new Date());
        response.setInsurer(new Reference()
            .setIdentifier(new Identifier()
                .setSystem("http://hl7.org/fhir/sid/us-npi").setValue(payerNpi))
            .setDisplay(payerName));
        response.setRequest(new Reference("Claim/" + claim.getIdElement().getIdPart()));
        response.addExtension()
            .setUrl("http://cms0057.example.com/fhir/StructureDefinition/sla-deadline")
            .setValue(new InstantType(Date.from(record.getSlaDeadline())));
        response.addExtension()
            .setUrl("http://cms0057.example.com/fhir/StructureDefinition/pa-tracking-id")
            .setValue(new StringType(record.getId()));
        return response;
    }
}
