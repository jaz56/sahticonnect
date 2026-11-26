package tn.sahticonnect.gateway.filtre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identifiant de corrélation : une valeur par requête, pour suivre un même appel d'un service à l'autre.
 * <ul>
 *   <li>repris de l'en-tête {@value #ENTETE} s'il est fourni (et valide), sinon généré (UUID) ;</li>
 *   <li>transmis au service appelé (en-tête de la requête relayée) ;</li>
 *   <li>renvoyé au client dans la réponse, y compris pour un fallback 503 ;</li>
 *   <li>écrit dans le journal de la gateway avec la méthode, le chemin, la route, le statut et la durée.</li>
 * </ul>
 * WebFilter (et non GlobalFilter) : il s'applique aussi à ce qui n'est pas une route (actuator, Swagger, fallback).
 */
@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    public static final String ENTETE = "X-Correlation-Id";
    /** Clé MDC lue par le format des journaux (logging.pattern.console), la même dans tous les services. */
    public static final String CLE_MDC = "correlationId";
    /** Valeur fournie par le client : courte et sans caractères de contrôle (pas d'injection dans les logs). */
    private static final Pattern VALEUR_ACCEPTEE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;   // avant le traitement CORS et le routage
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String recu = exchange.getRequest().getHeaders().getFirst(ENTETE);
        String id = recu != null && VALEUR_ACCEPTEE.matcher(recu).matches() ? recu : UUID.randomUUID().toString();

        ServerHttpRequest requete = exchange.getRequest().mutate().headers(h -> h.set(ENTETE, id)).build();
        exchange.getResponse().getHeaders().set(ENTETE, id);
        ServerWebExchange echange = exchange.mutate().request(requete).build();

        long debut = System.nanoTime();
        return chain.filter(echange).doFinally(signal -> {
            Route route = echange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            // MDC le temps de la ligne (réactif : pas de thread attitré à la requête) → « [id] » en tête de ligne,
            // même format que dans les services
            try (MDC.MDCCloseable c = MDC.putCloseable(CLE_MDC, id)) {
                log.info("{} {} → {} {} ({} ms)", requete.getMethod(), requete.getURI().getRawPath(),
                        route != null ? route.getId() : "-", echange.getResponse().getStatusCode(),
                        (System.nanoTime() - debut) / 1_000_000);
            }
        });
    }
}
