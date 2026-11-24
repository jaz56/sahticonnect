import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { Subscription } from 'rxjs';
import {
  NouvelleAlerteGQL,
  NouvelleAlerteSubscription,
  NouvelleInterventionGQL,
  NouvelleInterventionSubscription,
} from '../../../graphql/generated';
import { SessionService } from '../../../core/session.service';
import { NotificationService } from '../../../core/notification.service';
import { Pastille } from '../../../shared/pastille';
import { LIBELLES_CONSTANTES, LIBELLES_INTERVENTIONS, libelle } from '../../../shared/libelles';

type AlerteDirecte = NouvelleAlerteSubscription['nouvelleAlerte'];
type InterventionDirecte = NouvelleInterventionSubscription['nouvelleIntervention'];

const MAX_EN_LISTE = 50;

/**
 * Temps réel côté médecin : subscriptions GraphQL (WebSocket graphql-transport-ws via la gateway).
 * nouvelleAlerte → liste en direct + notification colorée selon le niveau ; nouvelleIntervention → fiche qui apparaît.
 */
@Component({
  selector: 'app-alertes-direct',
  imports: [DatePipe, DecimalPipe, RouterLink, MatCardModule, MatIconModule, MatSlideToggleModule, MatButtonModule, Pastille],
  templateUrl: './alertes-direct.html',
  styleUrl: './alertes-direct.scss',
})
export default class AlertesDirect {
  private readonly alertesGql = inject(NouvelleAlerteGQL);
  private readonly interventionsGql = inject(NouvelleInterventionGQL);
  private readonly notifications = inject(NotificationService);
  protected readonly session = inject(SessionService);

  /** false : tous les patients ; true : seulement le patient choisi dans la barre du haut. */
  protected readonly patientCourantSeulement = signal(false);
  protected readonly alertes = signal<AlerteDirecte[]>([]);
  protected readonly interventions = signal<InterventionDirecte[]>([]);
  protected readonly depuis = new Date();
  protected readonly libelleConstante = (c: string) => libelle(LIBELLES_CONSTANTES, c);
  protected readonly libelleIntervention = (c: string) => libelle(LIBELLES_INTERVENTIONS, c);

  constructor() {
    let abonnements: Subscription[] = [];
    // (ré)abonnement quand le filtre change ; désabonnement (fermeture des subscriptions) à la destruction
    effect(() => {
      const patientId = this.patientCourantSeulement() ? this.session.patientId() : undefined;
      abonnements.forEach((a) => a.unsubscribe());
      abonnements = [
        this.alertesGql.subscribe({ variables: { patientId, niveauMinimum: 'ELEVEE' } }).subscribe((r) => {
          const alerte = r.data?.nouvelleAlerte;
          if (alerte) {
            this.alertes.update((liste) => [alerte, ...liste].slice(0, MAX_EN_LISTE));
            this.notifications.alerte(`${alerte.niveau} · ${alerte.patientId} · ${alerte.message ?? alerte.seuilFranchi}`, alerte.niveau);
          }
        }),
        this.interventionsGql.subscribe({ variables: { patientId } }).subscribe((r) => {
          const intervention = r.data?.nouvelleIntervention;
          if (intervention) {
            this.interventions.update((liste) => [intervention, ...liste.filter((i) => i.id !== intervention.id)].slice(0, MAX_EN_LISTE));
          }
        }),
      ];
    });
    inject(DestroyRef).onDestroy(() => abonnements.forEach((a) => a.unsubscribe()));
  }
}
