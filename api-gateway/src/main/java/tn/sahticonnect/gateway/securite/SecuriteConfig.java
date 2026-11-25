package tn.sahticonnect.gateway.securite;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

import static tn.sahticonnect.gateway.securite.Roles.ADMIN;
import static tn.sahticonnect.gateway.securite.Roles.MEDECIN;
import static tn.sahticonnect.gateway.securite.Roles.PATIENT;

/**
 * La gateway est le point d'application de la sécurité : resource server OAuth2, jetons JWT émis par Keycloak
 * (realm sahticonnect), vérifiés localement avec les clés publiques du realm (JWKS, mises en cache).
 *
 * <pre>
 * /api/portail/**                          PATIENT, MEDECIN, ADMIN (+ contrôle d'appartenance dans le portail)
 * /api/graphql                             MEDECIN, ADMIN (WebSocket : jeton dans ?access_token)
 * GET /api/cnam/ws/**                      public (WSDL, XSD)
 * /api/cnam/ws/** (SOAP)                   ADMIN
 * POST /api/orchestrateur/workflows/declencher   MEDECIN, ADMIN
 * /api/orchestrateur/**                    ADMIN (dont le flux SSE)
 * public : /actuator/health, Swagger UI, contrats OpenAPI relus, fallbacks
 * </pre>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(SecuriteProperties.class)
public class SecuriteConfig {

    @Bean
    SecurityWebFilterChain chaineSecurite(ServerHttpSecurity http, SecuriteProperties proprietes, ReponsesSecurite reponses) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)          // API sans cookie de session : pas de CSRF
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()) // sans état : jeton à chaque requête
                .cors(cors -> cors.configurationSource(cors(proprietes)));

        if (!proprietes.active()) {
            return http.authorizeExchange(regles -> regles.anyExchange().permitAll()).build();
        }
        return http
                .authorizeExchange(regles -> regles
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()   // préflights CORS
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**").permitAll()
                        .pathMatchers("/api/portail/openapi/**", "/api/orchestrateur/openapi/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/cnam/ws/**").permitAll()   // WSDL et XSD
                        .pathMatchers("/api/portail/**").hasAnyRole(PATIENT, MEDECIN, ADMIN)
                        .pathMatchers("/api/graphql").hasAnyRole(MEDECIN, ADMIN)
                        .pathMatchers("/api/cnam/ws/**").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/orchestrateur/workflows/declencher").hasAnyRole(MEDECIN, ADMIN)
                        .pathMatchers("/api/orchestrateur/**").hasRole(ADMIN)
                        .pathMatchers("/actuator/**").hasRole(ADMIN)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(serveur -> serveur
                        .bearerTokenConverter(new JetonConverter())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(Roles.convertisseur()))
                        .authenticationEntryPoint(reponses)
                        .accessDeniedHandler(reponses))
                .exceptionHandling(erreurs -> erreurs.authenticationEntryPoint(reponses).accessDeniedHandler(reponses))
                .build();
    }

    /**
     * CORS du client Angular, préflights compris, y compris pour les réponses 401/403 de la sécurité (sinon le
     * navigateur ne pourrait pas les lire). Les WebSocket sont aussi refusées depuis une autre origine (403).
     */
    static CorsConfigurationSource cors(SecuriteProperties proprietes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(proprietes.originesCors());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Correlation-Id", "SOAPAction",
                "Cache-Control", "Last-Event-ID"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Location", "WWW-Authenticate"));
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
