import { Component, computed, input, output } from '@angular/core';
import { BadgeNiveau } from './badge-niveau';

const ANORMAUX = new Set(['CRITIQUE', 'ELEVEE']);

/**
 * « Moniteur » d'une constante vitale, à la manière d'un scope de chevet : grande valeur en monospace, unité,
 * mini-courbe des dernières mesures, heure de la mesure. La couleur n'apparaît que si le niveau calculé par
 * telemetrie est anormal (ÉLEVÉE, CRITIQUE), et toujours avec le badge du niveau.
 * C'est un bouton : il choisit la constante affichée dans la courbe détaillée (aria-pressed).
 */
@Component({
  selector: 'app-moniteur-constante',
  imports: [BadgeNiveau],
  template: `
    <button type="button" class="moniteur" [class]="'moniteur ' + ton()" [attr.aria-pressed]="selectionne()"
            [attr.data-constante]="type()" (click)="choisir.emit()">
      <span class="libelle">{{ libelle() }}</span>
      <span class="mesure">
        <!-- séparateur décimal resserré : en monospace, la virgule occuperait une case entière (« 146 , 5 ») -->
        <span class="valeur sc-mono">{{ partieEntiere() }}@if (partieDecimale(); as d) {<span class="sep">,</span>{{ d }}}</span>
        @if (valeur() != null) { <span class="unite">{{ unite() }}</span> }
      </span>
      <span class="trace" aria-hidden="true">
        @if (trace(); as t) {
          <svg viewBox="0 0 100 32" preserveAspectRatio="none">
            <polyline [attr.points]="t.points" />
          </svg>
          <span class="dernier" [style.top.%]="t.dernierY"></span>
        } @else {
          <span class="ligne-vide"></span>
        }
      </span>
      <span class="pied">
        <span class="heure sc-mono">{{ heure() ?? 'pas de mesure' }}</span>
        @if (anormal()) { <app-badge-niveau [valeur]="niveau()" /> }
      </span>
    </button>
  `,
  styleUrl: './moniteur-constante.scss',
})
export class MoniteurConstante {
  readonly type = input.required<string>();
  readonly libelle = input.required<string>();
  /** Valeur déjà formatée (locale fr-TN), ou null si aucune mesure. */
  readonly valeur = input<string | null>(null);
  readonly unite = input<string>('');
  readonly niveau = input<string | null | undefined>(null);
  /** Dernières valeurs, de la plus ancienne à la plus récente. */
  readonly serie = input<number[]>([]);
  readonly heure = input<string | null>(null);
  readonly selectionne = input(false);
  readonly choisir = output<void>();

  protected readonly partieEntiere = computed(() => (this.valeur() ?? '—').split(',')[0]);
  protected readonly partieDecimale = computed(() => (this.valeur() ?? '').split(',')[1] ?? null);
  protected readonly anormal = computed(() => ANORMAUX.has(this.niveau() ?? ''));
  protected readonly ton = computed(() => (this.anormal() ? (this.niveau() === 'CRITIQUE' ? 'critique' : 'elevee') : 'normal'));

  /** Points de la mini-courbe dans un repère 100 × 32 (marge de 3 en haut et en bas pour l'épaisseur du trait). */
  protected readonly trace = computed(() => {
    const s = this.serie();
    if (s.length < 2) {
      return null;
    }
    const min = Math.min(...s);
    const max = Math.max(...s);
    const etendue = max - min || 1;
    const y = (v: number) => 29 - ((v - min) / etendue) * 26;
    const points = s.map((v, i) => `${((i / (s.length - 1)) * 100).toFixed(2)},${y(v).toFixed(2)}`).join(' ');
    return { points, dernierY: (y(s[s.length - 1]) / 32) * 100 };
  });
}
