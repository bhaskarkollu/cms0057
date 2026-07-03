package com.cms0057.priorauth.crd;

import com.cms0057.priorauth.crd.model.CdsHookRequest;
import com.cms0057.priorauth.crd.model.CdsHookResponse;
import com.cms0057.priorauth.crd.model.CdsServiceDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CDS Hooks endpoints implementing Da Vinci CRD (Coverage Requirements Discovery).
 *
 *   GET  /cds-services                          — service discovery (public, no auth)
 *   POST /cds-services/cms0057-crd-order-sign   — order-sign hook
 *   POST /cds-services/cms0057-crd-order-select — order-select hook
 *   POST /cds-services/cms0057-crd-appointment-book — appointment-book hook
 */
@RestController
public class CrdController {

    private static final Logger log = LoggerFactory.getLogger(CrdController.class);

    private final CrdService crdService;

    public CrdController(CrdService crdService) {
        this.crdService = crdService;
    }

    @GetMapping(value = "/cds-services", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CdsServiceDefinition.CdsServicesResponse> discoverServices() {
        log.debug("CDS Hooks discovery requested");
        return ResponseEntity.ok(crdService.getServiceDiscovery());
    }

    @PostMapping(
        value = "/cds-services/cms0057-crd-order-sign",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CdsHookResponse> orderSign(@RequestBody CdsHookRequest request) {
        log.info("order-sign hook received, hookInstance={}", request.getHookInstance());
        return ResponseEntity.ok(crdService.processOrderSign(request));
    }

    @PostMapping(
        value = "/cds-services/cms0057-crd-order-select",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CdsHookResponse> orderSelect(@RequestBody CdsHookRequest request) {
        log.info("order-select hook received, hookInstance={}", request.getHookInstance());
        return ResponseEntity.ok(crdService.processOrderSelect(request));
    }

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
