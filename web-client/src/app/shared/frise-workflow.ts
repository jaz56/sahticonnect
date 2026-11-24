import { Component, computed, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Etape, Workflow } from '../api/orchestrateur';
import { ETAPES_WORKFLOW, LIBELLES_ETAPES } from './libelles';

export interface CaseFrise {
  nom: string;
  libelle: string;
  /** OK, ECHEC, IGNOREE, EN_COURS (étape suivante d'un workflow en cours) ou A_VENIR. */
  statut: string;
  etape?: Etape;
}

/**
 * Frise des 5 étapes d'un workflow : statut, durée et message de la dernière exécution de chaque étape
 * (une étape relancée apparaît avec son numéro de tentative).
 */
@Component({
  selector: 'app-frise-workflow',
  imports: [MatIconModule, MatTooltipModule, DatePipe, DecimalPipe],
  templateUrl: './frise-workflow.html',
  styleUrl: './frise-workflow.scss',
})
export class FriseWorkflow {
  readonly workflow = input.required<Workflow>();

  readonly cases = computed<CaseFrise[]>(() => {
    const wf = this.workflow();
    let suivanteMarquee = false;
    return ETAPES_WORKFLOW.map((nom) => {
      // dernière exécution de l'étape (les relances ajoutent des étapes)
      const etape = [...(wf.etapes ?? [])].reverse().find((e) => e.nom === nom);
      let statut: string = etape?.statut ?? 'A_VENIR';
      if (!etape && wf.enCours && !suivanteMarquee) {
        statut = 'EN_COURS';
        suivanteMarquee = true;
      }
      return { nom, libelle: LIBELLES_ETAPES[nom], statut, etape };
    });
  });

  protected icone(statut: string): string {
    switch (statut) {
      case 'OK': return 'check_circle';
      case 'ECHEC': return 'error';
      case 'IGNOREE': return 'do_not_disturb_on';
      case 'EN_COURS': return 'progress_activity';
      default: return 'radio_button_unchecked';
    }
  }
}
