package tn.sahticonnect.orchestrateur.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tn.sahticonnect.orchestrateur.api.WorkflowsApi;
import tn.sahticonnect.orchestrateur.api.model.DeclenchementAlerteDto;
import tn.sahticonnect.orchestrateur.api.model.EtatWorkflowDto;
import tn.sahticonnect.orchestrateur.api.model.WorkflowDto;
import tn.sahticonnect.orchestrateur.service.OrchestrateurAlertes;

import java.net.URI;
import java.util.List;

/**
 * Suivi, déclenchement et relance des workflows. Implémente l'interface générée depuis
 * docs/openapi/orchestrateur.yaml ; le flux SSE (/workflows/flux) est dans FluxWorkflowsController.
 */
@RestController
@RequiredArgsConstructor
public class WorkflowController implements WorkflowsApi {

    private final OrchestrateurAlertes orchestrateur;
    private final WorkflowMapper mapper;

    @Override
    public ResponseEntity<List<WorkflowDto>> listerWorkflows(String patientId, EtatWorkflowDto etat) {
        return ResponseEntity.ok(orchestrateur.lister(patientId, mapper.versEtat(etat)).stream().map(mapper::versDto).toList());
    }

    @Override
    public ResponseEntity<WorkflowDto> consulterWorkflow(String id) {
        return ResponseEntity.ok(mapper.versDto(orchestrateur.consulter(id)));
    }

    /** 202 : workflow créé et lancé ; 200 : un workflow existe déjà pour cette alerte (idempotence). */
    @Override
    public ResponseEntity<WorkflowDto> declencherWorkflow(DeclenchementAlerteDto declenchementAlerteDto) {
        OrchestrateurAlertes.Reception reception = orchestrateur.recevoir(mapper.versAlerte(declenchementAlerteDto));
        WorkflowDto dto = mapper.versDto(reception.workflow());
        if (!reception.cree()) {
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("/workflows/" + dto.getId())).body(dto);
    }

    @Override
    public ResponseEntity<WorkflowDto> relancerWorkflow(String id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.versDto(orchestrateur.relancer(id)));
    }
}
