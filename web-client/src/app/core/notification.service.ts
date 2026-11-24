import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ErreurApi } from './erreurs';

/** Messages éphémères (snackbar) : erreurs des services, confirmations, alertes en direct. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  erreur(erreur: ErreurApi): void {
    const suivi = erreur.correlationId ? ` — X-Correlation-Id : ${erreur.correlationId}` : '';
    const service = erreur.service ? ` [${erreur.service}]` : '';
    this.snackBar.open(`${erreur.code}${service} : ${erreur.message}${suivi}`, 'Fermer', {
      duration: 8000,
      panelClass: 'snack-erreur',
    });
  }

  succes(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000, panelClass: 'snack-succes' });
  }

  alerte(message: string, niveau: 'CRITIQUE' | 'ELEVEE'): void {
    this.snackBar.open(message, 'Voir', {
      duration: 6000,
      panelClass: niveau === 'CRITIQUE' ? 'snack-critique' : 'snack-elevee',
    });
  }
}
