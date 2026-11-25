package tn.sahticonnect.gateway.securite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Propagation de l'identité vers les services internes (qui ne voient jamais le jeton) :
 * <ol>
 *   <li>TOUJOURS : suppression des en-têtes {@value #PATIENT} et {@value #ROLES} envoyés par le client (sinon un
 *       client pourrait se faire passer pour un autre patient) et du paramètre access_token de l'URL ;</li>
 *   <li>utilisateur authentifié : {@value #ROLES} = ses rôles ; PATIENT (sans rôle MEDECIN ni ADMIN) :
 *       {@value #PATIENT} = claim patient_id du jeton. Un jeton PATIENT sans patient_id est refusé (403).</li>
 * </ol>
 * Exécuté avant RouteToRequestUrlFilter (ordre 10000), qui construit l'URL relayée à partir de la requête.
 */
@Component
public class PropagationIdentiteFilter implements GlobalFilter, Ordered {

    public static final String PATIENT = "X-Patient-Id";
    public static final String ROLES = "X-Roles";
    private static final Logger log = LoggerFactory.getLogger(PropagationIdentiteFilter.class);

    private final ReponsesSecurite reponses;

    public PropagationIdentiteFilter(ReponsesSecurite reponses) {
        this.reponses = reponses;
    }

    @Override
    public int getOrder() {
        return 9_000;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(jeton -> relayer(exchange, chain, jeton));
    }

    private Mono<Void> relayer(ServerWebExchange exchange, GatewayFilterChain chain, Optional<JwtAuthenticationToken> jeton) {
        List<String> roles = jeton.map(j -> Roles.duJeton(j.getToken())).orElse(List.of());
        String patientId = jeton.map(j -> j.getToken().getClaimAsString(Roles.CLAIM_PATIENT)).orElse(null);
        if (Roles.estPatientSeulement(roles) && (patientId == null || patientId.isBlank())) {
            log.warn("Jeton PATIENT de {} sans claim patient_id : refusé", jeton.get().getName());
            return reponses.refuser(exchange, "Jeton PATIENT sans patient_id : accès impossible aux données patient");
        }
        ServerHttpRequest requete = exchange.getRequest().mutate()
                .uri(sansJeton(exchange.getRequest().getURI()))
                .headers(entetes -> {
                    entetes.remove(PATIENT);
                    entetes.remove(ROLES);
                    if (!roles.isEmpty()) {
                        entetes.set(ROLES, String.join(",", roles));
                    }
                    if (Roles.estPatientSeulement(roles)) {
                        entetes.set(PATIENT, patientId);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(requete).build());
    }

    /** URL sans le paramètre access_token (jeton du WebSocket) : il ne doit jamais atteindre un service. */
    static URI sansJeton(URI uri) {
        if (uri.getRawQuery() == null || !uri.getRawQuery().contains(JetonConverter.PARAMETRE)) {
            return uri;
        }
        return UriComponentsBuilder.fromUri(uri).replaceQueryParam(JetonConverter.PARAMETRE).build(true).toUri();
    }
}
