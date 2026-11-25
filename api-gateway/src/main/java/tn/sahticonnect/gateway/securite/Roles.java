package tn.sahticonnect.gateway.securite;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Rôles de SahtiConnect, lus dans le claim Keycloak {@code realm_access.roles} du jeton. */
public final class Roles {

    public static final String PATIENT = "PATIENT";
    public static final String MEDECIN = "MEDECIN";
    public static final String ADMIN = "ADMIN";

    /** Claim (mapper Keycloak sur l'attribut utilisateur patientId) : identifiant du patient connecté. */
    public static final String CLAIM_PATIENT = "patient_id";

    private Roles() {
    }

    /** Rôles du realm, ex. ["PATIENT"]. */
    @SuppressWarnings("unchecked")
    public static List<String> duJeton(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return ((Collection<Object>) roles).stream().map(String::valueOf).toList();
    }

    /** Un PATIENT « pur » (sans rôle MEDECIN ni ADMIN) est limité à ses propres données. */
    public static boolean estPatientSeulement(Collection<String> roles) {
        return roles.contains(PATIENT) && !roles.contains(MEDECIN) && !roles.contains(ADMIN);
    }

    /** Jeton → authentification Spring Security : autorités ROLE_xxx, nom = preferred_username. */
    public static Converter<Jwt, Mono<AbstractAuthenticationToken>> convertisseur() {
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(jwt -> duJeton(jwt).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList());
        convertisseur.setPrincipalClaimName("preferred_username");
        return new ReactiveJwtAuthenticationConverterAdapter(convertisseur);
    }
}
