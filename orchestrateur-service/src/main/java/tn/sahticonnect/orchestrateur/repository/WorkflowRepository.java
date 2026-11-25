package tn.sahticonnect.orchestrateur.repository;

import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.sahticonnect.orchestrateur.entity.Workflow;

import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow, String>, JpaSpecificationExecutor<Workflow> {

    /** Idempotence : un seul workflow par alerte. */
    Optional<Workflow> findByAlerteId(String alerteId);

    /** Saga : workflow ELEVEE déjà compensé (ou en cours de compensation) par ce workflow CRITIQUE. */
    Optional<Workflow> findFirstByCompensePar(String workflowCritiqueId);

    /** Saga : workflows d'un patient et d'un niveau créés depuis une date (fenêtre d'escalade), plus récents d'abord. */
    List<Workflow> findByPatientIdAndNiveauAndDateCreationAfterOrderByDateCreationDesc(
            String patientId, NiveauAlerte niveau, OffsetDateTime depuis);
}
