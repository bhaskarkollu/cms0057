package com.cms0057.priorauth.dtr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * DTR (Documentation Templates and Rules) FHIR endpoints per Da Vinci DTR IG.
 *
 *   GET  /fhir/Questionnaire/{id}                     — retrieve questionnaire by ID
 *   GET  /fhir/Questionnaire?serviceCode={code}       — search by service code
 *   GET  /fhir/Questionnaire/{id}/$next-question      — adaptive form next question
 *   POST /fhir/QuestionnaireResponse                  — store completed response
 *   PUT  /fhir/QuestionnaireResponse/{id}             — update in-progress response
 */
@RestController
@RequestMapping("/fhir")
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

    @GetMapping(value = "/Questionnaire/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> getQuestionnaire(@PathVariable String id) {
        log.info("GET Questionnaire/{}", id);
        Questionnaire q = dtrService.getQuestionnaireById(id);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(q));
    }

    @GetMapping(value = "/Questionnaire", produces = FHIR_JSON)
    public ResponseEntity<String> searchQuestionnaires(
            @RequestParam(name = "serviceCode", required = false) String serviceCode) {
        log.info("GET Questionnaire?serviceCode={}", serviceCode);
        Questionnaire q = dtrService.getQuestionnaireForService(serviceCode != null ? serviceCode : "generic");
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(1);
        bundle.addEntry().setResource(q);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(bundle));
    }

    @GetMapping(value = "/Questionnaire/{id}/$next-question", produces = FHIR_JSON)
    public ResponseEntity<String> getAdaptiveQuestionnaire(
            @PathVariable String id,
            @RequestParam(name = "patient") String patientId) {
        log.info("GET Questionnaire/{}/$next-question, patient={}", id, patientId);
        Bundle bundle = dtrService.getAdaptiveQuestionnaire(id, patientId);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(bundle));
    }

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

    @PutMapping(
        value = "/QuestionnaireResponse/{id}",
        consumes = {FHIR_JSON, MediaType.APPLICATION_JSON_VALUE},
        produces = FHIR_JSON
    )
    public ResponseEntity<String> updateQuestionnaireResponse(
            @PathVariable String id,
            @RequestBody String body) {
        log.info("PUT QuestionnaireResponse/{}", id);
        QuestionnaireResponse response = (QuestionnaireResponse) fhirJsonParser.parseResource(body);
        response.setId(id);
        QuestionnaireResponse stored = dtrService.storeQuestionnaireResponse(response);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(stored));
    }
}
