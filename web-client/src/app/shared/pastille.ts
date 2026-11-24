import { Component, computed, input } from '@angular/core';

const LIBELLES: Record<string, string> = {
  CRITIQUE: 'Critique',
  ELEVE: 'Élevé',
  ELEVEE: 'Élevée',
  MODERE: 'Modéré',
  FAIBLE: 'Faible',
  NOUVELLE: 'Nouvelle',
  PRISE_EN_CHARGE: 'Prise en charge',
  RESOLUE: 'Résolue',
  EN_ATTENTE: 'En attente',
  FACTUREE: 'Facturée',
  A_REGULARISER: 'À régulariser',
  ECHEC: 'Échec',
};

/** Pastille colorée d'un niveau (risque, alerte) ou d'un statut ; la couleur suit le code (styles.scss). */
@Component({
  selector: 'app-pastille',
  template: `<span class="pastille" [class]="valeur()" [attr.data-niveau]="valeur()">{{ texte() }}</span>`,
  styles: `.pastille { vertical-align: middle; }`,
})
export class Pastille {
  readonly valeur = input.required<string | null | undefined>();
  /** Libellé imposé (sinon : libellé français du code). */
  readonly libelle = input<string>();

  protected readonly texte = computed(() => this.libelle() ?? LIBELLES[this.valeur() ?? ''] ?? this.valeur() ?? '—');
}
