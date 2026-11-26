import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Authentification } from './authentification.service';

/** URL de la gateway qui ne demandent pas de jeton (et ne doivent pas en recevoir). */
const PUBLIQUES = ['/actuator/health'];

/**
 * Ajoute « Authorization: Bearer <jeton> » aux requêtes vers la gateway (REST, GraphQL HTTP, SOAP), jamais vers une
 * autre origine (Keycloak, CDN…) : le jeton ne doit partir que vers l'API à laquelle il est destiné.
 */
export const jetonInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const versGateway = requete.url.startsWith(environment.gatewayUrl);
  const publique = PUBLIQUES.some((p) => requete.url.startsWith(environment.gatewayUrl + p));
  if (!versGateway || publique || requete.headers.has('Authorization')) {
    return suivant(requete);
  }
  return from(inject(Authentification).jeton()).pipe(
    switchMap((jeton) =>
      suivant(jeton ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete),
    ),
  );
};
