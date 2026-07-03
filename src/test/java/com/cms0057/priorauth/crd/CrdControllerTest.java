package com.cms0057.priorauth.crd;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.cms0057.priorauth.crd.model.CdsHookRequest;
import com.cms0057.priorauth.crd.model.CdsHookResponse;
import com.cms0057.priorauth.crd.model.CdsServiceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FhirContext fhirContext;

    @Test
    void discoverServices_returnsThreeCdsHooks() throws Exception {
        MvcResult result = mockMvc.perform(get("/cds-services"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        CdsServiceDefinition.CdsServicesResponse response =
            objectMapper.readValue(result.getResponse().getContentAsString(),
                CdsServiceDefinition.CdsServicesResponse.class);

        assertThat(response.getServices()).hasSize(3);
        assertThat(response.getServices())
            .extracting(CdsServiceDefinition::getHook)
            .containsExactlyInAnyOrder("order-sign", "order-select", "appointment-book");
    }

    @Test
    void orderSign_withPaRequiredServiceCode_returnsPaRequiredCard() throws Exception {
        CdsHookRequest hookRequest = buildOrderSignRequest("77065"); // Mammography — requires PA

        MvcResult result = mockMvc.perform(post("/cds-services/cms0057-crd-order-sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hookRequest)))
            .andExpect(status().isOk())
            .andReturn();

        CdsHookResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), CdsHookResponse.class);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards())
            .anyMatch(c -> c.getSummary().contains("Prior Authorization Required"));
        assertThat(response.getCards())
            .anyMatch(c -> "warning".equals(c.getIndicator()));
    }

    @Test
    void orderSign_withNoPaRequiredServiceCode_returnsNoPaCard() throws Exception {
        CdsHookRequest hookRequest = buildOrderSignRequest("99213"); // E&M visit — no PA needed

        MvcResult result = mockMvc.perform(post("/cds-services/cms0057-crd-order-sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hookRequest)))
            .andExpect(status().isOk())
            .andReturn();

        CdsHookResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), CdsHookResponse.class);

        // Should return at least one card (no-PA card)
        assertThat(response.getCards()).isNotEmpty();
    }

    @Test
    void orderSign_withEmptyContext_returnsNoPaCard() throws Exception {
        CdsHookRequest hookRequest = new CdsHookRequest();
        hookRequest.setHookInstance(UUID.randomUUID().toString());
        hookRequest.setHook("order-sign");
        hookRequest.setContext(Map.of());

        MvcResult result = mockMvc.perform(post("/cds-services/cms0057-crd-order-sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hookRequest)))
            .andExpect(status().isOk())
            .andReturn();

        CdsHookResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), CdsHookResponse.class);
        assertThat(response.getCards()).isNotEmpty();
    }

    @Test
    void appointmentBook_returnsAppointmentCoverageCard() throws Exception {
        CdsHookRequest hookRequest = new CdsHookRequest();
        hookRequest.setHookInstance(UUID.randomUUID().toString());
        hookRequest.setHook("appointment-book");
        hookRequest.setContext(Map.of("patientId", "patient-001"));

        MvcResult result = mockMvc.perform(post("/cds-services/cms0057-crd-appointment-book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hookRequest)))
            .andExpect(status().isOk())
            .andReturn();

        CdsHookResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), CdsHookResponse.class);
        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards().get(0).getSummary()).contains("Appointment");
    }

    // --- Helper ---

    private CdsHookRequest buildOrderSignRequest(String serviceCode) throws Exception {
        IParser parser = fhirContext.newJsonParser();

        ServiceRequest sr = new ServiceRequest();
        sr.setId("sr-001");
        sr.setStatus(ServiceRequest.ServiceRequestStatus.DRAFT);
        sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
        sr.getCode().addCoding()
            .setSystem("http://www.ama-assn.org/go/cpt")
            .setCode(serviceCode)
            .setDisplay("Test Service " + serviceCode);

        Bundle draftOrders = new Bundle();
        draftOrders.setType(Bundle.BundleType.COLLECTION);
        draftOrders.addEntry().setResource(sr);

        String draftOrdersJson = parser.encodeResourceToString(draftOrders);
        Object draftOrdersObj = objectMapper.readValue(draftOrdersJson, Object.class);

        CdsHookRequest request = new CdsHookRequest();
        request.setHookInstance(UUID.randomUUID().toString());
        request.setHook("order-sign");
        request.setContext(Map.of(
            "patientId", "patient-001",
            "userId", "Practitioner/prac-001",
            "draftOrders", draftOrdersObj
        ));

        return request;
    }
}
