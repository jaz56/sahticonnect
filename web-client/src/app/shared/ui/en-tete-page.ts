import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface ElementFil {
  libelle: string;
  /** Lien (absent pour une rubrique sans écran propre, ou pour la page courante). */
  lien?: string;
}

/**
 * En-tête commun d'un écran : fil d'Ariane, titre, sous-titre facultatif, actions alignées à droite
 * (contenu projeté [actions]). Le reste du contenu projeté s'affiche sous le titre (ex. : champs du dossier patient).
 */
@Component({
  selector: 'app-en-tete-page',
  imports: [RouterLink],
  template: `
    <header class="en-tete">
      @if (fil().length > 0) {
        <nav aria-label="Fil d'Ariane">
          <ol class="fil">
            @for (e of fil(); track $index; let dernier = $last) {
              <li>
                @if (e.lien && !dernier) {
                  <a [routerLink]="e.lien">{{ e.libelle }}</a>
                } @else {
                  <span [attr.aria-current]="dernier ? 'page' : null">{{ e.libelle }}</span>
                }
              </li>
            }
          </ol>
        </nav>
      }
      <div class="ligne-titre">
        <div class="titres">
          <h1>{{ titre() }}</h1>
          @if (sousTitre()) { <p class="sous-titre">{{ sousTitre() }}</p> }
        </div>
        <div class="actions"><ng-content select="[actions]" /></div>
      </div>
      <ng-content />
    </header>
  `,
  styles: `
    .en-tete { display: flex; flex-direction: column; gap: var(--sc-e-8); }
    .fil {
      display: flex;
      flex-wrap: wrap;
      gap: var(--sc-e-8);
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: var(--sc-t-12);
      line-height: var(--sc-lh-12);
      color: var(--sc-encre-secondaire);
    }
    .fil li + li::before { content: '/'; margin-right: var(--sc-e-8); color: var(--sc-encre-tertiaire); }
    .fil a { color: var(--sc-encre-secondaire); text-decoration: none; }
    .fil a:hover { color: var(--sc-accent); text-decoration: underline; }
    .fil [aria-current] { color: var(--sc-encre); }
    .ligne-titre {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--sc-e-16);
    }
    .titres { min-width: 0; display: flex; flex-direction: column; gap: var(--sc-e-4); }
    h1 {
      margin: 0;
      font-size: var(--sc-t-24);
      line-height: var(--sc-lh-24);
      font-weight: var(--sc-graisse-forte);
      letter-spacing: -0.01em;
      color: var(--sc-encre);
    }
    .sous-titre { margin: 0; max-width: 72ch; color: var(--sc-encre-secondaire); }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sc-e-12); }
    .actions:empty { display: none; }
  `,
})
export class EnTetePage {
  readonly fil = input<ElementFil[]>([]);
  readonly titre = input.required<string>();
  readonly sousTitre = input<string>();
}
