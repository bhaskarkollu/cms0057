package com.cms0057.priorauth.pas;

import ca.uhn.fhir.parser.IParser;
import com.cms0057.priorauth.pas.model.PriorAuthRecord;
import com.cms0057.priorauth.pas.model.PriorAuthRepository;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Prior Authorization Support (PAS) service implementing Da Vinci PAS IG.
 *
 * Handles the full PA lifecycle:
 *   $submit  — receive Claim Bundle, return ClaimResponse (pended or immediate decision)
 *   $inquire — poll status of an existing PA request
 *   SLA monitor — enforces CMS-0057-F 72h/7-day response deadlines
 *
 * Profiles:
 *   Claim:         http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claim
 *   ClaimResponse: http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse
 */
@Service
public class PasService {

    private static final Logger log = LoggerFactory.getLogger(PasService.class);

    private static final Set<String> AUTO_APPROVE_CODES =
        Set.of("99213", "99214", "99215", "G0101", "G0102");

    private final PriorAuthRepository repository;
    private final ClaimResponseBuilder responseBuilder;

    @Value("${cms0057.pas.urgent-response-hours}")
    private int urgentResponseHours;

    @Value("${cms0057.pas.standard-response-days}")
    private int standardResponseDays;

    public PasService(PriorAuthRepository repository, ClaimResponseBuilder responseBuilder,
                      IParser fhirJsonParser) {
        this.repository = repository;
        this.responseBuilder = responseBuilder;
    }

    /**
     * Processes a PA $submit operation.
     *
     * Provider submits a Bundle containing a PAS-profiled Claim plus supporting resources
     * (Coverage, Patient, Practitioner, ServiceRequest, DocumentReference).
     * Returns a Bundle with ClaimResponse: immediate approval or pended with SLA deadline.
     */
    @Transactional
    public Bundle submitPriorAuth(Bundle requestBundle) {
        Claim claim = extractClaim(requestBundle);
        log.info("PAS $submit received, claimId={}", claim.getId());

        PriorAuthRecord record = buildRecord(claim);
        record = repository.save(record);

        ClaimResponse claimResponse = determineInitialDecision(claim, record);

        log.info("PAS $submit complete, trackingId={}, status={}, slaDeadline={}",
            record.getId(), record.getStatus(), record.getSlaDeadline());

        return buildResponseBundle(claimResponse, record);
    }

    /**
     * Processes a PA $inquire — provider polls for decision update.
     */
    @Transactional(readOnly = true)
    public Bundle inquirePriorAuth(String claimId) {
        log.info("PAS $inquire for claimId={}", claimId);
        PriorAuthRecord record = repository.findByClaimId(claimId)
            .orElseThrow(() -> new IllegalArgumentException("No PA request found for claimId=" + claimId));

        Claim syntheticClaim = syntheticClaim(record);
        ClaimResponse response = switch (record.getStatus()) {
            case "approved" -> responseBuilder.buildApprovedResponse(syntheticClaim, record);
            case "denied"   -> responseBuilder.buildDeniedResponse(syntheticClaim, record, record.getNotes());
            case "partial"  -> responseBuilder.buildPartialResponse(syntheticClaim, record);
            default         -> responseBuilder.buildPendedResponse(syntheticClaim, record);
        };
        return buildResponseBundle(response, record);
    }

    @Transactional(readOnly = true)
    public Bundle getPatientPriorAuths(String patientId) {
        List<PriorAuthRecord> records = repository.findByPatientId(patientId);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(records.size());
        for (PriorAuthRecord r : records) {
            bundle.addEntry().setResource(syntheticClaim(r));
        }
        return bundle;
    }

    /** Runs every 15 minutes to alert on SLA breaches per CMS-0057-F. */
    @Scheduled(fixedRateString = "${cms0057.pas.sla-check-interval-ms:900000}")
    public void monitorSlaCompliance() {
        Instant alertThreshold = Instant.now().plus(2, ChronoUnit.HOURS);
        List<PriorAuthRecord> atRisk = repository.findPendingBreachingDeadline(alertThreshold);
        if (!atRisk.isEmpty()) {
            log.warn("CMS-0057-F SLA ALERT: {} PA requests approaching deadline within 2 hours", atRisk.size());
            for (PriorAuthRecord r : atRisk) {
                log.warn("  PA id={}, patient={}, priority={}, deadline={}",
                    r.getId(), r.getPatientId(), r.getPriority(), r.getSlaDeadline());
            }
        }
    }

    // --- Internal helpers ---

    private Claim extractClaim(Bundle bundle) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(r -> r instanceof Claim)
            .map(r -> (Claim) r)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Bundle must contain a Claim resource"));
    }

    private PriorAuthRecord buildRecord(Claim claim) {
        PriorAuthRecord record = new PriorAuthRecord();
        String localId = claim.getIdElement().getIdPart();
        record.setClaimId(localId != null && !localId.isBlank() ? localId : UUID.randomUUID().toString());
        log.debug("buildRecord: claim.getId()={}, idPart={}, storedClaimId={}", claim.getId(), localId, record.getClaimId());

        if (claim.getPatient() != null) {
            record.setPatientId(claim.getPatient().getReferenceElement().getIdPart());
        }
        if (claim.getProvider() != null) {
            record.setPractitionerId(claim.getProvider().getReferenceElement().getIdPart());
        }
        if (!claim.getInsurance().isEmpty()) {
            Claim.InsuranceComponent ins = claim.getInsuranceFirstRep();
            if (ins.getCoverage() != null) {
                record.setCoverageId(ins.getCoverage().getReferenceElement().getIdPart());
            }
        }
        if (!claim.getItem().isEmpty()) {
            Claim.ItemComponent item = claim.getItemFirstRep();
            if (item.getProductOrService() != null && !item.getProductOrService().getCoding().isEmpty()) {
                Coding coding = item.getProductOrService().getCodingFirstRep();
                record.setServiceCode(coding.getCode());
                record.setServiceDescription(coding.getDisplay());
            }
        }

        boolean isUrgent = claim.getPriority() != null &&
            "stat".equalsIgnoreCase(claim.getPriority().getCodingFirstRep().getCode());
        record.setPriority(isUrgent ? "urgent" : "standard");

        Instant now = Instant.now();
        record.setSubmittedAt(now);
        record.setSlaDeadline(isUrgent
            ? now.plus(urgentResponseHours, ChronoUnit.HOURS)
            : now.plus(standardResponseDays, ChronoUnit.DAYS));
        record.setStatus("queued");
        return record;
    }

    private ClaimResponse determineInitialDecision(Claim claim, PriorAuthRecord record) {
        if (AUTO_APPROVE_CODES.contains(record.getServiceCode())) {
            record.setStatus("approved");
            record.setDecidedAt(Instant.now());
            record.setPriorAuthNumber("PA-" + System.currentTimeMillis());
            record.setSlaCompliant(true);
            repository.save(record);
            return responseBuilder.buildApprovedResponse(claim, record);
        }
        record.setStatus("pended");
        repository.save(record);
        return responseBuilder.buildPendedResponse(claim, record);
    }

    private Bundle buildResponseBundle(ClaimResponse claimResponse, PriorAuthRecord record) {
        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.getMeta().setLastUpdated(new Date());
        bundle.getMeta().addProfile(
            "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-response-bundle");
        bundle.addEntry()
            .setFullUrl("ClaimResponse/" + claimResponse.getId())
            .setResource(claimResponse);
        bundle.addEntry()
            .setFullUrl("Task/" + UUID.randomUUID())
            .setResource(buildWorkflowTask(record));
        return bundle;
    }

    private Task buildWorkflowTask(PriorAuthRecord record) {
        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setStatus(mapStatus(record.getStatus()));
        task.setIntent(Task.TaskIntent.ORDER);
        task.setPriority("urgent".equals(record.getPriority()) ? Task.TaskPriority.URGENT : Task.TaskPriority.ROUTINE);
        task.getCode().addCoding()
            .setSystem("http://hl7.org/fhir/us/davinci-pas/CodeSystem/PASTasks")
            .setCode("complete-pa").setDisplay("Complete Prior Authorization");
        task.setDescription("CMS-0057-F PA - " + record.getStatus().toUpperCase()
            + " | SLA Deadline: " + record.getSlaDeadline());
        task.addInput()
            .setType(new CodeableConcept().addCoding(new Coding().setCode("prior-auth-claim-id")))
            .setValue(new StringType(record.getClaimId()));
        return task;
    }

    private Task.TaskStatus mapStatus(String status) {
        return switch (status) {
            case "queued"   -> Task.TaskStatus.RECEIVED;
            case "pended"   -> Task.TaskStatus.INPROGRESS;
            case "approved" -> Task.TaskStatus.COMPLETED;
            case "denied"   -> Task.TaskStatus.FAILED;
            default         -> Task.TaskStatus.RECEIVED;
        };
    }

    private Claim syntheticClaim(PriorAuthRecord record) {
        Claim claim = new Claim();
        claim.setId(record.getClaimId());
        claim.setPatient(new Reference("Patient/" + record.getPatientId()));
        return claim;
    }
}
