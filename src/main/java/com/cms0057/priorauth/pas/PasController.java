package com.cms0057.priorauth.pas;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fhir")
@Tag(name = "PAS", description = "Prior Authorization Support — Da Vinci PAS FHIR operations")
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

    @Operation(
        summary = "Submit prior authorization request — $submit",
        description = """
            Submits a Da Vinci PAS Bundle containing a prior authorization request.

            The Bundle must include a PAS-profiled `Claim` resource \
            (`http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim`) \
            plus supporting resources: `Coverage`, `Patient`, `Practitioner`, `ServiceRequest`, \
            and any `DocumentReference` resources gathered via the DTR workflow.

            **Response:**
            - `outcome=complete` — immediate approval (auto-approvable service codes: 99213–99215, G0101, G0102)
            - `outcome=queued` — pended; decision will be provided within the SLA deadline

            **CMS-0057-F SLA:**
            - Standard (`priority=normal`): ≤ 7 calendar days
            - Urgent/expedited (`priority=stat`): ≤ 72 hours
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Da Vinci PAS request Bundle (Claim + supporting resources)",
            content = @Content(mediaType = FHIR_JSON,
                examples = @ExampleObject(name = "Standard PA request", value = """
                    {
                      "resourceType": "Bundle",
                      "type": "collection",
                      "meta": {
                        "profile": ["http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-request-bundle"]
                      },
                      "entry": [{
                        "resource": {
                          "resourceType": "Claim",
                          "id": "claim-001",
                          "meta": {
                            "profile": ["http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim"]
                          },
                          "status": "active",
                          "use": "preauthorization",
                          "type": {
                            "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/claim-type", "code": "professional" }]
                          },
                          "priority": {
                            "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/processpriority", "code": "normal" }]
                          },
                          "patient": { "reference": "Patient/patient-001" },
                          "provider": { "reference": "Practitioner/prac-001" },
                          "created": "2026-07-02T10:00:00Z",
                          "insurance": [{ "sequence": 1, "focal": true, "coverage": { "reference": "Coverage/coverage-001" }}],
                          "item": [{
                            "sequence": 1,
                            "productOrService": {
                              "coding": [{ "system": "http://www.ama-assn.org/go/cpt", "code": "77065", "display": "Diagnostic mammography" }]
                            }
                          }]
                        }
                      }]
                    }
                    """)))
    )
    @ApiResponse(responseCode = "200", description = "PA response Bundle with ClaimResponse and workflow Task",
        content = @Content(mediaType = FHIR_JSON,
            examples = @ExampleObject(name = "Pended response", value = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [{
                    "resource": {
                      "resourceType": "ClaimResponse",
                      "use": "preauthorization",
                      "outcome": "queued",
                      "disposition": "PA request received and pending review. Decision required by 2026-07-09T..."
                    }
                  }]
                }
                """)))
    @ApiResponse(responseCode = "422", description = "Invalid request — Bundle must contain a Claim",
        content = @Content(mediaType = FHIR_JSON))
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

    @Operation(
        summary = "Query PA status — $inquire",
        description = """
            Polls the current status of an existing prior authorization request. \
            Send a FHIR `Parameters` resource with a `claimId` parameter containing \
            the Claim ID from the original $submit request.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = FHIR_JSON,
                examples = @ExampleObject(value = """
                    {
                      "resourceType": "Parameters",
                      "parameter": [{ "name": "claimId", "valueString": "claim-001" }]
                    }
                    """)))
    )
    @ApiResponse(responseCode = "200", description = "Current PA status as a ClaimResponse Bundle",
        content = @Content(mediaType = FHIR_JSON))
    @ApiResponse(responseCode = "404", description = "No PA request found for the given claimId",
        content = @Content(mediaType = FHIR_JSON))
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

    @Operation(
        summary = "Search PA requests by patient",
        description = "Returns a FHIR Bundle (searchset) of all prior authorization Claim resources for the given patient."
    )
    @ApiResponse(responseCode = "200", description = "Bundle of PA Claim resources",
        content = @Content(mediaType = FHIR_JSON))
    @ApiResponse(responseCode = "400", description = "Missing required patient parameter",
        content = @Content(mediaType = FHIR_JSON))
    @GetMapping(value = "/Claim", produces = FHIR_JSON)
    public ResponseEntity<String> searchClaims(
            @Parameter(description = "Patient FHIR ID", example = "patient-001")
            @RequestParam(name = "patient", required = false) String patientId) {
        log.info("GET /fhir/Claim?patient={}", patientId);
        if (patientId == null) {
            return ResponseEntity.badRequest()
                .body(fhirJsonParser.encodeResourceToString(buildOutcome("patient parameter is required")));
        }
        Bundle bundle = pasService.getPatientPriorAuths(patientId);
        return ResponseEntity.ok(fhirJsonParser.encodeResourceToString(bundle));
    }

    @Operation(
        summary = "FHIR CapabilityStatement",
        description = "Returns the FHIR R4 CapabilityStatement declaring CRD, DTR, and PAS server capabilities.",
        security = {}
    )
    @ApiResponse(responseCode = "200", description = "FHIR CapabilityStatement",
        content = @Content(mediaType = FHIR_JSON))
    @Tag(name = "FHIR")
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
