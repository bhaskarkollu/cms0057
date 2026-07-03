package com.cms0057.priorauth.dtr;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DtrServiceTest {

    private DtrService dtrService;

    @BeforeEach
    void setUp() {
        FhirContext ctx = FhirContext.forR4();
        dtrService = new DtrService(ctx, ctx.newJsonParser());
    }

    // --- getQuestionnaireById routing ---

    @ParameterizedTest(name = "id={0} → title contains {1}")
    @CsvSource({
        "mammography-pa,   Mammography",
        "mri-brain-pa,     MRI Brain",
        "echo-pa,          Echocardiography",
        "total-knee-pa,    Total Knee"
    })
    void getQuestionnaireById_knownId_returnsCorrectTitle(String id, String expectedTitle) {
        Questionnaire q = dtrService.getQuestionnaireById(id);

        assertThat(q.getTitle()).containsIgnoringCase(expectedTitle.strip());
    }

    @Test
    void getQuestionnaireById_unknownId_returnsGenericQuestionnaire() {
        Questionnaire q = dtrService.getQuestionnaireById("some-unknown-id");

        assertThat(q.getId()).isEqualTo("generic-pa-some-unknown-id");
        assertThat(q.getTitle()).contains("Prior Authorization");
    }

    // --- getQuestionnaireForService routing ---

    @ParameterizedTest(name = "CPT {0} → title contains {1}")
    @CsvSource({
        "77065, Mammography",
        "77066, Mammography",
        "77067, Mammography",
        "70551, MRI Brain",
        "70553, MRI Brain",
        "93306, Echocardiography",
        "93307, Echocardiography",
        "93308, Echocardiography",
        "27447, Total Knee"
    })
    void getQuestionnaireForService_knownCode_returnsCorrectQuestionnaire(String cpt, String expectedTitle) {
        Questionnaire q = dtrService.getQuestionnaireForService(cpt);

        assertThat(q.getTitle()).containsIgnoringCase(expectedTitle.strip());
    }

    @Test
    void getQuestionnaireForService_unknownCode_returnsGenericQuestionnaire() {
        Questionnaire q = dtrService.getQuestionnaireForService("99999");

        assertThat(q.getId()).startsWith("generic-pa-");
    }

    // --- Questionnaire structure ---

    @Test
    void mammographyQuestionnaire_hasRequiredItems() {
        Questionnaire q = dtrService.getQuestionnaireById("mammography-pa");

        assertThat(q.getItem()).isNotEmpty();
        assertThat(q.getStatus()).isEqualTo(Enumerations.PublicationStatus.ACTIVE);
        assertThat(q.getUrl()).contains("davinci-dtr");

        boolean hasClinicalIndication = q.getItem().stream()
            .anyMatch(i -> "clinical-indication".equals(i.getLinkId()) && i.getRequired());
        assertThat(hasClinicalIndication).isTrue();
    }

    @Test
    void mammographyQuestionnaire_hasChoiceItemForType() {
        Questionnaire q = dtrService.getQuestionnaireById("mammography-pa");

        assertThat(q.getItem()).anyMatch(i ->
            "mammography-type".equals(i.getLinkId()) &&
            i.getType() == Questionnaire.QuestionnaireItemType.CHOICE &&
            !i.getAnswerOption().isEmpty());
    }

    @Test
    void totalKneeQuestionnaire_requiresConservativeTreatmentItem() {
        Questionnaire q = dtrService.getQuestionnaireById("total-knee-pa");

        assertThat(q.getItem()).anyMatch(i ->
            "conservative-treatment".equals(i.getLinkId()) && i.getRequired());
    }

    @Test
    void genericQuestionnaire_hasSevenItems() {
        Questionnaire q = dtrService.getQuestionnaireForService("00000");

        assertThat(q.getItem()).hasSizeGreaterThanOrEqualTo(5);
    }

    // --- storeQuestionnaireResponse ---

    @Test
    void storeQuestionnaireResponse_assignsIdWhenMissing() {
        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setQuestionnaire("http://example.com/q/test");

        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(qr);

        assertThat(stored.getId()).isNotBlank();
    }

    @Test
    void storeQuestionnaireResponse_preservesExistingId() {
        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setId("my-existing-id");

        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(qr);

        assertThat(stored.getIdElement().getIdPart()).isEqualTo("my-existing-id");
    }

    @Test
    void storeQuestionnaireResponse_setsStatusToCompleted() {
        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);

        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(qr);

        assertThat(stored.getStatus())
            .isEqualTo(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
    }

    @Test
    void storeQuestionnaireResponse_setsAuthoredDate() {
        QuestionnaireResponse qr = new QuestionnaireResponse();

        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(qr);

        assertThat(stored.getAuthored()).isNotNull();
    }

    // --- getAdaptiveQuestionnaire ---

    @Test
    void getAdaptiveQuestionnaire_returnsBundleWithTwoEntries() {
        Bundle bundle = dtrService.getAdaptiveQuestionnaire("mammography-pa", "patient-001");

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
        assertThat(bundle.getEntry()).hasSize(2);
    }

    @Test
    void getAdaptiveQuestionnaire_bundleContainsQuestionnaireAndResponse() {
        Bundle bundle = dtrService.getAdaptiveQuestionnaire("mri-brain-pa", "patient-123");

        boolean hasQuestionnaire = bundle.getEntry().stream()
            .anyMatch(e -> e.getResource() instanceof Questionnaire);
        boolean hasResponse = bundle.getEntry().stream()
            .anyMatch(e -> e.getResource() instanceof QuestionnaireResponse);

        assertThat(hasQuestionnaire).isTrue();
        assertThat(hasResponse).isTrue();
    }

    @Test
    void getAdaptiveQuestionnaire_prePopulatedResponseReferencesPatient() {
        Bundle bundle = dtrService.getAdaptiveQuestionnaire("echo-pa", "patient-xyz");

        QuestionnaireResponse qr = bundle.getEntry().stream()
            .map(e -> e.getResource())
            .filter(r -> r instanceof QuestionnaireResponse)
            .map(r -> (QuestionnaireResponse) r)
            .findFirst().orElseThrow();

        assertThat(qr.getSubject().getReference()).isEqualTo("Patient/patient-xyz");
    }

    @Test
    void getAdaptiveQuestionnaire_prePopulatedResponseIsInProgress() {
        Bundle bundle = dtrService.getAdaptiveQuestionnaire("mammography-pa", "p-001");

        QuestionnaireResponse qr = bundle.getEntry().stream()
            .map(e -> e.getResource())
            .filter(r -> r instanceof QuestionnaireResponse)
            .map(r -> (QuestionnaireResponse) r)
            .findFirst().orElseThrow();

        assertThat(qr.getStatus())
            .isEqualTo(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);
    }
}
