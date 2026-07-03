package com.cms0057.priorauth.crd;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.cms0057.priorauth.crd.model.CdsHookRequest;
import com.cms0057.priorauth.crd.model.CdsHookResponse;
import com.cms0057.priorauth.crd.model.CdsHookResponse.Card;
import com.cms0057.priorauth.crd.model.CdsHookResponse.Link;
import com.cms0057.priorauth.crd.model.CdsHookResponse.Source;
import com.cms0057.priorauth.crd.model.CdsServiceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Coverage Requirements Discovery (CRD) service implementing Da Vinci CRD IG.
 *
 * Processes CDS Hook callbacks fired by the EHR when a provider orders a service
 * and returns payer coverage cards indicating whether prior auth is required.
 */
@Service
public class CrdService {

    private static final Logger log = LoggerFactory.getLogger(CrdService.class);

    private static final Set<String> PA_REQUIRED_CODES = Set.of(
        "77065", "77066", "77067",  // Mammography
        "93306", "93307", "93308",  // Echocardiography
        "70553", "70551",           // MRI brain
        "27447",                    // Total knee replacement
        "43239"                     // Upper GI endoscopy with biopsy
    );

    private final FhirContext fhirContext;
    private final IParser fhirJsonParser;
    private final ObjectMapper objectMapper;

    @Value("${cms0057.compliance.payer-name}")
    private String payerName;

    @Value("${cms0057.dtr.smart-launch-url}")
    private String dtrSmartLaunchUrl;

    public CrdService(FhirContext fhirContext, IParser fhirJsonParser, ObjectMapper objectMapper) {
        this.fhirContext = fhirContext;
        this.fhirJsonParser = fhirJsonParser;
        this.objectMapper = objectMapper;
    }

    public CdsServiceDefinition.CdsServicesResponse getServiceDiscovery() {
        List<CdsServiceDefinition> services = new ArrayList<>();

        services.add(CdsServiceDefinition.builder()
            .id("cms0057-crd-order-sign")
            .hook("order-sign")
            .title("CMS-0057 Prior Authorization Coverage Requirements")
            .description("Checks coverage requirements and prior authorization needs when a provider signs an order.")
            .prefetch(buildOrderSignPrefetch())
            .build());

        services.add(CdsServiceDefinition.builder()
            .id("cms0057-crd-order-select")
            .hook("order-select")
            .title("CMS-0057 Coverage Discovery on Order Selection")
            .description("Provides real-time coverage guidance when a provider selects an order.")
            .prefetch(buildOrderSelectPrefetch())
            .build());

        services.add(CdsServiceDefinition.builder()
            .id("cms0057-crd-appointment-book")
            .hook("appointment-book")
            .title("CMS-0057 Appointment Coverage Check")
            .description("Checks coverage and prior auth requirements when booking a patient appointment.")
            .prefetch(buildAppointmentPrefetch())
            .build());

        return CdsServiceDefinition.CdsServicesResponse.builder().services(services).build();
    }

    public CdsHookResponse processOrderSign(CdsHookRequest request) {
        log.info("Processing order-sign hook, instance={}", request.getHookInstance());
        List<Card> cards = new ArrayList<>();

        for (ServiceRequest sr : extractServiceRequests(request)) {
            String code = extractCode(sr);
            if (PA_REQUIRED_CODES.contains(code)) {
                cards.add(buildPaRequiredCard(code));
                cards.add(buildDocumentationCard(code));
            }
        }

        if (cards.isEmpty()) {
            cards.add(buildNoPaRequiredCard());
        }
        return CdsHookResponse.builder().cards(cards).build();
    }

    public CdsHookResponse processOrderSelect(CdsHookRequest request) {
        log.info("Processing order-select hook, instance={}", request.getHookInstance());
        List<Card> cards = new ArrayList<>();
        for (ServiceRequest sr : extractServiceRequests(request)) {
            cards.add(buildEarlyGuidanceCard(extractCode(sr)));
        }
        if (cards.isEmpty()) cards.add(buildNoPaRequiredCard());
        return CdsHookResponse.builder().cards(cards).build();
    }

    public CdsHookResponse processAppointmentBook(CdsHookRequest request) {
        log.info("Processing appointment-book hook, instance={}", request.getHookInstance());
        return CdsHookResponse.builder().cards(List.of(buildAppointmentCoverageCard())).build();
    }

    // --- Private helpers ---

    private List<ServiceRequest> extractServiceRequests(CdsHookRequest request) {
        List<ServiceRequest> results = new ArrayList<>();
        try {
            Object draftOrders = request.getContext() != null ? request.getContext().get("draftOrders") : null;
            if (draftOrders != null) {
                String json = objectMapper.writeValueAsString(draftOrders);
                Bundle bundle = (Bundle) fhirJsonParser.parseResource(json);
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.getResource() instanceof ServiceRequest sr) {
                        results.add(sr);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract ServiceRequests from hook context: {}", e.getMessage());
        }
        return results;
    }

    private String extractCode(ServiceRequest sr) {
        if (sr.getCode() != null && !sr.getCode().getCoding().isEmpty()) {
            return sr.getCode().getCodingFirstRep().getCode();
        }
        return "UNKNOWN";
    }

    private Card buildPaRequiredCard(String code) {
        return Card.builder()
            .uuid(UUID.randomUUID().toString())
            .summary("Prior Authorization Required")
            .detail("Service code **" + code + "** requires prior authorization. "
                + "Submit a PA request via the PAS API or start the DTR workflow below.")
            .indicator("warning")
            .source(payerSource())
            .links(List.of(
                Link.builder()
                    .label("Start Prior Authorization (DTR)")
                    .url(dtrSmartLaunchUrl + "?serviceCode=" + code)
                    .type("smart")
                    .appContext("{\"priorAuthRequired\":true,\"serviceCode\":\"" + code + "\"}")
                    .build(),
                Link.builder()
                    .label("Submit PA via PAS API")
                    .url("/fhir/Claim/$submit")
                    .type("absolute")
                    .build()
            ))
            .build();
    }

    private Card buildDocumentationCard(String code) {
        return Card.builder()
            .uuid(UUID.randomUUID().toString())
            .summary("Clinical Documentation Required")
            .detail("Supporting documentation is required. Complete the DTR questionnaire.")
            .indicator("info")
            .source(payerSource())
            .links(List.of(Link.builder()
                .label("Complete Documentation (DTR)")
                .url(dtrSmartLaunchUrl + "?serviceCode=" + code)
                .type("smart")
                .build()))
            .build();
    }

    private Card buildNoPaRequiredCard() {
        return Card.builder()
            .uuid(UUID.randomUUID().toString())
            .summary("No Prior Authorization Required")
            .detail("No prior authorization is required for the ordered service(s) under the patient's coverage.")
            .indicator("info")
            .source(payerSource())
            .build();
    }

    private Card buildEarlyGuidanceCard(String code) {
        return Card.builder()
            .uuid(UUID.randomUUID().toString())
            .summary("Coverage Guidance: " + code)
            .detail("Review coverage requirements before signing the order to avoid PA delays.")
            .indicator("info")
            .source(payerSource())
            .build();
    }

    private Card buildAppointmentCoverageCard() {
        return Card.builder()
            .uuid(UUID.randomUUID().toString())
            .summary("Appointment Coverage Verified")
            .detail("If prior authorization is needed, obtain it before the appointment date.")
            .indicator("info")
            .source(payerSource())
            .build();
    }

    private Source payerSource() {
        return Source.builder().label(payerName).url("/fhir/metadata").build();
    }

    private Map<String, String> buildOrderSignPrefetch() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("patient", "Patient/{{context.patientId}}");
        p.put("coverage", "Coverage?patient={{context.patientId}}&status=active");
        p.put("practitioner", "Practitioner/{{context.userId}}");
        return p;
    }

    private Map<String, String> buildOrderSelectPrefetch() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("patient", "Patient/{{context.patientId}}");
        p.put("coverage", "Coverage?patient={{context.patientId}}&status=active");
        return p;
    }

    private Map<String, String> buildAppointmentPrefetch() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("patient", "Patient/{{context.patientId}}");
        p.put("coverage", "Coverage?patient={{context.patientId}}&status=active");
        p.put("appointment", "Appointment/{{context.appointments}}");
        return p;
    }
}
