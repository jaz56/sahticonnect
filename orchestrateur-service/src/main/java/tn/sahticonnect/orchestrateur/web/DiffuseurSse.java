package tn.sahticonnect.orchestrateur.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.sahticonnect.orchestrateur.api.model.WorkflowDto;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.service.PublicationWorkflows;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Flux SSE : chaque changement d'état d'un workflow est envoyé à tous les clients connectés (événement
 * « workflow », donnée = Workflow complet en JSON). Un client déconnecté est retiré.
 */
@Component
@RequiredArgsConstructor
public class DiffuseurSse implements PublicationWorkflows {

    private final List<SseEmitter> abonnes = new CopyOnWriteArrayList<>();
    private final WorkflowMapper mapper;

    public SseEmitter abonner() {
        SseEmitter emetteur = new SseEmitter(0L); // pas de délai : la connexion reste ouverte
        abonnes.add(emetteur);
        emetteur.onCompletion(() -> abonnes.remove(emetteur));
        emetteur.onTimeout(() -> abonnes.remove(emetteur));
        emetteur.onError(erreur -> abonnes.remove(emetteur));
        try {
            emetteur.send(SseEmitter.event().comment("connecté au flux des workflows"));
        } catch (IOException e) {
            abonnes.remove(emetteur);
        }
        return emetteur;
    }

    @Override
    public void publier(Workflow workflow) {
        if (abonnes.isEmpty()) {
            return;
        }
        WorkflowDto dto = mapper.versDto(workflow);
        for (SseEmitter emetteur : abonnes) {
            try {
                synchronized (emetteur) { // plusieurs workflows peuvent publier en même temps
                    emetteur.send(SseEmitter.event().name("workflow").id(dto.getId()).data(dto, MediaType.APPLICATION_JSON));
                }
            } catch (IOException | IllegalStateException deconnecte) {
                abonnes.remove(emetteur);
            }
        }
    }

    public int nombreAbonnes() {
        return abonnes.size();
    }
}
