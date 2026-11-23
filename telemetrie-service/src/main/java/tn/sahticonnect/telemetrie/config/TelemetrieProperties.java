package tn.sahticonnect.telemetrie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Set;

/**
 * Configuration métier (préfixe « telemetrie »).
 *
 * @param fuseauHoraire     fuseau de l'horloge (année des identifiants d'alerte)
 * @param patients          identifiants des patients suivis
 * @param historiqueParType nombre de mesures gardées en mémoire par patient et par type
 * @param antiRebond        fenêtre pendant laquelle une même alerte n'est pas répétée
 */
@ConfigurationProperties(prefix = "telemetrie")
public record TelemetrieProperties(
        @DefaultValue("Africa/Tunis") String fuseauHoraire,
        Set<String> patients,
        @DefaultValue("100") int historiqueParType,
        @DefaultValue("60s") Duration antiRebond) {

    public TelemetrieProperties {
        patients = patients == null ? Set.of() : Set.copyOf(patients);
    }
}
