package tn.sahticonnect.gateway.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corps des réponses d'erreur produites par la gateway elle-même (fallbacks, sécurité) :
 * le schéma {@code Erreur} des contrats REST, ou une SOAP Fault sur la route SOAP.
 */
public final class ReponsesErreur {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    private ReponsesErreur() {
    }

    /** Schéma Erreur des contrats (horodatage, statut, code, message, chemin), plus « service » s'il est connu. */
    public static Map<String, Object> erreur(int statut, String code, String message, String chemin, String service) {
        Map<String, Object> erreur = new LinkedHashMap<>();
        erreur.put("horodatage", OffsetDateTime.now(TUNIS).truncatedTo(ChronoUnit.SECONDS).toString());
        erreur.put("statut", statut);
        erreur.put("code", code);
        erreur.put("message", message);
        erreur.put("chemin", chemin);
        if (service != null) {
            erreur.put("service", service);
        }
        return erreur;
    }

    /**
     * SOAP Fault 1.1 émise par la gateway (faultactor api-gateway) ; le &lt;detail&gt; porte un code stable,
     * comme les faults de dossier-cnam.
     *
     * @param faultcode Client (requête refusée : jeton, droits) ou Server (service indisponible)
     */
    public static String faultSoap(String faultcode, String message, String code, String service) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                  <SOAP-ENV:Body>
                    <SOAP-ENV:Fault>
                      <faultcode>SOAP-ENV:%s</faultcode>
                      <faultstring xml:lang="fr">%s</faultstring>
                      <faultactor>api-gateway</faultactor>
                      <detail>
                        <gw:erreurGateway xmlns:gw="http://sahticonnect.tn/gateway">
                          <gw:code>%s</gw:code>
                          <gw:service>%s</gw:service>
                        </gw:erreurGateway>
                      </detail>
                    </SOAP-ENV:Fault>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """.formatted(faultcode, xml(message), xml(code), xml(service));
    }

    private static String xml(String texte) {
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
