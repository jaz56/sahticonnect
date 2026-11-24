package tn.sahticonnect.orchestrateur.service;

/** Relance d'un workflow qui n'est pas en ECHEC, ou déjà en cours → 409 WORKFLOW_NON_RELANCABLE. */
public class WorkflowNonRelancableException extends RuntimeException {

    public WorkflowNonRelancableException(String message) {
        super(message);
    }
}
