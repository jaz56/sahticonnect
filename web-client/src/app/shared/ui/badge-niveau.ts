import { Component, computed, input } from '@angular/core';

/** Ton d'un badge : les couleurs de gravité sont réservées aux niveaux anormaux, à l'échec et au « à régulariser ». */
export type Ton = 'critique' | 'elevee' | 'neutre' | 'accent' | 'attenue';

const TONS: Record<string, Ton> = {
  CRITIQUE: 'critique',
  ECHEC: 'critique',
  ELEVEE: 'elevee',
  ELEVE: 'elevee',
  A_REGULARISER: 'elevee',
  EN_COURS: 'accent',
  COMPENSE: 'attenue',
  ANNULEE: 'attenue',
  ANNULE: 'attenue',
  IGNOREE: 'attenue',
};

const LIBELLES: Record<string, string> = {
  CRITIQUE: 'Critique',
  ELEVEE: 'Élevée',
  ELEVE: 'Élevé',
  MODERE: 'Modéré',
  FAIBLE: 'Faible',
  NORMAL: 'Normal',
  NOUVELLE: 'Nouvelle',
  PRISE_EN_CHARGE: 'Prise en charge',
  RESOLUE: 'Résolue',
  EN_ATTENTE: 'En attente',
  FACTUREE: 'Facturée',
  A_REGULARISER: 'À régulariser',
  ECHEC: 'Échec',
  OK: 'Réussie',
  IGNOREE: 'Ignorée',
  EN_COURS: 'En cours',
  A_VENIR: 'À venir',
  COMPENSE: 'Compensé',
  ANNULEE: 'Annulée',
  ANNULE: 'Annulé',
  ALERTE_RECUE: 'Alerte reçue',
  DROITS_VERIFIES: 'Droits vérifiés',
  INTERVENTION_DECLENCHEE: 'Intervention déclenchée',
  FICHE_PUBLIEE: 'Fiche publiée',
};

export function libelleNiveau(code: string | null | undefined): string {
  return code ? (LIBELLES[code] ?? code) : '—';
}

/**
 * Badge d'un niveau ou d'un statut : libellé en clair (la couleur n'est jamais la seule information) sur fond très
 * pâle, rayon 4 px. Une marque carrée précède les niveaux de gravité, pour les repérer d'un coup d'œil.
 */
@Component({
  selector: 'app-badge-niveau',
  template: `<span class="badge" [class]="'badge ' + ton()" [attr.data-niveau]="valeur()">{{ texte() }}</span>`,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: var(--sc-e-4);
      height: 24px;
      padding: 0 var(--sc-e-8);
      border-radius: var(--sc-rayon-badge);
      font-size: var(--sc-t-12);
      line-height: var(--sc-lh-12);
      font-weight: var(--sc-graisse-forte);
      white-space: nowrap;
      color: var(--sc-neutre);
      background: var(--sc-neutre-pale);
    }
    .critique { color: var(--sc-critique); background: var(--sc-critique-pale); }
    .elevee { color: var(--sc-elevee); background: var(--sc-elevee-pale); }
    .critique::before, .elevee::before {
      content: '';
      width: 6px;
      height: 6px;
      background: currentColor;
    }
    .accent { color: var(--sc-accent); background: var(--sc-accent-pale); }
    .attenue {
      color: var(--sc-encre-secondaire);
      background: transparent;
      box-shadow: inset 0 0 0 1px var(--sc-bordure);
    }
  `,
})
export class BadgeNiveau {
  readonly valeur = input.required<string | null | undefined>();
  /** Libellé imposé (sinon : libellé français du code). */
  readonly libelle = input<string>();

  protected readonly ton = computed<Ton>(() => TONS[this.valeur() ?? ''] ?? 'neutre');
  protected readonly texte = computed(() => this.libelle() ?? libelleNiveau(this.valeur()));
}
