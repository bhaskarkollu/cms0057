package com.cms0057.priorauth.dtr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DtrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FhirContext fhirContext;

    private static final String FHIR_JSON = "application/fhir+json";

    @Test
    void getQuestionnaire_mammography_returnsValidQuestionnaire() throws Exception {
        MvcResult result = mockMvc.perform(get("/fhir/Questionnaire/mammography-pa")
                .accept(FHIR_JSON))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Questionnaire q = (Questionnaire) parser.parseResource(result.getResponse().getContentAsString());

        assertThat(q.getTitle()).contains("Mammography");
        assertThat(q.getItem()).isNotEmpty();
        assertThat(q.getStatus()).isEqualTo(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);
    }

    @Test
    void searchQuestionnaires_byServiceCode_returnsBundle() throws Exception {
        MvcResult result = mockMvc.perform(get("/fhir/Questionnaire")
                .param("serviceCode", "77065")
                .accept(FHIR_JSON))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Bundle bundle = (Bundle) parser.parseResource(result.getResponse().getContentAsString());

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.SEARCHSET);
        assertThat(bundle.getTotal()).isGreaterThan(0);
        assertThat(bundle.getEntry()).isNotEmpty();
    }

    @Test
    void getAdaptiveQuestionnaire_returnsQuestionnaireAndPrePopulatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/fhir/Questionnaire/mri-brain-pa/$next-question")
                .param("patient", "patient-123")
                .accept(FHIR_JSON))
            .andExpect(status().isOk())
            .andReturn();

        IParser parser = fhirContext.newJsonParser();
        Bundle bundle = (Bundle) parser.parseResource(result.getResponse().getContentAsString());

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
        assertThat(bundle.getEntry()).hasSize(2);

        boolean hasQuestionnaire = bundle.getEntry().stream()
            .anyMatch(e -> e.getResource() instanceof Questionnaire);
        boolean hasQuestionnaireResponse = bundle.getEntry().stream()
            .anyMatch(e -> e.getResource() instanceof QuestionnaireResponse);

        assertThat(hasQuestionnaire).isTrue();
        assertThat(hasQuestionnaireResponse).isTrue();
    }

    @Test
    void createQuestionnaireResponse_returnsCreatedWithLocation() throws Exception {
        IParser parser = fhirContext.newJsonParser();

        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setQuestionnaire("http://hl7.org/fhir/us/davinci-dtr/Questionnaire/mammography-pa");
        qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
        qr.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/patient-001"));
        qr.setAuthored(new Date());
        qr.addItem().setLinkId("clinical-indication").addAnswer()
            .setValue(new org.hl7.fhir.r4.model.StringType("Screening per USPSTF guidelines"));

        String body = parser.encodeResourceToString(qr);

        MvcResult result = mockMvc.perform(post("/fhir/QuestionnaireResponse")
                .contentType(FHIR_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

        QuestionnaireResponse stored = (QuestionnaireResponse)
            parser.parseResource(result.getResponse().getContentAsString());
        assertThat(stored.getId()).isNotBlank();
        assertThat(stored.getStatus())
            .isEqualTo(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
    }

    @Test
    void updateQuestionnaireResponse_returnsUpdatedResponse() throws Exception {
        IParser parser = fhirContext.newJsonParser();

        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setId("qr-update-test-001");
        qr.setQuestionnaire("http://hl7.org/fhir/us/davinci-dtr/Questionnaire/generic-pa-99213");
        qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);

        String body = parser.encodeResourceToString(qr);

        MvcResult result = mockMvc.perform(put("/fhir/QuestionnaireResponse/qr-update-test-001")
                .contentType(FHIR_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        QuestionnaireResponse updated = (QuestionnaireResponse)
            parser.parseResource(result.getResponse().getContentAsString());
        assertThat(updated.getIdElement().getIdPart()).isEqualTo("qr-update-test-001");
    }
}
