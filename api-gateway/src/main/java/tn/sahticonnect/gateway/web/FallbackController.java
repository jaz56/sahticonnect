package tn.sahticonnect.gateway.web;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Réponses de repli des circuit breakers (filtre CircuitBreaker, {@code fallbackUri: forward:/fallback/...}),
 * quand le service ne répond pas, dépasse le délai ou que son circuit est ouvert.
 * <ul>
 *   <li>REST et GraphQL : 503 au format {@code Erreur} du contrat, code {@code SERVICE_INDISPONIBLE}, plus le champ
 *       {@code service} (le schéma Erreur n'interdit pas les propriétés supplémentaires) ;</li>
 *   <li>SOAP : une SOAP Fault {@code Server}, en HTTP 500 comme l'exige SOAP 1.1 / WS-I Basic Profile (R1126) pour
 *       toute réponse Fault : un client SOAP lit alors la Fault au lieu d'une erreur de transport.</li>
 * </ul>
 */
@RestController
public class FallbackController {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, Object>> indisponible(@PathVariable String service, ServerWebExchange exchange) {
        Map<String, Object> erreur = new LinkedHashMap<>();
        erreur.put("horodatage", OffsetDateTime.now(TUNIS).truncatedTo(ChronoUnit.SECONDS).toString());
        erreur.put("statut", 503);
        erreur.put("code", "SERVICE_INDISPONIBLE");
        erreur.put("message", message(service, exchange));
        erreur.put("chemin", cheminOriginal(exchange));
        erreur.put("service", service);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(erreur);
    }

    @RequestMapping(value = "/fallback/soap/{service}", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> faultSoap(@PathVariable String service, ServerWebExchange exchange) {
        String fault = """
                <?xml version="1.0" encoding="UTF-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                  <SOAP-ENV:Body>
                    <SOAP-ENV:Fault>
                      <faultcode>SOAP-ENV:Server</faultcode>
                      <faultstring xml:lang="fr">%s</faultstring>
                      <faultactor>api-gateway</faultactor>
                      <detail>
                        <gw:erreurGateway xmlns:gw="http://sahticonnect.tn/gateway">
                          <gw:code>SERVICE_INDISPONIBLE</gw:code>
                          <gw:service>%s</gw:service>
                        </gw:erreurGateway>
                      </detail>
                    </SOAP-ENV:Fault>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """.formatted(xml(message(service, exchange)), xml(service));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType("text/xml;charset=UTF-8")).body(fault);
    }

    private static String message(String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String raison;
        if (cause instanceof CallNotPermittedException) {
            raison = "circuit ouvert après des échecs répétés, nouvel essai dans quelques secondes";
        } else if (cause instanceof TimeoutException
                || cause instanceof ResponseStatusException r && r.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT) {
            raison = "aucune réponse dans le délai imparti";
        } else if (cause != null) {
            raison = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        } else {
            raison = "aucune réponse";
        }
        return "Service " + service + " indisponible : " + raison;
    }

    private static String cheminOriginal(ServerWebExchange exchange) {
        Set<URI> urls = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        return urls != null && !urls.isEmpty() ? urls.iterator().next().getRawPath() : exchange.getRequest().getPath().value();
    }

    private static String xml(String texte) {
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
