import { Component, input } from '@angular/core';

/**
 * Valeur chiffrée ou identifiant en monospace (chiffres tabulaires, zéro barré), avec son unité éventuelle en texte
 * secondaire. Valeur absente : tiret cadratin.
 */
@Component({
  selector: 'app-valeur-mono',
  template: `<span class="sc-mono valeur">{{ valeur() ?? '—' }}</span>@if (unite() && valeur() != null) {<span class="unite">{{ unite() }}</span>}`,
  styles: `
    :host { display: inline-flex; align-items: baseline; gap: var(--sc-e-4); white-space: nowrap; }
    .unite { font-size: var(--sc-t-12); line-height: var(--sc-lh-12); color: var(--sc-encre-tertiaire); }
  `,
})
export class ValeurMono {
  readonly valeur = input<string | number | null | undefined>();
  readonly unite = input<string | null | undefined>();
}
