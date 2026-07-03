package com.cms0057.priorauth.common;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final IParser fhirJsonParser;

    public GlobalExceptionHandler(IParser fhirJsonParser) {
        this.fhirJsonParser = fhirJsonParser;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.valueOf("application/fhir+json"))
            .body(fhirJsonParser.encodeResourceToString(outcome(ex.getMessage(),
                OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.valueOf("application/fhir+json"))
            .body(fhirJsonParser.encodeResourceToString(outcome("An unexpected error occurred",
                OperationOutcome.IssueSeverity.FATAL, OperationOutcome.IssueType.EXCEPTION)));
    }

    private OperationOutcome outcome(String msg, OperationOutcome.IssueSeverity severity,
                                      OperationOutcome.IssueType type) {
        OperationOutcome o = new OperationOutcome();
        o.addIssue().setSeverity(severity).setCode(type).setDiagnostics(msg);
        return o;
    }
}
