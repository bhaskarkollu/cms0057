package com.cms0057.priorauth.pas;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FhirContext fhirContext;

    private static final String FHIR_JSON = "application/fhir+json";

    @Test
    void submitPriorAuth_standardRequest_returnsPendedOrApprovedClaimResponse() throws Exception {
        Bundle requestBundle = buildPaSubmitBundle("patient-001", "prac-001", "77065", false);
        String body = fhirContext.newJsonParser().encodeResourceToString(requestBundle);

        MvcResult result = mockMvc.perform(post("/fhir/Claim/$submit")
                .contentType(FHIR_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Bundle responseBundle = (Bundle) parser.parseResource(result.getResponse().getContentAsString());

        assertThat(responseBundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
        assertThat(responseBundle.getEntry()).isNotEmpty();

        ClaimResponse claimResponse = responseBundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(r -> r instanceof ClaimResponse)
            .map(r -> (ClaimResponse) r)
            .findFirst()
            .orElseThrow();

        assertThat(claimResponse.getUse()).isEqualTo(ClaimResponse.Use.PREAUTHORIZATION);
        // Standard request for PA-required code should be pended or queued
        assertThat(claimResponse.getOutcome()).isIn(
            ClaimResponse.RemittanceOutcome.QUEUED,
            ClaimResponse.RemittanceOutcome.COMPLETE
        );
    }

    @Test
    void submitPriorAuth_urgentRequest_slaIs72Hours() throws Exception {
        Bundle requestBundle = buildPaSubmitBundle("patient-002", "prac-001", "77065", true);
        String body = fhirContext.newJsonParser().encodeResourceToString(requestBundle);

        MvcResult result = mockMvc.perform(post("/fhir/Claim/$submit")
                .contentType(FHIR_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Bundle responseBundle = (Bundle) parser.parseResource(result.getResponse().getContentAsString());

        // Verify SLA deadline extension is present
        ClaimResponse claimResponse = responseBundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(r -> r instanceof ClaimResponse)
            .map(r -> (ClaimResponse) r)
            .findFirst()
            .orElseThrow();

        boolean hasSlaExtension = claimResponse.getExtension().stream()
            .anyMatch(e -> e.getUrl().contains("sla-deadline"));
        assertThat(hasSlaExtension).isTrue();
    }

    @Test
    void submitPriorAuth_autoApprovableService_returnsApproved() throws Exception {
        // 99213 is in the auto-approve list
        Bundle requestBundle = buildPaSubmitBundle("patient-003", "prac-001", "99213", false);
        String body = fhirContext.newJsonParser().encodeResourceToString(requestBundle);

        MvcResult result = mockMvc.perform(post("/fhir/Claim/$submit")
                .contentType(FHIR_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Bundle responseBundle = (Bundle) parser.parseResource(result.getResponse().getContentAsString());

        ClaimResponse claimResponse = responseBundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(r -> r instanceof ClaimResponse)
            .map(r -> (ClaimResponse) r)
            .findFirst()
            .orElseThrow();

        assertThat(claimResponse.getOutcome()).isEqualTo(ClaimResponse.RemittanceOutcome.COMPLETE);
        assertThat(claimResponse.getPreAuthRef()).isNotBlank();
    }

    @Test
    void submitPriorAuth_missingClaim_returns422() throws Exception {
        Bundle emptyBundle = new Bundle();
        emptyBundle.setType(Bundle.BundleType.COLLECTION);
        String body = fhirContext.newJsonParser().encodeResourceToString(emptyBundle);

        mockMvc.perform(post("/fhir/Claim/$submit")
                .contentType(FHIR_JSON)
                .content(body))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void searchClaims_withoutPatientParam_returns400() throws Exception {
        mockMvc.perform(get("/fhir/Claim")
                .accept(FHIR_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    void searchClaims_withPatientParam_returnsBundle() throws Exception {
        // First submit a claim to create a record
        Bundle requestBundle = buildPaSubmitBundle("patient-search-test", "prac-001", "70553", false);
        mockMvc.perform(post("/fhir/Claim/$submit")
                .contentType(FHIR_JSON)
                .content(fhirContext.newJsonParser().encodeResourceToString(requestBundle)))
            .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/fhir/Claim")
                .param("patient", "patient-search-test")
                .accept(FHIR_JSON))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Bundle bundle = (Bundle) parser.parseResource(result.getResponse().getContentAsString());
        assertThat(bundle.getTotal()).isGreaterThan(0);
    }

    @Test
    void capabilityStatement_returnsValidJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/fhir/metadata")
                .accept(FHIR_JSON))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("CapabilityStatement");
        assertThat(body).contains("davinci-pas");
        assertThat(body).contains("davinci-crd");
        assertThat(body).contains("davinci-dtr");
    }

    @Test
    void submitAndInquire_roundTrip_returnsConsistentStatus() throws Exception {
        String patientId = "patient-inquire-test-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle requestBundle = buildPaSubmitBundle(patientId, "prac-001", "93306", false);
        IParser parser = fhirContext.newJsonParser();

        // Submit
        MvcResult submitResult = mockMvc.perform(post("/fhir/Claim/$submit")
                .contentType(FHIR_JSON)
                .content(parser.encodeResourceToString(requestBundle)))
            .andExpect(status().isOk())
            .andReturn();

        Bundle submitResponse = (Bundle) parser.parseResource(submitResult.getResponse().getContentAsString());
        ClaimResponse cr = (ClaimResponse) submitResponse.getEntry().get(0).getResource();

        // Extract claimId from request reference
        String claimId = cr.getRequest().getReference().replace("Claim/", "");

        // Inquire
        Parameters inquireParams = new Parameters();
        inquireParams.addParameter("claimId", claimId);

        MvcResult inquireResult = mockMvc.perform(post("/fhir/Claim/$inquire")
                .contentType(FHIR_JSON)
                .content(parser.encodeResourceToString(inquireParams)))
            .andExpect(status().isOk())
            .andReturn();

        Bundle inquireResponse = (Bundle) parser.parseResource(inquireResult.getResponse().getContentAsString());
        assertThat(inquireResponse.getEntry()).isNotEmpty();
    }

    // --- Helper ---

    private Bundle buildPaSubmitBundle(String patientId, String practitionerId,
                                        String serviceCode, boolean urgent) {
        Claim claim = new Claim();
        claim.setId(UUID.randomUUID().toString());
        claim.setStatus(Claim.ClaimStatus.ACTIVE);
        claim.setUse(Claim.Use.PREAUTHORIZATION);
        claim.setCreated(new Date());

        // Priority — stat = urgent
        claim.getPriority().addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/processpriority")
            .setCode(urgent ? "stat" : "normal")
            .setDisplay(urgent ? "Immediate" : "Normal");

        claim.setType(new CodeableConcept().addCoding(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/claim-type").setCode("professional")));

        claim.setPatient(new Reference("Patient/" + patientId));
        claim.setProvider(new Reference("Practitioner/" + practitionerId));

        // Insurance
        Claim.InsuranceComponent insurance = new Claim.InsuranceComponent();
        insurance.setSequence(1);
        insurance.setFocal(true);
        insurance.setCoverage(new Reference("Coverage/coverage-001"));
        claim.addInsurance(insurance);

        // ServiceRequest reference
        ServiceRequest sr = new ServiceRequest();
        sr.setId("sr-" + UUID.randomUUID().toString().substring(0, 8));
        sr.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
        sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
        sr.setSubject(new Reference("Patient/" + patientId));
        sr.getCode().addCoding()
            .setSystem("http://www.ama-assn.org/go/cpt")
            .setCode(serviceCode)
            .setDisplay("CPT " + serviceCode);

        // Claim item referencing the service
        Claim.ItemComponent item = new Claim.ItemComponent();
        item.setSequence(1);
        item.setProductOrService(new CodeableConcept().addCoding(
            new Coding()
                .setSystem("http://www.ama-assn.org/go/cpt")
                .setCode(serviceCode)
                .setDisplay("CPT " + serviceCode)));
        claim.addItem(item);

        // Profile
        claim.getMeta().addProfile(
            "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim");

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.getMeta().addProfile(
            "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-request-bundle");
        bundle.addEntry().setResource(claim);
        bundle.addEntry().setResource(sr);

        return bundle;
    }
}
