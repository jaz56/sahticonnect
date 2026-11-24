package tn.sahticonnect.orchestrateur.service;

/** Workflow inconnu → 404 WORKFLOW_INTROUVABLE. */
public class WorkflowIntrouvableException extends RuntimeException {

    public WorkflowIntrouvableException(String id) {
        super("Aucun workflow " + id);
    }
}
