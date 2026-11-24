package tn.sahticonnect.orchestrateur.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.sahticonnect.orchestrateur.entity.Workflow;

import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow, String>, JpaSpecificationExecutor<Workflow> {

    /** Idempotence : un seul workflow par alerte. */
    Optional<Workflow> findByAlerteId(String alerteId);
}
