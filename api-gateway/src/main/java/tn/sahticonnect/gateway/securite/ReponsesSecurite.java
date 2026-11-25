package tn.sahticonnect.gateway.securite;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.sahticonnect.gateway.web.ReponsesErreur;

import java.nio.charset.StandardCharsets;

/**
 * Réponses 401 et 403 de la gateway, au format des contrats :
 * <ul>
 *   <li>REST, GraphQL, SSE : schéma Erreur, codes NON_AUTHENTIFIE / ACCES_REFUSE ;</li>
 *   <li>route SOAP (/api/cnam/ws) : SOAP Fault « Client » (la requête est refusée, pas le service en panne),
 *       avec le même code dans le &lt;detail&gt; ; le statut HTTP reste 401 / 403.</li>
 * </ul>
 */
@Component
public class ReponsesSecurite implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper json;

    public ReponsesSecurite(ObjectMapper json) {
        this.json = json;
    }

    /** 401 : jeton absent, invalide, expiré, d'un autre émetteur ou d'une autre audience. */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException e) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"sahticonnect\"");
        String message = exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)
                ? "Jeton invalide ou expiré : reconnectez-vous"
                : "Authentification requise : jeton OIDC (Keycloak) manquant";
        return ecrire(exchange, HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE", message);
    }

    /** 403 : jeton valide, mais le rôle ne donne pas accès à cette route. */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException e) {
        return ecrire(exchange, HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Accès refusé : votre rôle ne permet pas " + exchange.getRequest().getMethod() + " "
                        + exchange.getRequest().getPath().value());
    }

    /** 403 utilisé hors du filtre d'autorisation (ex. jeton PATIENT sans claim patient_id). */
    public Mono<Void> refuser(ServerWebExchange exchange, String message) {
        return ecrire(exchange, HttpStatus.FORBIDDEN, "ACCES_REFUSE", message);
    }

    private Mono<Void> ecrire(ServerWebExchange exchange, HttpStatus statut, String code, String message) {
        ServerHttpResponse reponse = exchange.getResponse();
        String chemin = exchange.getRequest().getPath().value();
        byte[] corps;
        if (chemin.startsWith("/api/cnam/ws")) {
            reponse.getHeaders().setContentType(MediaType.parseMediaType("text/xml;charset=UTF-8"));
            corps = ReponsesErreur.faultSoap("Client", message, code, "api-gateway").getBytes(StandardCharsets.UTF_8);
        } else {
            reponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                corps = json.writeValueAsBytes(ReponsesErreur.erreur(statut.value(), code, message, chemin, null));
            } catch (Exception impossible) {
                corps = ("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
            }
        }
        reponse.setStatusCode(statut);
        DataBuffer tampon = reponse.bufferFactory().wrap(corps);
        return reponse.writeWith(Mono.just(tampon));
    }
}
