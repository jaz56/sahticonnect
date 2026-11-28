import { describe, expect, it } from 'vitest';
import { EtatWorkflow, Workflow } from '../../../api/orchestrateur';
import { fusionnerWorkflows, plusRecent } from './fusion-workflows';

function wf(id: string, etat: EtatWorkflow, miseAJour: string, patientId = 'PAT-0001'): Workflow {
  return {
    id, etat, patientId, dateCreation: '2026-11-28T13:45:35.236+01:00', dateMiseAJour: miseAJour,
  } as Workflow;
}

const tous = () => true;

describe('fusion des workflows (liste REST + flux SSE)', () => {
  it('réponse REST plus ancienne que l\'événement SSE : l\'état FACTUREE reçu par SSE est conservé', () => {
    // scénario du test e2e : FACTUREE arrive par SSE, puis la liste demandée avant (instantané à FICHE_PUBLIEE)
    const affiches = [wf('WF-2026-000058', EtatWorkflow.FACTUREE, '2026-11-28T13:45:35.723+01:00')];
    const liste = [wf('WF-2026-000058', EtatWorkflow.FICHE_PUBLIEE, '2026-11-28T13:45:35.589+01:00')];

    expect(fusionnerWorkflows(liste, affiches, tous)[0].etat).toBe(EtatWorkflow.FACTUREE);
  });

  it('réponse REST plus récente : elle remplace l\'affichage', () => {
    const affiches = [wf('WF-1', EtatWorkflow.FICHE_PUBLIEE, '2026-11-28T13:45:35.589+01:00')];
    const liste = [wf('WF-1', EtatWorkflow.FACTUREE, '2026-11-28T13:45:35.723+01:00')];

    expect(fusionnerWorkflows(liste, affiches, tous)[0].etat).toBe(EtatWorkflow.FACTUREE);
  });

  it('workflow reçu par SSE après l\'instantané : gardé s\'il passe les filtres, retiré sinon', () => {
    const affiches = [
      { ...wf('WF-NOUVEAU', EtatWorkflow.ALERTE_RECUE, '2026-11-28T13:50:00.000+01:00'), dateCreation: '2026-11-28T13:50:00.000+01:00' },
      wf('WF-AUTRE-PATIENT', EtatWorkflow.FACTUREE, '2026-11-28T13:45:40.000+01:00', 'PAT-0002'),
    ];
    const liste = [wf('WF-1', EtatWorkflow.FACTUREE, '2026-11-28T13:45:35.723+01:00')];

    const resultat = fusionnerWorkflows(liste, affiches, (w) => w.patientId === 'PAT-0001');

    expect(resultat.map((w) => w.id)).toEqual(['WF-NOUVEAU', 'WF-1']);
  });

  it('plusRecent : à égalité, la version affichée reste', () => {
    const affiche = wf('WF-1', EtatWorkflow.FACTUREE, '2026-11-28T13:45:35.723+01:00');
    const recu = wf('WF-1', EtatWorkflow.FICHE_PUBLIEE, '2026-11-28T13:45:35.723+01:00');

    expect(plusRecent(affiche, recu)).toBe(affiche);
  });
});
