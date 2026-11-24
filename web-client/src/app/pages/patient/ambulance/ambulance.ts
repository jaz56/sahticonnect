import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subscription, catchError, of, switchMap, timer } from 'rxjs';
import { MissionAmbulance, PortailService, StatutMission } from '../../../api/portail';
import { ErreurApi, SANS_NOTIFICATION, lireErreur } from '../../../core/erreurs';

const ETAPES_MISSION: StatutMission[] = [
  StatutMission.DEMANDEE, StatutMission.DEPECHEE, StatutMission.SUR_PLACE, StatutMission.TRANSPORT, StatutMission.TERMINEE,
];

/** Suivi d'une mission AMB-AAAA-NNNNNN, rafraîchi toutes les 10 s (le statut avance tout seul côté portail). */
@Component({
  selector: 'app-ambulance',
  imports: [DatePipe, FormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './ambulance.html',
  styleUrl: './ambulance.scss',
})
export default class Ambulance {
  private readonly portail = inject(PortailService);
  private readonly destroyRef = inject(DestroyRef);

  /** ?mission=AMB-… (lien depuis une autre page). */
  readonly mission = input<string>();
  protected identifiant = '';
  protected readonly suivie = signal<MissionAmbulance | null>(null);
  protected readonly erreur = signal<ErreurApi | null>(null);
  protected readonly derniereMiseAJour = signal<Date | null>(null);
  protected readonly etapes = ETAPES_MISSION;
  private abonnement?: Subscription;

  ngOnInit(): void {
    this.identifiant = this.mission() ?? '';
    if (this.identifiant) {
      this.suivre();
    }
  }

  protected suivre(): void {
    const id = this.identifiant.trim().toUpperCase();
    this.abonnement?.unsubscribe();
    this.erreur.set(null);
    this.suivie.set(null);
    this.abonnement = timer(0, 10000)
      .pipe(
        switchMap(() =>
          this.portail
            .consulterMissionAmbulance({ id }, 'body', false, { context: new HttpContext().set(SANS_NOTIFICATION, true) })
            .pipe(catchError((e: HttpErrorResponse) => {
              this.erreur.set(lireErreur(e));
              return of(null);
            })),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((m) => {
        if (m) {
          this.suivie.set(m);
          this.erreur.set(null);
          this.derniereMiseAJour.set(new Date());
        }
      });
  }

  protected atteinte(statut: StatutMission, courant: StatutMission): boolean {
    return ETAPES_MISSION.indexOf(statut) <= ETAPES_MISSION.indexOf(courant);
  }
}
