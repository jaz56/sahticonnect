package tn.sahticonnect.dashboard.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tn.sahticonnect.dashboard.entity.Alerte;
import tn.sahticonnect.dashboard.entity.Intervention;

/**
 * Diffusion en mémoire des nouvelles alertes et interventions vers les subscriptions GraphQL.
 * Sinks multicast : chaque abonné connecté reçoit les événements émis après son abonnement ;
 * un abonné lent ne bloque pas les autres (directBestEffort).
 */
@Component
public class PublicateurEvenements {

    /** Intervention publiée avec le service du patient (filtre « service » de nouvelleIntervention). */
    public record InterventionPubliee(Intervention intervention, String service) {
    }

    private final Sinks.Many<Alerte> alertes = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<InterventionPubliee> interventions = Sinks.many().multicast().directBestEffort();

    // un Sink n'accepte pas deux émissions simultanées : les émissions sont sérialisées
    public synchronized void publier(Alerte alerte) {
        alertes.tryEmitNext(alerte);
    }

    public synchronized void publier(Intervention intervention, String service) {
        interventions.tryEmitNext(new InterventionPubliee(intervention, service));
    }

    public Flux<Alerte> alertes() {
        return alertes.asFlux();
    }

    public Flux<InterventionPubliee> interventions() {
        return interventions.asFlux();
    }
}
