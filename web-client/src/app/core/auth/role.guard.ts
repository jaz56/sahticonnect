import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Authentification, RoleSahti, utilisateurDuJeton } from './authentification.service';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map, of, switchMap, take } from 'rxjs';

/**
 * Garde d'une route : l'utilisateur doit être connecté (sinon redirection vers Keycloak) et avoir l'un des rôles
 * demandés (sinon écran « Accès refusé »). La gateway applique les mêmes règles côté serveur : le garde n'est
 * qu'un confort d'interface, pas la sécurité.
 */
export function roleGuard(...roles: RoleSahti[]): CanActivateFn {
  return () => {
    const oidc = inject(OidcSecurityService);
    const authentification = inject(Authentification);
    const router = inject(Router);
    return oidc.isAuthenticated$.pipe(
      take(1),
      switchMap(({ isAuthenticated }) =>
        isAuthenticated ? oidc.getPayloadFromAccessToken().pipe(map((p) => utilisateurDuJeton(p))) : of(null),
      ),
      map((utilisateur) => {
        if (!utilisateur) {
          authentification.connecter();
          return false;
        }
        return roles.some((r) => utilisateur.roles.includes(r)) ? true : router.createUrlTree(['/acces-refuse']);
      }),
    );
  };
}
