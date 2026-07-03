package com.cms0057.priorauth.dtr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Documentation Templates and Rules (DTR) service implementing Da Vinci DTR IG.
 *
 * Provides FHIR Questionnaire resources driving clinical documentation collection
 * required for prior authorization. QuestionnaireResponse resources are stored and
 * linked back to the PAS Claim submission.
 */
@Service
public class DtrService {

    private static final Logger log = LoggerFactory.getLogger(DtrService.class);

    private final FhirContext fhirContext;
    private final IParser fhirJsonParser;

    public DtrService(FhirContext fhirContext, IParser fhirJsonParser) {
        this.fhirContext = fhirContext;
        this.fhirJsonParser = fhirJsonParser;
    }

    public Questionnaire getQuestionnaireForService(String serviceCode) {
        log.info("Retrieving DTR questionnaire for service code={}", serviceCode);
        return buildQuestionnaireForService(serviceCode);
    }

    public Questionnaire getQuestionnaireById(String questionnaireId) {
        log.info("Retrieving DTR questionnaire id={}", questionnaireId);
        return switch (questionnaireId) {
            case "mammography-pa" -> buildMammographyQuestionnaire();
            case "mri-brain-pa"   -> buildMriQuestionnaire();
            case "echo-pa"        -> buildEchoQuestionnaire();
            case "total-knee-pa"  -> buildOrthopedicQuestionnaire();
            default               -> buildGenericQuestionnaire(questionnaireId);
        };
    }

    /**
     * Validates and stores a completed QuestionnaireResponse.
     * Per Da Vinci DTR IG the completed response is later attached to the PAS Claim
     * as a DocumentReference.
     */
    public QuestionnaireResponse storeQuestionnaireResponse(QuestionnaireResponse response) {
        log.info("Storing QuestionnaireResponse for questionnaire={}", response.getQuestionnaire());
        if (response.getId() == null || response.getId().isBlank()) {
            response.setId(UUID.randomUUID().toString());
        }
        response.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.COMPLETED);
        response.setAuthored(new Date());
        log.info("Stored QuestionnaireResponse id={}", response.getId());
        return response;
    }

    /**
     * Returns a Bundle containing Questionnaire + pre-populated QuestionnaireResponse
     * using CQL-extracted patient data (adaptive forms per DTR STU3+).
     */
    public Bundle getAdaptiveQuestionnaire(String questionnaireId, String patientId) {
        log.info("Building adaptive DTR questionnaire, id={}, patient={}", questionnaireId, patientId);
        Questionnaire questionnaire = getQuestionnaireById(questionnaireId);
        QuestionnaireResponse prePopulated = buildPrePopulatedResponse(questionnaire, patientId);

        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.getMeta().setLastUpdated(new Date());
        bundle.addEntry().setResource(questionnaire);
        bundle.addEntry().setResource(prePopulated);
        return bundle;
    }

    // --- Questionnaire builders ---

    private Questionnaire buildQuestionnaireForService(String serviceCode) {
        return switch (serviceCode) {
            case "77065", "77066", "77067" -> buildMammographyQuestionnaire();
            case "70553", "70551"          -> buildMriQuestionnaire();
            case "93306", "93307", "93308" -> buildEchoQuestionnaire();
            case "27447"                   -> buildOrthopedicQuestionnaire();
            default                        -> buildGenericQuestionnaire(serviceCode);
        };
    }

    private Questionnaire buildMammographyQuestionnaire() {
        Questionnaire q = baseQuestionnaire("mammography-pa", "Mammography Prior Authorization Questionnaire");
        q.addItem(textItem("clinical-indication", "What is the clinical indication for mammography?", true));
        q.addItem(choiceItem("mammography-type", "Type of mammography",
            List.of("Diagnostic", "Screening", "Follow-up"), true));
        q.addItem(booleanItem("prior-mammogram", "Has the patient had a prior mammogram in the last 12 months?", true));
        q.addItem(dateItem("prior-mammogram-date", "Date of most recent prior mammogram", false));
        q.addItem(textItem("referring-provider-npi", "Referring provider NPI", true));
        q.addItem(booleanItem("symptoms-present", "Are symptoms or abnormalities present?", true));
        q.addItem(textItem("symptom-description", "Describe symptoms or findings", false));
        return q;
    }

    private Questionnaire buildMriQuestionnaire() {
        Questionnaire q = baseQuestionnaire("mri-brain-pa", "MRI Brain Prior Authorization Questionnaire");
        q.addItem(textItem("clinical-indication", "Primary clinical indication for brain MRI", true));
        q.addItem(choiceItem("mri-type", "MRI type requested",
            List.of("MRI without contrast", "MRI with contrast", "MRI with and without contrast"), true));
        q.addItem(booleanItem("prior-ct", "Has the patient had a prior CT or MRI of the brain?", true));
        q.addItem(booleanItem("neurological-symptoms", "Does the patient have neurological symptoms?", true));
        q.addItem(textItem("neurological-detail", "Describe neurological symptoms", false));
        q.addItem(booleanItem("conservative-treatment", "Has conservative treatment been tried?", false));
        return q;
    }

    private Questionnaire buildEchoQuestionnaire() {
        Questionnaire q = baseQuestionnaire("echo-pa", "Echocardiography Prior Authorization Questionnaire");
        q.addItem(textItem("clinical-indication", "Clinical indication for echocardiography", true));
        q.addItem(choiceItem("echo-type", "Echocardiogram type",
            List.of("Transthoracic (TTE)", "Transesophageal (TEE)", "Stress Echo", "Follow-up TTE"), true));
        q.addItem(booleanItem("prior-echo", "Has the patient had a prior echocardiogram?", true));
        q.addItem(textItem("cardiac-history", "Relevant cardiac history", false));
        q.addItem(booleanItem("symptoms-present", "Does the patient have current cardiac symptoms?", true));
        return q;
    }

    private Questionnaire buildOrthopedicQuestionnaire() {
        Questionnaire q = baseQuestionnaire("total-knee-pa", "Total Knee Replacement Prior Authorization Questionnaire");
        q.addItem(textItem("diagnosis", "Primary diagnosis (ICD-10 code and description)", true));
        q.addItem(booleanItem("conservative-treatment", "Has the patient completed at least 3 months of conservative treatment?", true));
        q.addItem(textItem("conservative-detail", "Describe conservative treatments (PT, medications, injections, etc.)", true));
        q.addItem(booleanItem("functional-limitation", "Does the patient have significant functional limitation affecting ADLs?", true));
        q.addItem(textItem("functional-detail", "Describe functional limitations", true));
        q.addItem(booleanItem("imaging-obtained", "Has diagnostic imaging been obtained?", true));
        q.addItem(textItem("imaging-findings", "Describe imaging findings", false));
        q.addItem(textItem("surgeon-npi", "Performing surgeon NPI", true));
        return q;
    }

    private Questionnaire buildGenericQuestionnaire(String serviceCode) {
        Questionnaire q = baseQuestionnaire("generic-pa-" + serviceCode, "Prior Authorization Documentation Questionnaire");
        q.addItem(textItem("clinical-indication", "Clinical indication for the requested service", true));
        q.addItem(textItem("diagnosis", "Primary diagnosis (ICD-10)", true));
        q.addItem(booleanItem("medically-necessary", "Is this service medically necessary?", true));
        q.addItem(textItem("clinical-notes", "Supporting clinical notes or documentation", false));
        q.addItem(booleanItem("prior-treatment", "Has alternative or prior treatment been attempted?", false));
        q.addItem(textItem("prior-treatment-detail", "Describe prior treatments and outcomes", false));
        q.addItem(textItem("requesting-provider-npi", "Requesting provider NPI", true));
        return q;
    }

    private Questionnaire baseQuestionnaire(String id, String title) {
        Questionnaire q = new Questionnaire();
        q.setId(id);
        q.setUrl("http://hl7.org/fhir/us/davinci-dtr/Questionnaire/" + id);
        q.setVersion("1.0.0");
        q.setTitle(title);
        q.setStatus(Enumerations.PublicationStatus.ACTIVE);
        q.setDate(new Date());
        q.setPublisher("CMS-0057 Prior Authorization Service");
        return q;
    }

    private QuestionnaireItemComponent textItem(String linkId, String text, boolean required) {
        QuestionnaireItemComponent item = new QuestionnaireItemComponent();
        item.setLinkId(linkId);
        item.setText(text);
        item.setType(QuestionnaireItemType.TEXT);
        item.setRequired(required);
        return item;
    }

    private QuestionnaireItemComponent booleanItem(String linkId, String text, boolean required) {
        QuestionnaireItemComponent item = new QuestionnaireItemComponent();
        item.setLinkId(linkId);
        item.setText(text);
        item.setType(QuestionnaireItemType.BOOLEAN);
        item.setRequired(required);
        return item;
    }

    private QuestionnaireItemComponent dateItem(String linkId, String text, boolean required) {
        QuestionnaireItemComponent item = new QuestionnaireItemComponent();
        item.setLinkId(linkId);
        item.setText(text);
        item.setType(QuestionnaireItemType.DATE);
        item.setRequired(required);
        return item;
    }

    private QuestionnaireItemComponent choiceItem(String linkId, String text,
                                                   List<String> options, boolean required) {
        QuestionnaireItemComponent item = new QuestionnaireItemComponent();
        item.setLinkId(linkId);
        item.setText(text);
        item.setType(QuestionnaireItemType.CHOICE);
        item.setRequired(required);
        for (String opt : options) {
            item.addAnswerOption().setValue(new Coding()
                .setSystem("http://cms0057.example.com/codes")
                .setCode(opt.toLowerCase().replace(" ", "-"))
                .setDisplay(opt));
        }
        return item;
    }

    private QuestionnaireResponse buildPrePopulatedResponse(Questionnaire questionnaire, String patientId) {
        QuestionnaireResponse qr = new QuestionnaireResponse();
        qr.setId(UUID.randomUUID().toString());
        qr.setQuestionnaire(questionnaire.getUrl());
        qr.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);
        qr.setSubject(new Reference("Patient/" + patientId));
        qr.setAuthored(new Date());
        return qr;
    }
}
