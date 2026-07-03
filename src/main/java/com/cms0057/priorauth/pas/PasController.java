package com.cms0057.priorauth.pas;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Prior Authorization Support (PAS) FHIR endpoints per Da Vinci PAS IG.
 *
 *   POST /fhir/Claim/$submit    — submit a new PA request (Bundle → Bundle)
 *   POST /fhir/Claim/$inquire   — query status of an existing PA
 *   GET  /fhir/Claim            — search PA requests by patient
 *   GET  /fhir/metadata         — FHIR CapabilityStatement
 *
 * CMS-0057-F response time mandates:
 *   Urgent (expedited): ≤ 72 hours
 *   Standard:           ≤ 7 calendar days
 */
@RestController
@RequestMapping("/fhir")
public class PasController {

    private static final Logger log = LoggerFactory.getLogger(PasController.class);
    private static final String FHIR_JSON = "application/fhir+json";

    private final PasService pasService;
    private final IParser fhirJsonParser;
    private final ObjectMapper objectMapper;

    public PasController(PasService pasService, IParser fhirJsonParser, ObjectMapper objectMapper) {
        this.pasService = pasService;
        this.fhirJsonParser = fhirJsonParser;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /fhir/Claim/$submit
     *
     * Provider submits a PA request as a Da Vinci PAS Bundle (Claim + Coverage +
     * Patient + Practitioner + ServiceRequest + DocumentReference gathered via DTR).
     * Returns a Bundle with ClaimResponse:
     *   - outcome=complete  → immediate approval (auto-approvable service)
     *   - outcome=queued    → pended, with SLA deadline per CMS-0057-F
     */
    @PostMapping(
        value = "/Claim/$submit",
        consumes = {FHIR_JSON, "application/json"},
        produces = FHIR_JSON
    )
    public ResponseEntity<String> submitPriorAuth(@RequestBody String body) {
        log.info("PAS $submit received");
        try {
            Bundle requestBundle = (Bundle) fhirJsonParser.parseResource(body);
            Bundle responseBundle = pasService.submitPriorAuth(requestBundle);
            return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(responseBundle));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PAS $submit request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(fhirJsonParser.encodeResourceToString(buildOutcome(e.getMessage())));
        }
    }

    /**
     * POST /fhir/Claim/$inquire
     *
     * Provider queries status of an existing PA. Request body is a FHIR Parameters
     * resource with parameter name "claimId". Returns current ClaimResponse Bundle.
     */
    @PostMapping(
        value = "/Claim/$inquire",
        consumes = {FHIR_JSON, "application/json"},
        produces = FHIR_JSON
    )
    public ResponseEntity<String> inquirePriorAuth(@RequestBody String body) {
        log.info("PAS $inquire received");
        try {
            String claimId = extractClaimId(body);
            Bundle responseBundle = pasService.inquirePriorAuth(claimId);
            return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(responseBundle));
        } catch (IllegalArgumentException e) {
            log.warn("PAS $inquire failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(fhirJsonParser.encodeResourceToString(buildOutcome(e.getMessage())));
        }
    }

    /**
     * GET /fhir/Claim?patient={patientId}
     * Returns all PA requests for a given patient.
     */
    @GetMapping(value = "/Claim", produces = FHIR_JSON)
    public ResponseEntity<String> searchClaims(
            @RequestParam(name = "patient", required = false) String patientId) {
        log.info("GET /fhir/Claim?patient={}", patientId);
        if (patientId == null) {
            return ResponseEntity.badRequest()
                .body(fhirJsonParser.encodeResourceToString(buildOutcome("patient parameter is required")));
        }
        Bundle bundle = pasService.getPatientPriorAuths(patientId);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(bundle));
    }

    /**
     * GET /fhir/metadata — FHIR CapabilityStatement declaring PAS/CRD/DTR support.
     */
    @GetMapping(value = "/metadata", produces = FHIR_JSON)
    public ResponseEntity<String> capabilityStatement() {
        String cs = """
            {
              "resourceType": "CapabilityStatement",
              "status": "active",
              "date": "2026-07-02",
              "publisher": "CMS-0057 Prior Authorization Service",
              "kind": "instance",
              "software": {"name": "cms0057-prior-auth-service", "version": "1.0.0"},
              "fhirVersion": "4.0.1",
              "format": ["application/fhir+json"],
              "implementationGuide": [
                "http://hl7.org/fhir/us/davinci-pas/ImplementationGuide/hl7.fhir.us.davinci-pas",
                "http://hl7.org/fhir/us/davinci-crd/ImplementationGuide/hl7.fhir.us.davinci-crd",
                "http://hl7.org/fhir/us/davinci-dtr/ImplementationGuide/hl7.fhir.us.davinci-dtr"
              ],
              "rest": [{
                "mode": "server",
                "resource": [
                  {"type": "Claim", "operation": [{"name": "$submit"}, {"name": "$inquire"}]},
                  {"type": "ClaimResponse", "interaction": [{"code": "read"}]},
                  {"type": "Questionnaire", "interaction": [{"code": "read"}, {"code": "search-type"}]},
                  {"type": "QuestionnaireResponse", "interaction": [{"code": "create"}, {"code": "update"}]},
                  {"type": "Coverage", "interaction": [{"code": "read"}]},
                  {"type": "ServiceRequest", "interaction": [{"code": "read"}]},
                  {"type": "Patient", "interaction": [{"code": "read"}, {"code": "search-type"}]}
                ]
              }]
            }
            """;
        return ResponseEntity.ok(cs);
    }

    private String extractClaimId(String body) {
        try {
            Parameters params = (Parameters) fhirJsonParser.parseResource(body);
            return params.getParameter().stream()
                .filter(p -> "claimId".equals(p.getName()) && p.getValue() != null)
                .map(p -> p.getValue().primitiveValue())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("claimId parameter not found"));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            try {
                String value = objectMapper.readTree(body).path("claimId").asText();
                if (value.isBlank()) throw new IllegalArgumentException("claimId parameter not found");
                return value;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Could not extract claimId from request body");
            }
        }
    }

    private OperationOutcome buildOutcome(String message) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.INVALID)
            .setDiagnostics(message);
        return outcome;
    }
}
