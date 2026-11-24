package tn.sahticonnect.orchestrateur.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig implements WebMvcConfigurer {

    @Bean
    public Clock horloge(OrchestrateurProperties proprietes) {
        return Clock.system(ZoneId.of(proprietes.fuseauHoraire()));
    }

    /** Exécution des workflows : un thread virtuel par workflow (appels bloquants vers 4 services). */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService executeurWorkflows() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Sert le contrat src/main/resources/openapi/*.yaml, affiché par Swagger UI. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**").addResourceLocations("classpath:/openapi/");
    }
}
