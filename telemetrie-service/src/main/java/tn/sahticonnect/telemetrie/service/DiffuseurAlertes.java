package tn.sahticonnect.telemetrie.service;

import io.grpc.stub.ServerCallStreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.sahticonnect.telemetrie.Alerte;
import tn.sahticonnect.telemetrie.domaine.Niveau;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diffusion en mémoire des alertes à tous les abonnés de SouscrireAlertes (orchestrateur, dashboard…).
 * Chaque abonné a un filtre (patients, niveau minimum). Un abonné déconnecté est retiré :
 * par le handler d'annulation de gRPC, ou au premier envoi qui échoue.
 */
@Component
@Slf4j
public class DiffuseurAlertes {

    /** Abonné : patients suivis (vide = tous) et niveau minimum. */
    public record Abonnement(Set<String> patients, Niveau niveauMinimum, ServerCallStreamObserver<Alerte> flux) {

        boolean accepte(String patient, Niveau niveau) {
            return (patients.isEmpty() || patients.contains(patient)) && niveau.estAuMoins(niveauMinimum);
        }
    }

    private final Set<Abonnement> abonnements = ConcurrentHashMap.newKeySet();

    public void abonner(Abonnement abonnement) {
        abonnements.add(abonnement);
        // nettoyage quand le client annule ou se déconnecte
        abonnement.flux().setOnCancelHandler(() -> {
            abonnements.remove(abonnement);
            log.info("Abonné aux alertes déconnecté ({} restant(s))", abonnements.size());
        });
        log.info("Nouvel abonné aux alertes : patients={}, niveau ≥ {} ({} abonné(s))",
                abonnement.patients().isEmpty() ? "tous" : abonnement.patients(),
                abonnement.niveauMinimum(), abonnements.size());
    }

    /** Envoie l'alerte à chaque abonné dont le filtre l'accepte. */
    public void publier(Alerte alerte, Niveau niveau) {
        for (Abonnement abonnement : abonnements) {
            if (!abonnement.accepte(alerte.getIdentifiantPatient(), niveau)) {
                continue;
            }
            ServerCallStreamObserver<Alerte> flux = abonnement.flux();
            // un StreamObserver n'est pas thread-safe : plusieurs capteurs peuvent publier en même temps
            synchronized (flux) {
                if (flux.isCancelled()) {
                    abonnements.remove(abonnement);
                    continue;
                }
                try {
                    flux.onNext(alerte);
                } catch (RuntimeException e) {
                    log.warn("Envoi impossible à un abonné, retiré : {}", e.getMessage());
                    abonnements.remove(abonnement);
                }
            }
        }
    }

    public int nombreAbonnes() {
        return abonnements.size();
    }
}
