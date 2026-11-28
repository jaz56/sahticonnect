import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** État vide : ce qui manque, et (contenu projeté) l'action qui permet d'avancer. */
@Component({
  selector: 'app-etat-vide',
  template: `
    <div class="etat" role="status">
      <p class="message">{{ message() }}</p>
      @if (detail()) { <p class="detail">{{ detail() }}</p> }
      <div class="action"><ng-content /></div>
    </div>
  `,
  styles: `
    .etat {
      display: flex;
      flex-direction: column;
      gap: var(--sc-e-8);
      padding: var(--sc-e-24) 0;
    }
    .message { margin: 0; color: var(--sc-encre); }
    .detail { margin: 0; font-size: var(--sc-t-12); line-height: var(--sc-lh-12); color: var(--sc-encre-secondaire); }
    .action:empty { display: none; }
  `,
})
export class EtatVide {
  readonly message = input.required<string>();
  readonly detail = input<string>();
}

/** État d'erreur : ce qui a échoué et quoi faire, sans dramatiser (filet critique à gauche, pas de fond rouge). */
@Component({
  selector: 'app-etat-erreur',
  imports: [MatIconModule],
  template: `
    <div class="erreur" role="alert">
      <mat-icon aria-hidden="true">error</mat-icon>
      <div class="textes">
        <p class="message">{{ message() }}</p>
        @if (detail()) { <p class="detail sc-mono">{{ detail() }}</p> }
        <div class="action"><ng-content /></div>
      </div>
    </div>
  `,
  styles: `
    .erreur {
      display: flex;
      gap: var(--sc-e-12);
      padding: var(--sc-e-16) var(--sc-e-24);
      background: var(--sc-surface);
      border: var(--sc-trait) solid var(--sc-bordure);
      border-left: 3px solid var(--sc-critique);
      border-radius: var(--sc-rayon);
    }
    mat-icon { color: var(--sc-critique); flex: none; }
    .textes { display: flex; flex-direction: column; gap: var(--sc-e-8); min-width: 0; }
    .message { margin: 0; color: var(--sc-encre); font-weight: var(--sc-graisse-moyenne); }
    .detail { margin: 0; font-size: var(--sc-t-12); line-height: var(--sc-lh-12); color: var(--sc-encre-secondaire); overflow-wrap: anywhere; }
    .action:empty { display: none; }
  `,
})
export class EtatErreur {
  readonly message = input.required<string>();
  readonly detail = input<string>();
}
