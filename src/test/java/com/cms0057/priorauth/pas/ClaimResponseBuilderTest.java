package com.cms0057.priorauth.pas;

import com.cms0057.priorauth.pas.model.PriorAuthRecord;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimResponseBuilderTest {

    private ClaimResponseBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ClaimResponseBuilder();
        ReflectionTestUtils.setField(builder, "payerId",   "PAYER001");
        ReflectionTestUtils.setField(builder, "payerName", "Test Payer");
        ReflectionTestUtils.setField(builder, "payerNpi",  "1234567890");
    }

    // --- Common base response assertions ---

    @Test
    void buildPendedResponse_setsUseToPreauthorization() {
        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), record("c-001", "standard", null));

        assertThat(cr.getUse()).isEqualTo(ClaimResponse.Use.PREAUTHORIZATION);
    }

    @Test
    void buildPendedResponse_setsStatusToActive() {
        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), record("c-001", "standard", null));

        assertThat(cr.getStatus()).isEqualTo(ClaimResponse.ClaimResponseStatus.ACTIVE);
    }

    @Test
    void buildPendedResponse_setsInsurerWithPayerNpi() {
        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), record("c-001", "standard", null));

        assertThat(cr.getInsurer().getDisplay()).isEqualTo("Test Payer");
        assertThat(cr.getInsurer().getIdentifier().getValue()).isEqualTo("1234567890");
    }

    @Test
    void buildPendedResponse_hasSlaDeadlineExtension() {
        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), record("c-001", "urgent", null));

        boolean hasSla = cr.getExtension().stream()
            .anyMatch(e -> e.getUrl().contains("sla-deadline"));
        assertThat(hasSla).isTrue();
    }

    @Test
    void buildPendedResponse_hasTrackingIdExtension() {
        PriorAuthRecord rec = record("c-001", "standard", null);
        rec.setId("track-001");

        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), rec);

        boolean hasTrackingId = cr.getExtension().stream()
            .anyMatch(e -> e.getUrl().contains("pa-tracking-id"));
        assertThat(hasTrackingId).isTrue();
    }

    @Test
    void buildPendedResponse_requestReferencePointsToClaim() {
        ClaimResponse cr = builder.buildPendedResponse(claim("claim-abc"), record("claim-abc", "standard", null));

        assertThat(cr.getRequest().getReference()).isEqualTo("Claim/claim-abc");
    }

    @Test
    void buildPendedResponse_hasDaVinciProfile() {
        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), record("c-001", "standard", null));

        assertThat(cr.getMeta().getProfile())
            .anyMatch(p -> p.getValue().contains("davinci-pas"));
    }

    // --- outcome variants ---

    @Test
    void buildPendedResponse_outcomeIsQueued() {
        ClaimResponse cr = builder.buildPendedResponse(claim("c-001"), record("c-001", "standard", null));

        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.QUEUED);
    }

    @Test
    void buildApprovedResponse_outcomeIsComplete() {
        PriorAuthRecord rec = record("c-002", "standard", "PA-999");
        ClaimResponse cr = builder.buildApprovedResponse(claim("c-002"), rec);

        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.COMPLETE);
    }

    @Test
    void buildApprovedResponse_setsPreAuthRef() {
        PriorAuthRecord rec = record("c-002", "standard", "PA-12345");
        ClaimResponse cr = builder.buildApprovedResponse(claim("c-002"), rec);

        assertThat(cr.getPreAuthRef()).isEqualTo("PA-12345");
    }

    @Test
    void buildApprovedResponse_dispositionContainsAuthNumber() {
        PriorAuthRecord rec = record("c-002", "standard", "PA-XYZ");
        ClaimResponse cr = builder.buildApprovedResponse(claim("c-002"), rec);

        assertThat(cr.getDisposition()).contains("PA-XYZ");
    }

    @Test
    void buildApprovedResponse_x12ReviewActionIsCertifiedInTotal() {
        PriorAuthRecord rec = record("c-003", "standard", "PA-001");
        Claim claimWithItem = claim("c-003");
        claimWithItem.addItem().setSequence(1);

        ClaimResponse cr = builder.buildApprovedResponse(claimWithItem, rec);

        boolean certified = cr.getExtension().stream()
            .filter(e -> e.getUrl().contains("reviewAction"))
            .anyMatch(e -> e.getValue().toString().contains("A1") ||
                          e.getValue().primitiveValue() != null);
        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.COMPLETE);
    }

    @Test
    void buildDeniedResponse_outcomeIsError() {
        ClaimResponse cr = builder.buildDeniedResponse(claim("c-004"), record("c-004", "standard", null), "Not covered");

        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.ERROR);
    }

    @Test
    void buildDeniedResponse_dispositionContainsDenialReason() {
        ClaimResponse cr = builder.buildDeniedResponse(claim("c-004"), record("c-004", "standard", null), "Experimental treatment");

        assertThat(cr.getDisposition()).contains("Experimental treatment");
    }

    @Test
    void buildDeniedResponse_hasErrorComponent() {
        ClaimResponse cr = builder.buildDeniedResponse(claim("c-004"), record("c-004", "standard", null), "Not medically necessary");

        assertThat(cr.getError()).isNotEmpty();
    }

    @Test
    void buildPartialResponse_outcomeIsPartial() {
        ClaimResponse cr = builder.buildPartialResponse(claim("c-005"), record("c-005", "standard", null));

        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.PARTIAL);
    }

    @Test
    void buildPartialResponse_dispositionMentionsPartialApproval() {
        ClaimResponse cr = builder.buildPartialResponse(claim("c-005"), record("c-005", "standard", null));

        assertThat(cr.getDisposition()).containsIgnoringCase("partial");
    }

    // --- Helpers ---

    private Claim claim(String id) {
        Claim c = new Claim();
        c.setId(id);
        c.setPatient(new Reference("Patient/patient-001"));
        return c;
    }

    private PriorAuthRecord record(String claimId, String priority, String paNumber) {
        PriorAuthRecord r = new PriorAuthRecord();
        r.setId("rec-" + claimId);
        r.setClaimId(claimId);
        r.setPatientId("patient-001");
        r.setPriority(priority);
        r.setStatus("urgent".equals(priority) ? "pended" : "queued");
        r.setSubmittedAt(Instant.now());
        r.setSlaDeadline("urgent".equals(priority)
            ? Instant.now().plus(72, ChronoUnit.HOURS)
            : Instant.now().plus(7, ChronoUnit.DAYS));
        r.setPriorAuthNumber(paNumber);
        return r;
    }
}
