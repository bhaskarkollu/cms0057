package com.cms0057.priorauth.common;

import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FhirValidationService {

    private static final Logger log = LoggerFactory.getLogger(FhirValidationService.class);

    private final FhirValidator fhirValidator;

    public FhirValidationService(FhirValidator fhirValidator) {
        this.fhirValidator = fhirValidator;
    }

    public ValidationResult validatePasClaim(Claim claim) {
        ValidationResult result = fhirValidator.validateWithResult(claim);
        if (!result.isSuccessful()) {
            log.warn("PAS Claim validation issues for claimId={}: {}", claim.getId(), result.getMessages());
        }
        return result;
    }

    public OperationOutcome toOperationOutcome(ValidationResult result) {
        OperationOutcome outcome = new OperationOutcome();
        result.getMessages().forEach(msg -> outcome.addIssue()
            .setSeverity(OperationOutcome.IssueSeverity.fromCode(msg.getSeverity().getCode()))
            .setCode(OperationOutcome.IssueType.INVALID)
            .setDiagnostics(msg.getMessage()));
        return outcome;
    }
}
