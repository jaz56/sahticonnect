import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { EtatWorkflow, OrchestrateurService, Workflow } from '../../../api/orchestrateur';
import { FluxWorkflowsService } from '../../../core/flux-workflows.service';
import { NotificationService } from '../../../core/notification.service';
import { PATIENTS } from '../../../core/session.service';
import { DtPipe } from '../../../core/dt.pipe';
import { FriseWorkflow } from '../../../shared/frise-workflow';
import { Pastille } from '../../../shared/pastille';
import { LIBELLES_ETAPES, LIBELLES_INTERVENTIONS, libelle } from '../../../shared/libelles';

/**
 * Workflows de l'orchestrateur : liste (REST, filtres patient et état), détail en frise des 5 étapes,
 * mise à jour en direct par le flux SSE ; « Relancer » pour un workflow en ECHEC.
 */
@Component({
  selector: 'app-workflows',
  imports: [
    DatePipe, DtPipe, MatCardModule, MatFormFieldModule, MatSelectModule, MatButtonModule, MatIconModule, MatListModule,
    MatProgressBarModule, FriseWorkflow, Pastille,
  ],
  templateUrl: './workflows.html',
  styleUrl: './workflows.scss',
})
export default class Workflows {
  private readonly orchestrateur = inject(OrchestrateurService);
  private readonly notifications = inject(NotificationService);
  protected readonly flux = inject(FluxWorkflowsService);

  /** ?id=WF-… : workflow à ouvrir (lien « Suivre le workflow » de l'accueil). */
  readonly id = input<string>();

  protected readonly patients = PATIENTS;
  protected readonly etats = Object.values(EtatWorkflow);
  protected readonly filtrePatient = signal<string | null>(null);
  protected readonly filtreEtat = signal<EtatWorkflow | null>(null);

  protected readonly workflows = signal<Workflow[]>([]);
  protected readonly selectionId = signal<string | null>(null);
  protected readonly selection = computed(() => this.workflows().find((w) => w.id === this.selectionId()) ?? null);
  protected readonly chargement = signal(false);
  protected readonly dernierEvenement = signal<Date | null>(null);
  protected readonly libelleEtape = (c?: string) => libelle(LIBELLES_ETAPES, c);
  protected readonly libelleIntervention = (c?: string) => libelle(LIBELLES_INTERVENTIONS, c);

  constructor() {
    effect(() => {
      const patientId = this.filtrePatient() ?? undefined;
      const etat = this.filtreEtat() ?? undefined;
      untracked(() => this.charger(patientId, etat));
    });
    effect(() => {
      const id = this.id();
      if (id) {
        untracked(() => this.selectionId.set(id));
      }
    });

    // flux SSE : chaque changement d'état d'un workflow remplace (ou ajoute) sa ligne
    this.flux.workflows$.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((wf) => {
      this.dernierEvenement.set(new Date());
      if (!this.correspondAuxFiltres(wf)) {
        return;
      }
      this.workflows.update((liste) => {
        const sans = liste.filter((w) => w.id !== wf.id);
        return [wf, ...sans].sort((a, b) => b.dateCreation.localeCompare(a.dateCreation));
      });
    });
  }

  private charger(patientId?: string, etat?: EtatWorkflow): void {
    this.chargement.set(true);
    this.orchestrateur.listerWorkflows({ patientId, etat }).subscribe({
      next: (liste) => {
        this.workflows.set(liste);
        this.chargement.set(false);
        if (!this.selectionId() && liste.length > 0) {
          this.selectionId.set(liste[0].id);
        }
      },
      error: () => this.chargement.set(false),
    });
  }

  private correspondAuxFiltres(wf: Workflow): boolean {
    return (!this.filtrePatient() || wf.patientId === this.filtrePatient())
      && (!this.filtreEtat() || wf.etat === this.filtreEtat());
  }

  protected relancer(wf: Workflow): void {
    this.orchestrateur.relancerWorkflow({ id: wf.id }).subscribe((relance) => {
      this.notifications.succes(`Workflow ${wf.id} relancé : seules les étapes manquantes sont rejouées`);
      this.workflows.update((liste) => liste.map((w) => (w.id === relance.id ? relance : w)));
    });
  }
}
