package tn.sahticonnect.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Adresses des autres services (préfixe « sahti »).
 *
 * @param timeout délai maximal d'un appel distant ; au-delà, le champ vaut null (SERVICE_INDISPONIBLE)
 */
@ConfigurationProperties(prefix = "sahti")
public record SahtiProperties(
        @DefaultValue("Africa/Tunis") String fuseauHoraire,
        @DefaultValue("3s") Duration timeout,
        Telemetrie telemetrie,
        Cnam cnam) {

    public record Telemetrie(@DefaultValue("localhost:9090") String adresse) {
    }

    public record Cnam(@DefaultValue("http://localhost:8081/ws") String url) {
    }
}
