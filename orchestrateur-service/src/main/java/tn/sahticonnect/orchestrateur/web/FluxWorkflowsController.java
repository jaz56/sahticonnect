package tn.sahticonnect.orchestrateur.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GET /workflows/flux (Server-Sent Events). Écrit à la main : une réponse en flux (SseEmitter) ne correspond pas aux
 * interfaces générées. L'opération est décrite dans le contrat mais exclue de la génération (FILTER dans le pom).
 */
@RestController
@RequiredArgsConstructor
public class FluxWorkflowsController {

    private final DiffuseurSse diffuseur;

    @GetMapping(path = "/workflows/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suivreWorkflows() {
        return diffuseur.abonner();
    }
}
