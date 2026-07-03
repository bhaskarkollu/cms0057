package com.cms0057.priorauth.crd;

import com.cms0057.priorauth.crd.model.CdsHookRequest;
import com.cms0057.priorauth.crd.model.CdsHookResponse;
import com.cms0057.priorauth.crd.model.CdsServiceDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "CRD", description = "Coverage Requirements Discovery — CDS Hooks 2.0")
public class CrdController {

    private static final Logger log = LoggerFactory.getLogger(CrdController.class);

    private final CrdService crdService;

    public CrdController(CrdService crdService) {
        this.crdService = crdService;
    }

    @Operation(
        summary = "CDS Hooks service discovery",
        description = "Returns all supported CDS Hook services. Public endpoint — no authentication required per CDS Hooks spec section 3.",
        security = {}
    )
    @ApiResponse(responseCode = "200", description = "List of available CDS services",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CdsServiceDefinition.CdsServicesResponse.class)))
    @GetMapping(value = "/cds-services", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CdsServiceDefinition.CdsServicesResponse> discoverServices() {
        log.debug("CDS Hooks discovery requested");
        return ResponseEntity.ok(crdService.getServiceDiscovery());
    }

    @Operation(
        summary = "order-sign hook",
        description = """
            Fired by the EHR when a provider is about to sign one or more orders. \
            Returns CDS Hook cards indicating whether prior authorization is required \
            for the ordered service(s) per Da Vinci CRD IG section 4.1.

            CPT codes that trigger a PA-required card:
            `77065–77067` (mammography), `70551/70553` (MRI brain), \
            `93306–93308` (echocardiography), `27447` (total knee), `43239` (upper GI endoscopy).
            """
    )
    @ApiResponse(responseCode = "200", description = "CDS Hook cards with coverage requirement decisions",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CdsHookResponse.class),
            examples = @ExampleObject(name = "PA Required", value = """
                {
                  "cards": [{
                    "uuid": "a1b2c3d4-...",
                    "summary": "Prior Authorization Required",
                    "indicator": "warning",
                    "detail": "Service code 77065 requires prior authorization.",
                    "source": { "label": "CMS0057 Demo Payer" },
                    "links": [{
                      "label": "Start Prior Authorization (DTR)",
                      "url": "http://localhost:3000/dtr?serviceCode=77065",
                      "type": "smart"
                    }]
                  }]
                }
                """)))
    @PostMapping(
        value = "/cds-services/cms0057-crd-order-sign",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CdsHookResponse> orderSign(@RequestBody CdsHookRequest request) {
        log.info("order-sign hook received, hookInstance={}", request.getHookInstance());
        return ResponseEntity.ok(crdService.processOrderSign(request));
    }

    @Operation(
        summary = "order-select hook",
        description = "Fired when a provider selects an order for entry. Returns early coverage guidance before the order is finalized."
    )
    @ApiResponse(responseCode = "200", description = "Coverage guidance cards",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CdsHookResponse.class)))
    @PostMapping(
        value = "/cds-services/cms0057-crd-order-select",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CdsHookResponse> orderSelect(@RequestBody CdsHookRequest request) {
        log.info("order-select hook received, hookInstance={}", request.getHookInstance());
        return ResponseEntity.ok(crdService.processOrderSelect(request));
    }

    @Operation(
        summary = "appointment-book hook",
        description = "Fired when booking a patient appointment. Checks coverage and PA requirements for the appointment type."
    )
    @ApiResponse(responseCode = "200", description = "Appointment coverage cards",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CdsHookResponse.class)))
    @PostMapping(
        value = "/cds-services/cms0057-crd-appointment-book",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CdsHookResponse> appointmentBook(@RequestBody CdsHookRequest request) {
        log.info("appointment-book hook received, hookInstance={}", request.getHookInstance());
        return ResponseEntity.ok(crdService.processAppointmentBook(request));
    }
}
