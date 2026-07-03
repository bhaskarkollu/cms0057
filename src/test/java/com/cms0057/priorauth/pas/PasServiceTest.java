package com.cms0057.priorauth.pas;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.cms0057.priorauth.pas.model.PriorAuthRecord;
import com.cms0057.priorauth.pas.model.PriorAuthRepository;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasServiceTest {

    @Mock private PriorAuthRepository repository;

    private ClaimResponseBuilder responseBuilder;
    private PasService pasService;

    private final FhirContext fhirContext = FhirContext.forR4();
    private final IParser parser = fhirContext.newJsonParser();

    @BeforeEach
    void setUp() {
        responseBuilder = new ClaimResponseBuilder();
        ReflectionTestUtils.setField(responseBuilder, "payerId",   "PAYER001");
        ReflectionTestUtils.setField(responseBuilder, "payerName", "Test Payer");
        ReflectionTestUtils.setField(responseBuilder, "payerNpi",  "1234567890");

        pasService = new PasService(repository, responseBuilder, parser);
        ReflectionTestUtils.setField(pasService, "urgentResponseHours",  72);
        ReflectionTestUtils.setField(pasService, "standardResponseDays", 7);

        // lenient: not every test path calls save() (e.g. inquire, search, early-throw tests)
        lenient().when(repository.save(any(PriorAuthRecord.class))).thenAnswer(inv -> {
            PriorAuthRecord r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID().toString());
            return r;
        });
    }

    // --- $submit: bundle validation ---

    @Test
    void submitPriorAuth_emptyBundle_throwsIllegalArgument() {
        Bundle empty = new Bundle();
        empty.setType(Bundle.BundleType.COLLECTION);

        assertThatThrownBy(() -> pasService.submitPriorAuth(empty))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Claim");
    }

    @Test
    void submitPriorAuth_bundleWithNoClaim_throwsIllegalArgument() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.addEntry().setResource(new Patient());

        assertThatThrownBy(() -> pasService.submitPriorAuth(bundle))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- $submit: auto-approve ---

    @ParameterizedTest(name = "CPT {0} is auto-approved")
    @ValueSource(strings = {"99213", "99214", "99215", "G0101", "G0102"})
    void submitPriorAuth_autoApprovableCode_returnsApprovedResponse(String cpt) {
        Bundle bundle = buildPaBundle(cpt, false);

        Bundle response = pasService.submitPriorAuth(bundle);

        ClaimResponse cr = extractClaimResponse(response);
        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.COMPLETE);
    }

    @Test
    void submitPriorAuth_autoApprovable_recordHasPriorAuthNumber() {
        Bundle bundle = buildPaBundle("99213", false);

        pasService.submitPriorAuth(bundle);

        ArgumentCaptor<PriorAuthRecord> captor = ArgumentCaptor.forClass(PriorAuthRecord.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        boolean anyApproved = captor.getAllValues().stream()
            .anyMatch(r -> "approved".equals(r.getStatus()) && r.getPriorAuthNumber() != null);
        assertThat(anyApproved).isTrue();
    }

    // --- $submit: pended ---

    @ParameterizedTest(name = "CPT {0} is pended")
    @ValueSource(strings = {"77065", "70553", "93306", "27447"})
    void submitPriorAuth_paRequiredCode_returnsPendedResponse(String cpt) {
        Bundle bundle = buildPaBundle(cpt, false);

        Bundle response = pasService.submitPriorAuth(bundle);

        ClaimResponse cr = extractClaimResponse(response);
        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.QUEUED);
    }

    @Test
    void submitPriorAuth_paRequiredCode_recordStatusIsPended() {
        Bundle bundle = buildPaBundle("77065", false);

        pasService.submitPriorAuth(bundle);

        ArgumentCaptor<PriorAuthRecord> captor = ArgumentCaptor.forClass(PriorAuthRecord.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        boolean anyPended = captor.getAllValues().stream()
            .anyMatch(r -> "pended".equals(r.getStatus()));
        assertThat(anyPended).isTrue();
    }

    // --- $submit: SLA deadlines ---

    @Test
    void submitPriorAuth_standardPriority_slaIs7Days() {
        Bundle bundle = buildPaBundle("77065", false);

        pasService.submitPriorAuth(bundle);

        ArgumentCaptor<PriorAuthRecord> captor = ArgumentCaptor.forClass(PriorAuthRecord.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        PriorAuthRecord saved = captor.getAllValues().stream()
            .filter(r -> r.getSlaDeadline() != null).findFirst().orElseThrow();

        long days = ChronoUnit.DAYS.between(saved.getSubmittedAt(), saved.getSlaDeadline());
        assertThat(days).isEqualTo(7);
    }

    @Test
    void submitPriorAuth_urgentPriority_slaIs72Hours() {
        Bundle bundle = buildPaBundle("77065", true);

        pasService.submitPriorAuth(bundle);

        ArgumentCaptor<PriorAuthRecord> captor = ArgumentCaptor.forClass(PriorAuthRecord.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        PriorAuthRecord saved = captor.getAllValues().stream()
            .filter(r -> r.getSlaDeadline() != null).findFirst().orElseThrow();

        long hours = ChronoUnit.HOURS.between(saved.getSubmittedAt(), saved.getSlaDeadline());
        assertThat(hours).isEqualTo(72);
    }

    @Test
    void submitPriorAuth_urgentPriority_recordPriorityIsUrgent() {
        Bundle bundle = buildPaBundle("77065", true);

        pasService.submitPriorAuth(bundle);

        ArgumentCaptor<PriorAuthRecord> captor = ArgumentCaptor.forClass(PriorAuthRecord.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        boolean anyUrgent = captor.getAllValues().stream()
            .anyMatch(r -> "urgent".equals(r.getPriority()));
        assertThat(anyUrgent).isTrue();
    }

    // --- $submit: response bundle structure ---

    @Test
    void submitPriorAuth_responseBundle_containsClaimResponseAndTask() {
        Bundle response = pasService.submitPriorAuth(buildPaBundle("77065", false));

        boolean hasClaimResponse = response.getEntry().stream()
            .anyMatch(e -> e.getResource() instanceof ClaimResponse);
        boolean hasTask = response.getEntry().stream()
            .anyMatch(e -> e.getResource() instanceof Task);

        assertThat(hasClaimResponse).isTrue();
        assertThat(hasTask).isTrue();
    }

    @Test
    void submitPriorAuth_claimResponseLinksBackToClaim() {
        Claim claim = buildClaim("claim-link-test", "77065", false);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.addEntry().setResource(claim);

        Bundle response = pasService.submitPriorAuth(bundle);

        ClaimResponse cr = extractClaimResponse(response);
        assertThat(cr.getRequest().getReference()).contains("claim-link-test");
    }

    // --- $inquire ---

    @Test
    void inquirePriorAuth_existingClaim_returnsPendedResponse() {
        PriorAuthRecord rec = pendedRecord("claim-999");
        when(repository.findByClaimId("claim-999")).thenReturn(Optional.of(rec));

        Bundle response = pasService.inquirePriorAuth("claim-999");

        ClaimResponse cr = extractClaimResponse(response);
        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.QUEUED);
    }

    @Test
    void inquirePriorAuth_approvedClaim_returnsApprovedResponse() {
        PriorAuthRecord rec = pendedRecord("claim-approved");
        rec.setStatus("approved");
        rec.setPriorAuthNumber("PA-001");
        when(repository.findByClaimId("claim-approved")).thenReturn(Optional.of(rec));

        Bundle response = pasService.inquirePriorAuth("claim-approved");

        ClaimResponse cr = extractClaimResponse(response);
        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.COMPLETE);
    }

    @Test
    void inquirePriorAuth_deniedClaim_returnsDeniedResponse() {
        PriorAuthRecord rec = pendedRecord("claim-denied");
        rec.setStatus("denied");
        rec.setNotes("Not medically necessary");
        when(repository.findByClaimId("claim-denied")).thenReturn(Optional.of(rec));

        Bundle response = pasService.inquirePriorAuth("claim-denied");

        ClaimResponse cr = extractClaimResponse(response);
        assertThat(cr.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.ERROR);
    }

    @Test
    void inquirePriorAuth_nonExistentClaim_throwsIllegalArgument() {
        when(repository.findByClaimId("no-such-claim")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pasService.inquirePriorAuth("no-such-claim"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no-such-claim");
    }

    // --- getPatientPriorAuths ---

    @Test
    void getPatientPriorAuths_returnsSearchsetBundle() {
        when(repository.findByPatientId("p-001")).thenReturn(List.of(
            pendedRecord("c-1"),
            pendedRecord("c-2")
        ));

        Bundle bundle = pasService.getPatientPriorAuths("p-001");

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.SEARCHSET);
        assertThat(bundle.getTotal()).isEqualTo(2);
        assertThat(bundle.getEntry()).hasSize(2);
    }

    @Test
    void getPatientPriorAuths_noRecords_returnsEmptyBundle() {
        when(repository.findByPatientId("p-none")).thenReturn(List.of());

        Bundle bundle = pasService.getPatientPriorAuths("p-none");

        assertThat(bundle.getTotal()).isZero();
        assertThat(bundle.getEntry()).isEmpty();
    }

    // --- Helpers ---

    private Bundle buildPaBundle(String cpt, boolean urgent) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.addEntry().setResource(buildClaim(UUID.randomUUID().toString(), cpt, urgent));
        return bundle;
    }

    private Claim buildClaim(String id, String cpt, boolean urgent) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setStatus(Claim.ClaimStatus.ACTIVE);
        claim.setUse(Claim.Use.PREAUTHORIZATION);
        claim.setPatient(new Reference("Patient/patient-001"));
        claim.setProvider(new Reference("Practitioner/prac-001"));
        claim.getPriority().addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/processpriority")
            .setCode(urgent ? "stat" : "normal");
        claim.addInsurance().setSequence(1).setFocal(true)
            .setCoverage(new Reference("Coverage/cov-001"));
        claim.addItem().setSequence(1)
            .setProductOrService(new CodeableConcept().addCoding(
                new Coding().setSystem("http://www.ama-assn.org/go/cpt").setCode(cpt)));
        return claim;
    }

    private ClaimResponse extractClaimResponse(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(r -> r instanceof ClaimResponse)
            .map(r -> (ClaimResponse) r)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No ClaimResponse in response bundle"));
    }

    private PriorAuthRecord pendedRecord(String claimId) {
        PriorAuthRecord r = new PriorAuthRecord();
        r.setId(UUID.randomUUID().toString());
        r.setClaimId(claimId);
        r.setPatientId("patient-001");
        r.setStatus("pended");
        r.setPriority("standard");
        r.setSubmittedAt(Instant.now());
        r.setSlaDeadline(Instant.now().plus(7, ChronoUnit.DAYS));
        return r;
    }
}
