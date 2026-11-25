package tn.sahticonnect.gateway.securite;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Jetons JWT de test, signés comme ceux de Keycloak (RS256) avec une clé RSA de test, dont la partie publique est
 * servie en JWKS par WireMock (l'adresse « interne » des clés). L'issuer, lui, est l'adresse PUBLIQUE de Keycloak.
 */
public final class Jetons {

    public static final String ISSUER_PUBLIC = "http://localhost:8180/realms/sahticonnect";
    public static final String ISSUER_INTERNE = "http://keycloak:8080/realms/sahticonnect";
    public static final String CHEMIN_JWKS = "/realms/sahticonnect/protocol/openid-connect/certs";
    public static final String AUDIENCE = "sahticonnect-gateway";

    private static final RSAKey CLE = generer("cle-test");
    private static final RSAKey AUTRE_CLE = generer("cle-pirate");

    private Jetons() {
    }

    /** JWKS publié (clé publique seulement), comme /protocol/openid-connect/certs de Keycloak. */
    public static String jwks() {
        return new JWKSet(CLE.toPublicJWK()).toString();
    }

    public static String patient(String utilisateur, String patientId) {
        return jeton(utilisateur, List.of("PATIENT"), patientId, ISSUER_PUBLIC, AUDIENCE, Instant.now().plusSeconds(300), CLE);
    }

    public static String role(String utilisateur, String role) {
        return jeton(utilisateur, List.of(role), null, ISSUER_PUBLIC, AUDIENCE, Instant.now().plusSeconds(300), CLE);
    }

    /** Jeton PATIENT sans claim patient_id (mapper Keycloak manquant) : la gateway doit le refuser. */
    public static String patientSansPatientId() {
        return jeton("intrus", List.of("PATIENT"), null, ISSUER_PUBLIC, AUDIENCE, Instant.now().plusSeconds(300), CLE);
    }

    public static String avecIssuer(String issuer) {
        return jeton("admin", List.of("ADMIN"), null, issuer, AUDIENCE, Instant.now().plusSeconds(300), CLE);
    }

    public static String avecAudience(String audience) {
        return jeton("admin", List.of("ADMIN"), null, ISSUER_PUBLIC, audience, Instant.now().plusSeconds(300), CLE);
    }

    public static String expire() {
        return jeton("admin", List.of("ADMIN"), null, ISSUER_PUBLIC, AUDIENCE, Instant.now().minusSeconds(120), CLE);
    }

    public static String signeParUneAutreCle() {
        return jeton("admin", List.of("ADMIN"), null, ISSUER_PUBLIC, AUDIENCE, Instant.now().plusSeconds(300), AUTRE_CLE);
    }

    public static String jeton(String utilisateur, List<String> roles, String patientId, String issuer, String audience,
                               Instant expiration, RSAKey cle) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("id-" + utilisateur)
                .audience(List.of(audience, "account"))
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(expiration))
                .claim("preferred_username", utilisateur)
                .claim("azp", "web-client")
                .claim("realm_access", Map.of("roles", roles));
        if (patientId != null) {
            claims.claim("patient_id", patientId);
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cle.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(cle));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generer(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
