import { EnvironmentProviders } from '@angular/core';
import { LogLevel, provideAuth, withAppInitializerAuthCheck } from 'angular-auth-oidc-client';
import { environment } from '../../../environments/environment';

/**
 * OIDC avec angular-auth-oidc-client (certifiée OpenID, indépendante de Keycloak) :
 * <ul>
 *   <li>Authorization Code + PKCE (S256) : aucun secret dans le navigateur, le code d'autorisation intercepté est
 *       inutilisable sans le code_verifier gardé par l'application ;</li>
 *   <li>rafraîchissement automatique avec le refresh token, 30 s avant l'expiration du jeton (5 min) ;</li>
 *   <li>au démarrage (withAppInitializerAuthCheck) : retour de Keycloak traité, ou session existante restaurée.</li>
 * </ul>
 */
export function provideAuthentification(): EnvironmentProviders {
  const origine = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:4200';
  return provideAuth(
    {
      config: {
        authority: environment.keycloak.authority,
        clientId: environment.keycloak.clientId,
        redirectUrl: origine,
        postLogoutRedirectUri: origine,
        responseType: 'code', // Authorization Code (+ PKCE, automatique pour ce type de réponse)
        scope: 'openid profile email',
        silentRenew: true,
        useRefreshToken: true,
        renewTimeBeforeTokenExpiresInSeconds: 30,
        ignoreNonceAfterRefresh: true,
        triggerRefreshWhenIdTokenExpired: false,
        historyCleanupOff: false,
        logLevel: LogLevel.Warn,
      },
    },
    withAppInitializerAuthCheck(),
  );
}
