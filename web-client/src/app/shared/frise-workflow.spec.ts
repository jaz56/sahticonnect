import { TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFrTn from '@angular/common/locales/fr-TN';
import { FriseWorkflow } from './frise-workflow';
import { EtatWorkflow, NiveauAlerte, NomEtape, StatutEtape, TypeEtape, Workflow } from '../api/orchestrateur';

function etape(nom: NomEtape, statut: StatutEtape, tentative = 1, message = ''): Workflow['etapes'][number] {
  return { nom, statut, tentative, dureeMs: 42, horodatage: '2026-11-24T14:30:00+01:00', message };
}

function creer(workflow: Workflow): HTMLElement {
  const fixture = TestBed.createComponent(FriseWorkflow);
  fixture.componentRef.setInput('workflow', workflow);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

const base: Workflow = {
  id: 'WF-2026-000001', alerteId: 'ALR-2026-000001', patientId: 'PAT-0001', niveau: NiveauAlerte.CRITIQUE,
  etat: EtatWorkflow.ECHEC, dateCreation: '2026-11-24T14:30:00+01:00', etapes: [],
};

describe('FriseWorkflow', () => {
  beforeEach(() => {
    registerLocaleData(localeFrTn);
    TestBed.configureTestingModule({ providers: [{ provide: LOCALE_ID, useValue: 'fr-TN' }] });
  });

  const statuts = (el: HTMLElement) => Array.from(el.querySelectorAll('[data-etape]')).map((c) => `${c.getAttribute('data-etape')}:${c.getAttribute('data-statut')}`);

  it('affiche les 5 étapes dans l\'ordre, avec statut, durée et message', () => {
    const el = creer({
      ...base,
      etat: EtatWorkflow.ECHEC,
      etapes: [
        etape(NomEtape.ALERTE_RECUE, StatutEtape.OK),
        etape(NomEtape.DROITS_VERIFIES, StatutEtape.ECHEC, 1, 'Service dossier-cnam indisponible'),
        etape(NomEtape.INTERVENTION_DECLENCHEE, StatutEtape.OK),
        etape(NomEtape.FICHE_PUBLIEE, StatutEtape.OK),
        etape(NomEtape.FACTUREE, StatutEtape.IGNOREE),
      ],
    });

    expect(statuts(el)).toEqual([
      'ALERTE_RECUE:OK', 'DROITS_VERIFIES:ECHEC', 'INTERVENTION_DECLENCHEE:OK', 'FICHE_PUBLIEE:OK', 'FACTUREE:IGNOREE',
    ]);
    expect(el.textContent).toContain('2. Droits vérifiés');
    expect(el.textContent).toContain('42 ms');
    expect(el.textContent).toContain('Service dossier-cnam indisponible');
  });

  it('relance : la DERNIÈRE exécution de chaque étape est affichée, avec sa tentative', () => {
    const el = creer({
      ...base,
      etat: EtatWorkflow.FACTUREE,
      etapes: [
        etape(NomEtape.ALERTE_RECUE, StatutEtape.OK),
        etape(NomEtape.DROITS_VERIFIES, StatutEtape.ECHEC),
        etape(NomEtape.DROITS_VERIFIES, StatutEtape.OK, 2, 'Couvert : APCI 100 %'),
      ],
    });

    expect(statuts(el).slice(0, 2)).toEqual(['ALERTE_RECUE:OK', 'DROITS_VERIFIES:OK']);
    expect(el.querySelector('[data-etape="DROITS_VERIFIES"]')!.textContent).toContain('tentative 2');
  });

  it('workflow en cours : l\'étape suivante est « en cours », les autres « à venir »', () => {
    const el = creer({ ...base, etat: EtatWorkflow.ALERTE_RECUE, enCours: true, etapes: [etape(NomEtape.ALERTE_RECUE, StatutEtape.OK)] });

    expect(statuts(el)).toEqual([
      'ALERTE_RECUE:OK', 'DROITS_VERIFIES:EN_COURS', 'INTERVENTION_DECLENCHEE:A_VENIR', 'FICHE_PUBLIEE:A_VENIR', 'FACTUREE:A_VENIR',
    ]);
  });

  // ===================== Saga d'escalade =====================

  it('workflow CRITIQUE avec escalade : l\'étape ESCALADE s\'insère avant l\'intervention', () => {
    const el = creer({
      ...base,
      etat: EtatWorkflow.FACTUREE,
      etapes: [
        etape(NomEtape.ALERTE_RECUE, StatutEtape.OK),
        etape(NomEtape.DROITS_VERIFIES, StatutEtape.OK),
        etape(NomEtape.ESCALADE, StatutEtape.OK, 1, 'WF-2026-000020 compensé'),
        etape(NomEtape.INTERVENTION_DECLENCHEE, StatutEtape.OK),
        etape(NomEtape.FICHE_PUBLIEE, StatutEtape.OK),
        etape(NomEtape.FACTUREE, StatutEtape.OK),
      ],
    });

    expect(statuts(el)).toEqual([
      'ALERTE_RECUE:OK', 'DROITS_VERIFIES:OK', 'ESCALADE:OK', 'INTERVENTION_DECLENCHEE:OK', 'FICHE_PUBLIEE:OK', 'FACTUREE:OK',
    ]);
    expect(el.querySelector('[data-etape="ESCALADE"]')!.textContent).toContain('3. Escalade');
    expect(el.querySelectorAll('[data-compensation]')).toHaveLength(0);
  });

  it('workflow ELEVEE compensé : ses étapes, puis les compensations dans l\'ordre inverse', () => {
    const compensation = (nom: NomEtape, message: string): Workflow['etapes'][number] =>
      ({ ...etape(nom, StatutEtape.OK, 1, message), type: TypeEtape.COMPENSATION });
    const el = creer({
      ...base,
      niveau: NiveauAlerte.ELEVEE,
      etat: EtatWorkflow.COMPENSE,
      compensePar: 'WF-2026-000021',
      etapes: [
        etape(NomEtape.ALERTE_RECUE, StatutEtape.OK),
        etape(NomEtape.DROITS_VERIFIES, StatutEtape.OK),
        etape(NomEtape.INTERVENTION_DECLENCHEE, StatutEtape.OK),
        etape(NomEtape.FICHE_PUBLIEE, StatutEtape.OK),
        etape(NomEtape.FACTUREE, StatutEtape.OK),
        compensation(NomEtape.FACTUREE, 'Feuille FS-2026-000002 ANNULEE'),
        compensation(NomEtape.FICHE_PUBLIEE, 'Intervention INT-2026-000004 COMPENSE'),
        compensation(NomEtape.INTERVENTION_DECLENCHEE, 'RDV RDV-2026-000043 annulé'),
      ],
    });

    // la frise principale ignore les compensations (FACTUREE reste OK : l'étape a bien eu lieu)
    expect(statuts(el)).toEqual([
      'ALERTE_RECUE:OK', 'DROITS_VERIFIES:OK', 'INTERVENTION_DECLENCHEE:OK', 'FICHE_PUBLIEE:OK', 'FACTUREE:OK',
    ]);
    const compensations = Array.from(el.querySelectorAll('[data-compensation]')).map((c) => c.getAttribute('data-compensation'));
    expect(compensations).toEqual(['FACTUREE', 'FICHE_PUBLIEE', 'INTERVENTION_DECLENCHEE']);
    expect(el.textContent).toContain('déclenchée par WF-2026-000021');
    expect(el.textContent).toContain('Feuille de soins annulée (SOAP)');
  });
});
