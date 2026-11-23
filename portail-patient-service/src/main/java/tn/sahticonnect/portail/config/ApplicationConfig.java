package tn.sahticonnect.portail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Random;

/** Beans transverses : horloge, générateur aléatoire, publication du contrat OpenAPI. */
@Configuration
public class ApplicationConfig implements WebMvcConfigurer {

    /**
     * Horloge injectée partout où l'on a besoin de « maintenant » : les tests la remplacent
     * par une horloge fixe. Les dates sont stockées en heure locale de ce fuseau.
     */
    @Bean
    public Clock horloge(@Value("${portail.fuseau-horaire:Africa/Tunis}") String fuseau) {
        return Clock.system(ZoneId.of(fuseau));
    }

    /** Délai estimé des ambulances (java.util.Random est thread-safe). */
    @Bean
    public Random aleatoire() {
        return new Random();
    }

    /** Sert le contrat src/main/resources/openapi/*.yaml, affiché par Swagger UI. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**").addResourceLocations("classpath:/openapi/");
    }
}
