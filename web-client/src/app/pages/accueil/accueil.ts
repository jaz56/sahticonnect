import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PATIENTS, SessionService } from '../../core/session.service';
import { NiveauAlerte, OrchestrateurService, Workflow } from '../../api/orchestrateur';
import { alerteSimulee } from '../../core/alertes-simulees';
import { Pastille } from '../../shared/pastille';
import { SANS_NOTIFICATION } from '../../core/erreurs';

type EtatGateway = 'UP' | 'DOWN' | 'INJOIGNABLE' | 'VERIFICATION';

@Component({
  selector: 'app-accueil',
  imports: [
    FormsModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatButtonToggleModule, MatProgressSpinnerModule, Pastille, KeyValuePipe,
  ],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export default class Accueil {
  private readonly http = inject(HttpClient);
  private readonly orchestrateur = inject(OrchestrateurService);
  protected readonly session = inject(SessionService);

  protected readonly patients = PATIENTS;
  protected readonly gatewayUrl = environment.gatewayUrl;
  protected readonly etatGateway = signal<EtatGateway>('VERIFICATION');
  protected readonly composants = signal<Record<string, string>>({});

  protected patientAlerte = this.session.patientId();
  protected niveauAlerte: NiveauAlerte = NiveauAlerte.CRITIQUE;
  protected readonly envoi = signal(false);
  protected readonly workflowCree = signal<Workflow | null>(null);

  constructor() {
    // état de la gateway, vérifié toutes les 10 s (le 503 de l'actuator est attendu : pas de snackbar)
    timer(0, 10000)
      .pipe(
        switchMap(() =>
          this.http
            .get<{ status: string; components?: Record<string, { status: string }> }>(environment.santeGatewayUrl, {
              context: new HttpContext().set(SANS_NOTIFICATION, true),
            })
            .pipe(
              map((s) => ({ etat: s.status === 'UP' ? ('UP' as const) : ('DOWN' as const), composants: s.components ?? {} })),
              catchError((e) => of({ etat: e.status === 503 ? ('DOWN' as const) : ('INJOIGNABLE' as const), composants: {} })),
            ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(({ etat, composants }) => {
        this.etatGateway.set(etat);
        this.composants.set(Object.fromEntries(Object.entries(composants).map(([nom, c]) => [nom, c.status])));
      });
  }

  protected simulerAlerte(): void {
    this.envoi.set(true);
    this.workflowCree.set(null);
    this.orchestrateur
      .declencherWorkflow({ declenchementAlerte: alerteSimulee(this.patientAlerte, this.niveauAlerte) })
      .subscribe({
        next: (wf) => {
          this.workflowCree.set(wf);
          this.envoi.set(false);
        },
        error: () => this.envoi.set(false),
      });
  }
}
