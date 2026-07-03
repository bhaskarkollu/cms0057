package com.cms0057.priorauth.crd;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.cms0057.priorauth.crd.model.CdsHookRequest;
import com.cms0057.priorauth.crd.model.CdsHookResponse;
import com.cms0057.priorauth.crd.model.CdsServiceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CrdServiceTest {

    private CrdService crdService;

    private final FhirContext fhirContext = FhirContext.forR4();
    private final IParser fhirParser = fhirContext.newJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        crdService = new CrdService(fhirContext, fhirParser, objectMapper);
        ReflectionTestUtils.setField(crdService, "payerName", "Test Payer");
        ReflectionTestUtils.setField(crdService, "dtrSmartLaunchUrl", "http://dtr.example.com");
    }

    // --- Service discovery ---

    @Test
    void getServiceDiscovery_returnsThreeServices() {
        CdsServiceDefinition.CdsServicesResponse response = crdService.getServiceDiscovery();

        assertThat(response.getServices()).hasSize(3);
        assertThat(response.getServices())
            .extracting(CdsServiceDefinition::getHook)
            .containsExactlyInAnyOrder("order-sign", "order-select", "appointment-book");
    }

    @Test
    void getServiceDiscovery_eachServiceHasIdAndPrefetch() {
        CdsServiceDefinition.CdsServicesResponse response = crdService.getServiceDiscovery();

        response.getServices().forEach(svc -> {
            assertThat(svc.getId()).isNotBlank();
            assertThat(svc.getPrefetch()).isNotEmpty();
        });
    }

    // --- order-sign: PA required ---

    @ParameterizedTest(name = "CPT {0} triggers PA-required card")
    @ValueSource(strings = {"77065", "77066", "77067", "70551", "70553", "93306", "93307", "93308", "27447", "43239"})
    void orderSign_paRequiredCode_returnsWarningCard(String cpt) {
        CdsHookRequest request = buildOrderSignRequest(cpt);

        CdsHookResponse response = crdService.processOrderSign(request);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards())
            .anyMatch(c -> "warning".equals(c.getIndicator()));
        assertThat(response.getCards())
            .anyMatch(c -> c.getSummary().contains("Prior Authorization Required"));
    }

    @ParameterizedTest(name = "CPT {0} PA card links to DTR SMART app")
    @ValueSource(strings = {"77065", "70553", "93306"})
    void orderSign_paRequiredCode_cardLinksContainDtrUrl(String cpt) {
        CdsHookRequest request = buildOrderSignRequest(cpt);

        CdsHookResponse response = crdService.processOrderSign(request);

        boolean hasDtrLink = response.getCards().stream()
            .flatMap(c -> c.getLinks() != null ? c.getLinks().stream() : java.util.stream.Stream.empty())
            .anyMatch(l -> l.getUrl() != null && l.getUrl().contains("dtr.example.com"));
        assertThat(hasDtrLink).isTrue();
    }

    @ParameterizedTest(name = "CPT {0} also triggers documentation card")
    @ValueSource(strings = {"77065", "27447"})
    void orderSign_paRequiredCode_returnsDocumentationCard(String cpt) {
        CdsHookRequest request = buildOrderSignRequest(cpt);

        CdsHookResponse response = crdService.processOrderSign(request);

        assertThat(response.getCards())
            .anyMatch(c -> c.getSummary().contains("Documentation Required"));
    }

    // --- order-sign: no PA needed ---

    @ParameterizedTest(name = "CPT {0} returns no-PA info card")
    @ValueSource(strings = {"99213", "99214", "G0101", "90791"})
    void orderSign_nonPaCode_returnsNoPaInfoCard(String cpt) {
        CdsHookRequest request = buildOrderSignRequest(cpt);

        CdsHookResponse response = crdService.processOrderSign(request);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getIndicator()).isEqualTo("info");
        assertThat(response.getCards().get(0).getSummary()).contains("No Prior Authorization");
    }

    @Test
    void orderSign_emptyContext_returnsNoPaCard() {
        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance("inst-001");
        request.setHook("order-sign");
        request.setContext(Map.of());

        CdsHookResponse response = crdService.processOrderSign(request);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getSummary()).contains("No Prior Authorization");
    }

    @Test
    void orderSign_nullContext_returnsNoPaCard() {
        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance("inst-002");
        request.setHook("order-sign");

        CdsHookResponse response = crdService.processOrderSign(request);

        assertThat(response.getCards()).hasSize(1);
    }

    // --- order-select ---

    @Test
    void orderSelect_withServiceRequest_returnsGuidanceCard() {
        CdsHookRequest request = buildOrderSignRequest("77065");
        request.setHook("order-select");

        CdsHookResponse response = crdService.processOrderSelect(request);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards().get(0).getIndicator()).isEqualTo("info");
    }

    @Test
    void orderSelect_emptyContext_returnsNoPaCard() {
        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance("inst-003");
        request.setContext(Map.of());

        CdsHookResponse response = crdService.processOrderSelect(request);

        assertThat(response.getCards()).hasSize(1);
    }

    // --- appointment-book ---

    @Test
    void appointmentBook_returnsAppointmentCard() {
        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance("inst-004");
        request.setHook("appointment-book");
        request.setContext(Map.of("patientId", "patient-001"));

        CdsHookResponse response = crdService.processAppointmentBook(request);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getSummary()).contains("Appointment");
    }

    @Test
    void appointmentBook_cardHasPayerSource() {
        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance("inst-005");
        request.setContext(Map.of());

        CdsHookResponse response = crdService.processAppointmentBook(request);

        assertThat(response.getCards().get(0).getSource()).isNotNull();
        assertThat(response.getCards().get(0).getSource().getLabel()).isEqualTo("Test Payer");
    }

    // --- All cards have a UUID ---

    @Test
    void orderSign_allCardsHaveUuid() {
        CdsHookRequest request = buildOrderSignRequest("77065");

        CdsHookResponse response = crdService.processOrderSign(request);

        response.getCards().forEach(card ->
            assertThat(card.getUuid()).isNotBlank());
    }

    // --- Helpers ---

    private CdsHookRequest buildOrderSignRequest(String cpt) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId("sr-test");
        sr.setStatus(ServiceRequest.ServiceRequestStatus.DRAFT);
        sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
        sr.getCode().addCoding()
            .setSystem("http://www.ama-assn.org/go/cpt")
            .setCode(cpt)
            .setDisplay("Test " + cpt);

        Bundle draftOrders = new Bundle();
        draftOrders.setType(Bundle.BundleType.COLLECTION);
        draftOrders.addEntry().setResource(sr);

        Object draftOrdersObj;
        try {
            draftOrdersObj = objectMapper.readValue(fhirParser.encodeResourceToString(draftOrders), Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance("test-instance");
        request.setHook("order-sign");
        request.setContext(Map.of(
            "patientId", "patient-001",
            "userId", "Practitioner/prac-001",
            "draftOrders", draftOrdersObj
        ));
        return request;
    }
}
