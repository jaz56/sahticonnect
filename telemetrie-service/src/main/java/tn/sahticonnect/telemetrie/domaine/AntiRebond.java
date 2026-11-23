package tn.sahticonnect.telemetrie.domaine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tn.sahticonnect.telemetrie.config.TelemetrieProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-rebond des alertes. Un capteur envoie une mesure par seconde : sans filtre, une FC à 140
 * pendant une minute produirait 60 alertes identiques et 60 interventions.
 * <ul>
 *   <li>une alerte au plus par (patient, type de mesure, niveau) sur la fenêtre (60 s par défaut) ;</li>
 *   <li>une aggravation (ÉLEVÉE puis CRITIQUE) est émise immédiatement : aucune alerte CRITIQUE récente ;</li>
 *   <li>tant qu'une alerte d'un niveau supérieur est récente, un niveau inférieur n'est pas réémis
 *       (CRITIQUE puis ÉLEVÉE = amélioration, pas une nouvelle alerte) ;</li>
 *   <li>la fenêtre part de la dernière alerte émise : passé ce délai, une nouvelle alerte est émise.</li>
 * </ul>
 * Thread-safe : plusieurs capteurs envoient en parallèle.
 */
@Component
public class AntiRebond {

    private record Cle(String patient, Constante constante) {
    }

    private final Clock horloge;
    private final Duration fenetre;
    /** Pour chaque (patient, constante) : instant de la dernière alerte émise, par niveau. */
    private final Map<Cle, Map<Niveau, Instant>> dernieresAlertes = new ConcurrentHashMap<>();

    @Autowired
    public AntiRebond(Clock horloge, TelemetrieProperties proprietes) {
        this(horloge, proprietes.antiRebond());
    }

    public AntiRebond(Clock horloge, Duration fenetre) {
        this.horloge = horloge;
        this.fenetre = fenetre;
    }

    /**
     * Faut-il émettre une alerte pour cette mesure ? Si oui, l'émission est enregistrée
     * (la décision et l'enregistrement sont atomiques pour un même patient et une même constante).
     */
    public boolean autoriser(String patient, Constante constante, Niveau niveau) {
        if (niveau == Niveau.NORMAL) {
            return false;
        }
        Instant maintenant = horloge.instant();
        boolean[] emise = {false};
        dernieresAlertes.compute(new Cle(patient, constante), (cle, parNiveau) -> {
            Map<Niveau, Instant> historique = parNiveau != null ? parNiveau : new EnumMap<>(Niveau.class);
            boolean recente = historique.entrySet().stream()
                    .anyMatch(e -> e.getKey().estAuMoins(niveau) && estDansLaFenetre(e.getValue(), maintenant));
            if (!recente) {
                historique.put(niveau, maintenant);
                emise[0] = true;
            }
            return historique;
        });
        return emise[0];
    }

    public Duration fenetre() {
        return fenetre;
    }

    /** Oublie toutes les alertes émises (tests, redémarrage logique). */
    public void reinitialiser() {
        dernieresAlertes.clear();
    }

    private boolean estDansLaFenetre(Instant emission, Instant maintenant) {
        return Duration.between(emission, maintenant).compareTo(fenetre) < 0;
    }
}
