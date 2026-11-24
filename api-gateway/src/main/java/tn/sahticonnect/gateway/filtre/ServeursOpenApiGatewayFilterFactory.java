package tn.sahticonnect.gateway.filtre;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtre {@code ServeursOpenApi=/api/portail} : réécrit la section {@code servers} d'un contrat OpenAPI relayé.
 * <p>
 * Les contrats de docs/ annoncent l'adresse directe du service (http://localhost:8082). Lu tel quel dans le Swagger UI
 * de la gateway, « Try it out » appellerait le service directement (autre origine, gateway contournée). Le fichier
 * relayé annonce donc l'adresse relative {@code /api/portail}, résolue par Swagger UI sur l'hôte de la gateway.
 * Le contrat lui-même n'est pas modifié : seule la réponse relayée l'est.
 */
@Component
public class ServeursOpenApiGatewayFilterFactory extends AbstractGatewayFilterFactory<ServeursOpenApiGatewayFilterFactory.Config> {

    /** Bloc « servers: » de premier niveau, jusqu'à la clé de premier niveau suivante. */
    private static final Pattern SERVEURS = Pattern.compile("(?m)^servers:[ \\t]*\\R(?:(?:[ \\t-].*)?\\R)*");

    private final ModifyResponseBodyGatewayFilterFactory modification;

    public ServeursOpenApiGatewayFilterFactory(ModifyResponseBodyGatewayFilterFactory modification) {
        super(Config.class);
        this.modification = modification;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("prefixe");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return modification.apply(c -> c.setRewriteFunction(String.class, String.class,
                (exchange, contrat) -> Mono.justOrEmpty(reecrire(contrat, config.getPrefixe()))));
    }

    static String reecrire(String contrat, String prefixe) {
        if (contrat == null) {
            return null;
        }
        String serveurs = "servers:\n  - url: " + prefixe + "\n    description: Via l'API Gateway\n";
        Matcher m = SERVEURS.matcher(contrat);
        return m.find() ? m.replaceFirst(Matcher.quoteReplacement(serveurs)) : contrat;
    }

    public static class Config {
        private String prefixe;

        public String getPrefixe() {
            return prefixe;
        }

        public void setPrefixe(String prefixe) {
            this.prefixe = prefixe;
        }
    }
}
