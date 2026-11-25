import { Component, computed, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Etape, Workflow } from '../api/orchestrateur';
import { ETAPES_WORKFLOW, LIBELLES_COMPENSATIONS, LIBELLES_ETAPES } from './libelles';

export interface CaseFrise {
  nom: string;
  libelle: string;
  /** OK, ECHEC, IGNOREE, EN_COURS (étape suivante d'un workflow en cours) ou A_VENIR. */
  statut: string;
  etape?: Etape;
}

/**
 * Frise des 5 étapes d'un workflow : statut, durée et message de la dernière exécution de chaque étape
 * (une étape relancée apparaît avec son numéro de tentative). Saga d'escalade : l'étape ESCALADE d'un workflow
 * CRITIQUE s'insère avant l'intervention ; les compensations d'un workflow ELEVEE forment une seconde ligne,
 * dans l'ordre où elles ont été exécutées (l'inverse des étapes d'origine).
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
    const executions = (wf.etapes ?? []).filter((e) => e.type !== 'COMPENSATION');
    const noms: string[] = [...ETAPES_WORKFLOW];
    if (executions.some((e) => e.nom === 'ESCALADE')) {
      noms.splice(2, 0, 'ESCALADE');
    }
    let suivanteMarquee = false;
    return noms.map((nom) => {
      // dernière exécution de l'étape (les relances ajoutent des étapes)
      const etape = [...executions].reverse().find((e) => e.nom === nom);
      let statut: string = etape?.statut ?? 'A_VENIR';
      if (!etape && wf.enCours && !suivanteMarquee) {
        statut = 'EN_COURS';
        suivanteMarquee = true;
      }
      return { nom, libelle: LIBELLES_ETAPES[nom], statut, etape };
    });
  });

  /** Compensations exécutées (saga), dans l'ordre d'exécution. */
  readonly compensations = computed<CaseFrise[]>(() =>
    (this.workflow().etapes ?? [])
      .filter((e) => e.type === 'COMPENSATION')
      .map((e) => ({ nom: e.nom, libelle: LIBELLES_COMPENSATIONS[e.nom] ?? e.nom, statut: e.statut, etape: e })),
  );

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
