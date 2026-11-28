import { Component, DestroyRef, LOCALE_ID, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, formatNumber } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { PaginateurFr } from '../../../shared/ui/paginateur-fr';
import { MatProgressBarModule } from '@angular/material/progress-bar';
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
import { LIBELLES_CONSTANTES, LIBELLES_INTERVENTIONS, libelle } from '../../../shared/libelles';
import {
  BadgeNiveau, BlocSection, ElementFil, EnTetePage, EtatErreur, EtatVide, MoniteurConstante, ServiceIndisponible,
  ValeurMono,
} from '../../../shared/ui';

type Fiche = NonNullable<FichePatientQuery['patient']>;
type Constante = NonNullable<Fiche['constantes']>[number];
type PageHistorique = NonNullable<HistoriqueSoinsQuery['historiqueSoins']>;

/** Unités par défaut (les alertes ne portent pas d'unité ; les mesures, si). */
const UNITES: Record<string, string> = {
  FREQUENCE_CARDIAQUE: 'bpm', SPO2: '%', TENSION_SYSTOLIQUE: 'mmHg', TENSION_DIASTOLIQUE: 'mmHg', TEMPERATURE: '°C', CHUTE: '',
};

/** Nombre de points de la mini-courbe d'un moniteur. */
const POINTS_MINI_COURBE = 20;

/** Valeur d'un token de design (variables CSS), lue au moment de dessiner le graphique (canvas : pas de var()). */
function token(nom: string, defaut: string): string {
  const valeur = typeof getComputedStyle === 'function' ? getComputedStyle(document.documentElement).getPropertyValue(nom).trim() : '';
  return valeur || defaut;
}

/**
 * Fiche patient du médecin : UNE requête GraphQL (données locales + constantes gRPC + couverture SOAP) ;
 * un service distant en panne n'efface que son champ, remplacé par une puce « Service indisponible ».
 * Constantes en « moniteurs » rafraîchis toutes les 5 s (query légère ConstantesPatient) : un clic sur un moniteur
 * choisit la courbe détaillée. Alertes et interventions en tableaux, historique de soins paginé.
 */
@Component({
  selector: 'app-fiche-patient',
  imports: [
    DatePipe, RouterLink, DtPipe, MatButtonModule, MatTableModule, MatPaginatorModule, MatProgressBarModule,
    BaseChartDirective, BadgeNiveau, BlocSection, EnTetePage, EtatErreur, EtatVide, MoniteurConstante,
    ServiceIndisponible, ValeurMono,
  ],
  // Chart.js n'est chargé qu'avec cet écran (chunk lazy), pas dans le bundle initial
  providers: [provideCharts(withDefaultRegisterables()), { provide: MatPaginatorIntl, useClass: PaginateurFr }],
  templateUrl: './fiche-patient.html',
  styleUrl: './fiche-patient.scss',
})
export default class FichePatient {
  private readonly ficheGql = inject(FichePatientGQL);
  private readonly constantesGql = inject(ConstantesPatientGQL);
  private readonly historiqueGql = inject(HistoriqueSoinsGQL);
  private readonly resoudreGql = inject(ResoudreAlerteGQL);
  private readonly notifications = inject(NotificationService);
  private readonly locale = inject(LOCALE_ID);

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
  protected readonly unites = UNITES;
  protected readonly libelleIntervention = (code: string) => libelle(LIBELLES_INTERVENTIONS, code);
  protected readonly colonnesHistorique = ['date', 'etablissement', 'diagnostic', 'actes', 'numeroFeuilleSoins'];

  protected readonly fil = computed<ElementFil[]>(() => [
    { libelle: 'Espace médecin' },
    { libelle: 'Tableau de bord', lien: '/medecin/tableau-de-bord' },
    { libelle: this.id() },
  ]);

  /** Mesures par type, de la plus ancienne à la plus récente. */
  private readonly series = computed(() => {
    const parType = new Map<string, Constante[]>();
    for (const c of [...(this.constantes() ?? [])].sort((a, b) => a.horodatage.localeCompare(b.horodatage))) {
      parType.set(c.type, [...(parType.get(c.type) ?? []), c]);
    }
    return parType;
  });

  /** Un moniteur par constante (toujours les 5 : la grille ne bouge pas quand une mesure manque) ; « Chute » si reçue. */
  protected readonly moniteurs = computed(() => {
    const series = this.series();
    const types: string[] = [...this.typesConstantes, ...(series.has('CHUTE') ? ['CHUTE'] : [])];
    return types.map((type) => {
      const serie = series.get(type) ?? [];
      const derniere = serie.at(-1);
      return {
        type: type as TypeConstante,
        libelle: LIBELLES_CONSTANTES[type],
        valeur: derniere ? formatNumber(derniere.valeur, this.locale, '1.0-1') : null,
        unite: derniere?.unite ?? UNITES[type],
        niveau: derniere?.niveau ?? null,
        serie: serie.slice(-POINTS_MINI_COURBE).map((c) => c.valeur),
        heure: derniere ? derniere.horodatage.substring(11, 19) : null,
      };
    });
  });

  /** Heure de la mesure la plus récente, toutes constantes confondues. */
  protected readonly derniereMesure = computed(() =>
    [...this.series().values()].map((s) => s.at(-1)!.horodatage).sort().at(-1)?.substring(11, 19) ?? null,
  );

  protected readonly serieGraphique = computed(() => this.series().get(this.typeGraphique()) ?? []);

  protected readonly donneesGraphique = computed<ChartConfiguration<'line'>['data']>(() => {
    const serie = this.serieGraphique();
    const accent = token('--sc-accent', '#0f5c63');
    const couleurs: Record<string, string> = {
      CRITIQUE: token('--sc-critique', '#a4161a'),
      ELEVEE: token('--sc-elevee', '#8a4b00'),
    };
    return {
      labels: serie.map((c) => c.horodatage.substring(11, 19)),
      datasets: [
        {
          data: serie.map((c) => c.valeur),
          label: `${LIBELLES_CONSTANTES[this.typeGraphique()]} (${serie[0]?.unite ?? UNITES[this.typeGraphique()]})`,
          borderColor: accent,
          borderWidth: 2,
          borderCapStyle: 'round',
          borderJoinStyle: 'round',
          fill: false,
          tension: 0,
          // le niveau vient de telemetrie (Mesure.niveau) : le client ne recalcule aucun seuil ; seules les mesures
          // anormales sont marquées (point de 8 px cerclé de blanc)
          pointRadius: serie.map((c) => (couleurs[c.niveau ?? ''] ? 4 : 0)),
          pointHoverRadius: 5,
          pointBackgroundColor: serie.map((c) => couleurs[c.niveau ?? ''] ?? accent),
          pointBorderColor: token('--sc-surface', '#ffffff'),
          pointBorderWidth: 2,
        },
      ],
    };
  });

  protected readonly optionsGraphique = computed<ChartConfiguration<'line'>['options']>(() => {
    const mono = token('--sc-police-mono', 'monospace');
    const police = token('--sc-police', 'sans-serif');
    const secondaire = token('--sc-encre-secondaire', '#5e5a54');
    const filet = token('--sc-bordure', '#e2dfd9');
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },   // la courbe se lit à la verticale du pointeur
      plugins: {
        legend: { display: false },                        // une seule série : le titre au-dessus la nomme
        tooltip: {
          backgroundColor: token('--sc-encre', '#17222e'),
          titleFont: { family: mono, size: 12 },
          bodyFont: { family: mono, size: 12 },
          padding: 8,
          cornerRadius: 4,
          displayColors: false,
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { color: filet },
          ticks: { color: secondaire, font: { family: mono, size: 12 }, maxRotation: 0, autoSkipPadding: 24 },
        },
        y: {
          grid: { color: filet, lineWidth: 1 },
          border: { display: false },
          ticks: { color: secondaire, font: { family: mono, size: 12 }, padding: 8 },
          title: { display: false, font: { family: police } },
        },
      },
    };
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.charger(id);
      this.pageHistorique.set(0);
      this.chargerHistorique(id, 0);
    });

    // rafraîchissement des constantes toutes les 5 s (seulement les constantes, pas toute la fiche)
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

  protected valeurAlerte(valeur: number | null | undefined): string | null {
    return valeur == null ? null : formatNumber(valeur, this.locale, '1.0-1');
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
