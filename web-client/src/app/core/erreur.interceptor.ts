import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from './notification.service';
import { SANS_NOTIFICATION, lireErreur } from './erreurs';

/**
 * Toute erreur HTTP (REST du portail et de l'orchestrateur, fallback 503 de la gateway…) est lue au format Erreur
 * du contrat et affichée dans un snackbar, avec le X-Correlation-Id renvoyé par la gateway (pour retrouver la
 * requête dans les journaux). L'erreur est ensuite propagée : le composant peut aussi l'afficher sur ses champs.
 */
export const erreurInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const notifications = inject(NotificationService);
  return suivant(requete).pipe(
    catchError((erreur: unknown) => {
      if (erreur instanceof HttpErrorResponse && !requete.context.get(SANS_NOTIFICATION)) {
        notifications.erreur(lireErreur(erreur));
      }
      return throwError(() => erreur);
    }),
  );
};
