package tn.sahticonnect.orchestrateur.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Adresses des 4 services et paramètres du workflow (préfixe « sahti »).
 *
 * @param timeout délai de connexion et de lecture de chaque appel HTTP (SOAP, REST, GraphQL)
 */
@ConfigurationProperties(prefix = "sahti")
public record OrchestrateurProperties(
        @DefaultValue("Africa/Tunis") String fuseauHoraire,
        @DefaultValue("3s") Duration timeout,
        @DefaultValue Telemetrie telemetrie,
        @DefaultValue Service cnam,
        @DefaultValue Service portail,
        @DefaultValue Service dashboard,
        @DefaultValue Alertes alertes,
        @DefaultValue Facturation facturation) {

    public record Telemetrie(@DefaultValue("localhost:9090") String adresse) {
    }

    public record Service(String url) {
    }

    /** Abonnement gRPC aux alertes et backoff de reconnexion (1 s, 2 s, 4 s… plafonné). */
    public record Alertes(@DefaultValue("true") boolean abonnementActif,
                          @DefaultValue("1s") Duration reconnexionMin,
                          @DefaultValue("30s") Duration reconnexionMax) {
    }

    /** Champs fixes de la feuille de soins émise à la fin du workflow. */
    public record Facturation(@DefaultValue("SahtiConnect – intervention à domicile") String etablissement,
                              @DefaultValue("Équipe d'intervention SahtiConnect") String medecin) {
    }
}
