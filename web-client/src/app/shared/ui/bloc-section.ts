import { Component, input } from '@angular/core';

let compteur = 0;

/**
 * Bloc de contenu : titre suivi d'un filet fin, actions à droite (contenu projeté [actions]), marge interne de 24 px.
 * Surface blanche bordée de 1 px, sans ombre. La section est nommée par son titre (aria-labelledby).
 */
@Component({
  selector: 'app-bloc-section',
  template: `
    <section class="bloc" [attr.aria-labelledby]="idTitre">
      <div class="tete">
        <div class="titres">
          <h2 [id]="idTitre">{{ titre() }}</h2>
          @if (sousTitre()) { <p>{{ sousTitre() }}</p> }
        </div>
        <div class="actions"><ng-content select="[actions]" /></div>
      </div>
      <div class="corps"><ng-content /></div>
    </section>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .bloc {
      background: var(--sc-surface);
      border: var(--sc-trait) solid var(--sc-bordure);
      border-radius: var(--sc-rayon);
      padding: var(--sc-e-24);
    }
    .tete {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--sc-e-12) var(--sc-e-16);
      padding-bottom: var(--sc-e-12);
      margin-bottom: var(--sc-e-16);
      border-bottom: var(--sc-trait) solid var(--sc-bordure);
    }
    .titres { display: flex; flex-direction: column; gap: var(--sc-e-4); min-width: 0; }
    h2 {
      margin: 0;
      font-size: var(--sc-t-16);
      line-height: var(--sc-lh-16);
      font-weight: var(--sc-graisse-forte);
      color: var(--sc-encre);
    }
    p {
      margin: 0;
      font-size: var(--sc-t-12);
      line-height: var(--sc-lh-12);
      color: var(--sc-encre-secondaire);
    }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sc-e-12); }
    .actions:empty { display: none; }
  `,
})
export class BlocSection {
  readonly titre = input.required<string>();
  readonly sousTitre = input<string>();

  protected readonly idTitre = `bloc-${++compteur}`;
}
