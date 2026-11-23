package tn.sahticonnect.telemetrie.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ApplicationConfig {

    /** Horloge injectée (horodatage des mesures sans date, anti-rebond) : remplacée par une horloge fixe en test. */
    @Bean
    public Clock horloge(TelemetrieProperties proprietes) {
        return Clock.system(ZoneId.of(proprietes.fuseauHoraire()));
    }
}
