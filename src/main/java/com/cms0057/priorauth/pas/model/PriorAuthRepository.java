package com.cms0057.priorauth.pas.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriorAuthRepository extends JpaRepository<PriorAuthRecord, String> {

    Optional<PriorAuthRecord> findByClaimId(String claimId);

    List<PriorAuthRecord> findByPatientId(String patientId);

    List<PriorAuthRecord> findByStatus(String status);

    /** Find PA requests approaching SLA deadline for alerting */
    @Query("SELECT r FROM PriorAuthRecord r WHERE r.status IN ('queued','pended') AND r.slaDeadline <= :threshold")
    List<PriorAuthRecord> findPendingBreachingDeadline(Instant threshold);
}
