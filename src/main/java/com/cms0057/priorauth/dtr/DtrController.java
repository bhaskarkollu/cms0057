package com.cms0057.priorauth.dtr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fhir")
@Tag(name = "DTR", description = "Documentation Templates and Rules — FHIR Questionnaire endpoints")
public class DtrController {

    private static final Logger log = LoggerFactory.getLogger(DtrController.class);
    private static final String FHIR_JSON = "application/fhir+json";

    private final DtrService dtrService;
    private final IParser fhirJsonParser;

    public DtrController(DtrService dtrService, IParser fhirJsonParser,
                         @SuppressWarnings("unused") FhirContext fhirContext) {
        this.dtrService = dtrService;
        this.fhirJsonParser = fhirJsonParser;
    }

    @Operation(
        summary = "Get Questionnaire by ID",
        description = """
            Returns a FHIR R4 Questionnaire resource for the given questionnaire ID. \
            Available IDs: `mammography-pa`, `mri-brain-pa`, `echo-pa`, `total-knee-pa`, \
            or `generic-pa-{cptCode}` for any other service.
            """
    )
    @ApiResponse(responseCode = "200", description = "FHIR Questionnaire resource",
        content = @Content(mediaType = FHIR_JSON))
    @GetMapping(value = "/Questionnaire/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> getQuestionnaire(
            @Parameter(description = "Questionnaire ID (e.g. mammography-pa, mri-brain-pa)",
                example = "mammography-pa")
            @PathVariable String id) {
        log.info("GET Questionnaire/{}", id);
        Questionnaire q = dtrService.getQuestionnaireById(id);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(q));
    }

    @Operation(
        summary = "Search Questionnaires by service code",
        description = "Returns a FHIR Bundle (searchset) containing the Questionnaire for the given CPT service code."
    )
    @ApiResponse(responseCode = "200", description = "FHIR Bundle (searchset) of matching Questionnaires",
        content = @Content(mediaType = FHIR_JSON))
    @GetMapping(value = "/Questionnaire", produces = FHIR_JSON)
    public ResponseEntity<String> searchQuestionnaires(
            @Parameter(description = "CPT service code to find questionnaire for", example = "77065")
            @RequestParam(name = "serviceCode", required = false) String serviceCode) {
        log.info("GET Questionnaire?serviceCode={}", serviceCode);
        Questionnaire q = dtrService.getQuestionnaireForService(serviceCode != null ? serviceCode : "generic");
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(1);
        bundle.addEntry().setResource(q);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(bundle));
    }

    @Operation(
        summary = "Adaptive form — next question",
        description = """
            Returns a FHIR Bundle containing the Questionnaire and a pre-populated \
            QuestionnaireResponse with answers extracted via CQL from the patient's clinical data \
            (adaptive forms per Da Vinci DTR STU 3).
            """
    )
    @ApiResponse(responseCode = "200", description = "Bundle with Questionnaire + pre-populated QuestionnaireResponse",
        content = @Content(mediaType = FHIR_JSON))
    @GetMapping(value = "/Questionnaire/{id}/$next-question", produces = FHIR_JSON)
    public ResponseEntity<String> getAdaptiveQuestionnaire(
            @Parameter(description = "Questionnaire ID", example = "mammography-pa")
            @PathVariable String id,
            @Parameter(description = "Patient FHIR ID", example = "patient-001")
            @RequestParam(name = "patient") String patientId) {
        log.info("GET Questionnaire/{}/$next-question, patient={}", id, patientId);
        Bundle bundle = dtrService.getAdaptiveQuestionnaire(id, patientId);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(bundle));
    }

    @Operation(
        summary = "Submit completed QuestionnaireResponse",
        description = """
            Stores a completed FHIR QuestionnaireResponse submitted by the DTR SMART app. \
            The stored response is later referenced as a DocumentReference when submitting \
            the PAS Claim ($submit).
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "FHIR QuestionnaireResponse resource",
            content = @Content(mediaType = FHIR_JSON,
                examples = @ExampleObject(value = """
                    {
                      "resourceType": "QuestionnaireResponse",
                      "questionnaire": "http://hl7.org/fhir/us/davinci-dtr/Questionnaire/mammography-pa",
                      "status": "completed",
                      "subject": { "reference": "Patient/patient-001" },
                      "authored": "2026-07-02T10:00:00Z",
                      "item": [{
                        "linkId": "clinical-indication",
                        "answer": [{ "valueString": "Screening per USPSTF guidelines" }]
                      }]
                    }
                    """)))
    )
    @ApiResponse(responseCode = "201", description = "QuestionnaireResponse stored",
        content = @Content(mediaType = FHIR_JSON))
    @PostMapping(
        value = "/QuestionnaireResponse",
        consumes = {FHIR_JSON, MediaType.APPLICATION_JSON_VALUE},
        produces = FHIR_JSON
    )
    public ResponseEntity<String> createQuestionnaireResponse(@RequestBody String body) {
        log.info("POST QuestionnaireResponse");
        QuestionnaireResponse response = (QuestionnaireResponse) fhirJsonParser.parseResource(body);
        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(response);
        return ResponseEntity
            .status(201)
            .header("Location", "/fhir/QuestionnaireResponse/" + stored.getId())
            .body(fhirJsonParser.encodeResourceToString(stored));
    }

    @Operation(
        summary = "Update in-progress QuestionnaireResponse",
        description = "Partially saves a QuestionnaireResponse during an active DTR session (auto-save)."
    )
    @ApiResponse(responseCode = "200", description = "Updated QuestionnaireResponse",
        content = @Content(mediaType = FHIR_JSON))
    @PutMapping(
        value = "/QuestionnaireResponse/{id}",
        consumes = {FHIR_JSON, MediaType.APPLICATION_JSON_VALUE},
        produces = FHIR_JSON
    )
    public ResponseEntity<String> updateQuestionnaireResponse(
            @Parameter(description = "QuestionnaireResponse ID", example = "qr-001")
            @PathVariable String id,
            @RequestBody String body) {
        log.info("PUT QuestionnaireResponse/{}", id);
        QuestionnaireResponse response = (QuestionnaireResponse) fhirJsonParser.parseResource(body);
        response.setId(id);
        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(response);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(stored));
    }
}
