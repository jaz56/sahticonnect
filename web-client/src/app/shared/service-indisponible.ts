import { Component, input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ChampIndisponible } from '../core/graphql/reponse-partielle';

/**
 * Puce affichée à la place d'un champ GraphQL resté vide dans une réponse partielle
 * (erreur SERVICE_INDISPONIBLE sur ce champ) : le reste de la page s'affiche normalement.
 */
@Component({
  selector: 'app-service-indisponible',
  imports: [MatChipsModule, MatIconModule, MatTooltipModule],
  template: `
    <mat-chip class="indisponible" [matTooltip]="champ().message" data-testid="service-indisponible">
      <mat-icon matChipAvatar>cloud_off</mat-icon>
      Service indisponible ({{ champ().service }})
    </mat-chip>
  `,
  styles: `
    .indisponible {
      --mat-chip-elevated-container-color: var(--sahti-elevee-fond);
      --mat-chip-label-text-color: var(--sahti-elevee);
      --mat-chip-with-avatar-avatar-color: var(--sahti-elevee);
    }
  `,
})
export class ServiceIndisponible {
  readonly champ = input.required<ChampIndisponible>();
}
