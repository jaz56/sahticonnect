import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { switchMap, timer } from 'rxjs';
import {
  ConstantesPatientGQL,
  FichePatientGQL,
  FichePatientQuery,
  HistoriqueSoinsGQL,
  HistoriqueSoinsQuery,
  ResoudreAlerteGQL,
  TypeConstante,
} from '../../../graphql/generated';
import { ChampIndisponible, autresErreurs, champsIndisponibles } from '../../../core/graphql/reponse-partielle';
import { NotificationService } from '../../../core/notification.service';
import { DtPipe } from '../../../core/dt.pipe';
import { Pastille } from '../../../shared/pastille';
import { ServiceIndisponible } from '../../../shared/service-indisponible';
import { LIBELLES_CONSTANTES, LIBELLES_INTERVENTIONS, libelle } from '../../../shared/libelles';

type Fiche = NonNullable<FichePatientQuery['patient']>;
type Constante = NonNullable<Fiche['constantes']>[number];
type PageHistorique = NonNullable<HistoriqueSoinsQuery['historiqueSoins']>;

const COULEURS_NIVEAUX: Record<string, string> = { CRITIQUE: '#b3261e', ELEVEE: '#e07b00' };

/**
 * Fiche patient du médecin : UNE requête GraphQL (données locales + constantes gRPC + couverture SOAP) ;
 * un service distant en panne n'efface que son champ, remplacé par une puce « Service indisponible ».
 * Graphique des constantes rafraîchi toutes les 5 s (query légère ConstantesPatient), historique de soins paginé.
 */
@Component({
  selector: 'app-fiche-patient',
  imports: [
    DatePipe, DecimalPipe, RouterLink, DtPipe, MatCardModule, MatButtonModule, MatButtonToggleModule, MatIconModule,
    MatTableModule, MatPaginatorModule, MatProgressBarModule, MatTooltipModule, BaseChartDirective, Pastille,
    ServiceIndisponible,
  ],
  // Chart.js n'est chargé qu'avec cet écran (chunk lazy), pas dans le bundle initial
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './fiche-patient.html',
  styleUrl: './fiche-patient.scss',
})
export default class FichePatient {
  private readonly ficheGql = inject(FichePatientGQL);
  private readonly constantesGql = inject(ConstantesPatientGQL);
  private readonly historiqueGql = inject(HistoriqueSoinsGQL);
  private readonly resoudreGql = inject(ResoudreAlerteGQL);
  private readonly notifications = inject(NotificationService);

  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly fiche = signal<Fiche | null>(null);
  protected readonly introuvable = signal(false);
  protected readonly erreurs = signal<string[]>([]);
  protected readonly indisponibles = signal(new Map<string, ChampIndisponible>());
  protected readonly constantes = signal<Constante[] | null>(null);
  protected readonly chargement = signal(false);

  protected readonly historique = signal<PageHistorique | null>(null);
  protected readonly historiqueIndisponible = signal<ChampIndisponible | null>(null);
  protected readonly pageHistorique = signal(0);
  protected readonly tailleHistorique = 5;

  protected readonly typeGraphique = signal<TypeConstante>('FREQUENCE_CARDIAQUE');
  protected readonly typesConstantes: TypeConstante[] = [
    'FREQUENCE_CARDIAQUE', 'SPO2', 'TENSION_SYSTOLIQUE', 'TENSION_DIASTOLIQUE', 'TEMPERATURE',
  ];
  protected readonly libellesConstantes = LIBELLES_CONSTANTES;
  protected readonly libelleIntervention = (code: string) => libelle(LIBELLES_INTERVENTIONS, code);
  protected readonly colonnesHistorique = ['date', 'etablissement', 'diagnostic', 'actes', 'numeroFeuilleSoins'];

  /** Dernière valeur de chaque type de constante. */
  protected readonly dernieres = computed(() => {
    const parType = new Map<string, Constante>();
    for (const c of [...(this.constantes() ?? [])].sort((a, b) => a.horodatage.localeCompare(b.horodatage))) {
      parType.set(c.type, c);
    }
    return [...parType.values()];
  });

  protected readonly donneesGraphique = computed<ChartConfiguration<'line'>['data']>(() => {
    const serie = (this.constantes() ?? [])
      .filter((c) => c.type === this.typeGraphique())
      .sort((a, b) => a.horodatage.localeCompare(b.horodatage));
    return {
      labels: serie.map((c) => c.horodatage.substring(11, 19)),
      datasets: [
        {
          data: serie.map((c) => c.valeur),
          label: `${LIBELLES_CONSTANTES[this.typeGraphique()]} (${serie[0]?.unite ?? ''})`,
          borderColor: '#0061a4',
          backgroundColor: 'rgba(0, 97, 164, 0.08)',
          fill: true,
          tension: 0.3,
          pointRadius: 4,
          // le niveau vient de telemetrie (Mesure.niveau) : le client ne recalcule aucun seuil
          pointBackgroundColor: serie.map((c) => COULEURS_NIVEAUX[c.niveau ?? ''] ?? '#0061a4'),
        },
      ],
    };
  });

  protected readonly optionsGraphique: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    plugins: { legend: { display: true, position: 'bottom' } },
  };

  constructor() {
    effect(() => {
      const id = this.id();
      this.charger(id);
      this.pageHistorique.set(0);
      this.chargerHistorique(id, 0);
    });

    // rafraîchissement du graphique toutes les 5 s (seulement les constantes, pas toute la fiche)
    toObservable(this.id)
      .pipe(
        switchMap((id) => timer(5000, 5000).pipe(switchMap(() => this.constantesGql.fetch({ variables: { id }, errorPolicy: 'all' })))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((r) => {
        const indispo = champsIndisponibles(r.error).get('patient.constantes');
        this.indisponibles.update((m) => {
          const copie = new Map(m);
          indispo ? copie.set('patient.constantes', indispo) : copie.delete('patient.constantes');
          return copie;
        });
        if (r.data?.patient) {
          this.constantes.set(r.data.patient.constantes ?? null);
        }
      });
  }

  /** errorPolicy « all » : une réponse partielle garde ses données ET ses erreurs. */
  protected charger(id = this.id()): void {
    this.chargement.set(true);
    this.ficheGql.fetch({ variables: { id }, errorPolicy: 'all' }).subscribe({
      next: (r) => {
        this.chargement.set(false);
        this.indisponibles.set(champsIndisponibles(r.error));
        this.erreurs.set(autresErreurs(r.error));
        const patient = r.data?.patient ?? null;
        this.introuvable.set(!patient && this.erreurs().length === 0);
        this.fiche.set(patient);
        this.constantes.set(patient?.constantes ?? null);
      },
      error: () => this.chargement.set(false),
    });
  }

  protected chargerHistorique(id: string, page: number): void {
    this.historiqueGql.fetch({ variables: { patientId: id, page, taille: this.tailleHistorique }, errorPolicy: 'all' }).subscribe((r) => {
      this.historiqueIndisponible.set(champsIndisponibles(r.error).get('historiqueSoins') ?? null);
      this.historique.set(r.data?.historiqueSoins ?? null);
    });
  }

  protected changerPage(e: PageEvent): void {
    this.pageHistorique.set(e.pageIndex);
    this.chargerHistorique(this.id(), e.pageIndex);
  }

  protected indisponible(chemin: string): ChampIndisponible | undefined {
    return this.indisponibles().get(chemin);
  }

  protected resoudre(alerteId: string): void {
    this.resoudreGql
      .mutate({ variables: { id: alerteId, commentaire: 'Résolue depuis la fiche patient (client web)' } })
      .subscribe((r) => {
        if (r.data?.resoudreAlerte) {
          this.notifications.succes(`Alerte ${alerteId} résolue`);
          this.charger(); // le niveau de risque est recalculé par le dashboard
        }
      });
  }

  protected actes(entree: PageHistorique['elements'][number]): string {
    return entree.actes.map((a) => `${a.lettreCle} ${a.coefficient}`).join(' + ');
  }
}
