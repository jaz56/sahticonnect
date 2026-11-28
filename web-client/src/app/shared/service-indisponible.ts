import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ChampIndisponible } from '../core/graphql/reponse-partielle';

/**
 * Puce affichée à la place d'un champ GraphQL resté vide dans une réponse partielle
 * (erreur SERVICE_INDISPONIBLE sur ce champ) : le reste de la page s'affiche normalement.
 * Neutre (ce n'est pas un niveau de gravité médicale) ; le message technique est dans l'infobulle.
 */
@Component({
  selector: 'app-service-indisponible',
  imports: [MatIconModule, MatTooltipModule],
  template: `
    <span class="indisponible" tabindex="0" [matTooltip]="champ().message" data-testid="service-indisponible">
      <mat-icon aria-hidden="true">cloud_off</mat-icon>
      Service indisponible ({{ champ().service }})
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .indisponible {
      display: inline-flex;
      align-items: center;
      gap: var(--sc-e-4);
      height: 24px;
      padding: 0 var(--sc-e-8);
      border-radius: var(--sc-rayon-badge);
      border: var(--sc-trait) solid var(--sc-bordure-forte);
      background: var(--sc-neutre-pale);
      color: var(--sc-encre-secondaire);
      font-size: var(--sc-t-12);
      line-height: var(--sc-lh-12);
      font-weight: var(--sc-graisse-moyenne);
      white-space: nowrap;
    }
    mat-icon { --sc-icone: 16px; font-size: 16px; }
  `,
})
export class ServiceIndisponible {
  readonly champ = input.required<ChampIndisponible>();
}
