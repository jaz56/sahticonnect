package tn.sahticonnect.orchestrateur.service;

import tn.sahticonnect.orchestrateur.entity.Workflow;

/** Diffusion de chaque changement d'état d'un workflow (implémentée par le flux SSE). */
public interface PublicationWorkflows {

    void publier(Workflow workflow);
}
