import { TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFrTn from '@angular/common/locales/fr-TN';
import { provideRouter } from '@angular/router';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GraphQLError } from 'graphql';
import FichePatient from './fiche-patient';
import { NotificationService } from '../../../core/notification.service';

const patient = {
  __typename: 'PatientSuivi',
  id: 'PAT-0002', nom: 'Trabelsi', prenom: 'Leila', dateNaissance: '1965-07-22', age: 61, service: 'Cardiologie',
  pathologie: 'Hypertension', niveauRisque: 'ELEVE', medecinReferent: 'Dr Ines Haddad',
  couvert: null, tauxRemboursement: null, // dossier-cnam en panne : champs distants à null
  constantes: [
    { __typename: 'ConstanteVitale', id: 'MES-1', type: 'TENSION_SYSTOLIQUE', valeur: 172, unite: 'mmHg', horodatage: '2026-11-24T14:30:00+01:00', niveau: 'ELEVEE' },
  ],
  alertes: [
    { __typename: 'Alerte', id: 'ALR-2026-900002', niveau: 'ELEVEE', statut: 'PRISE_EN_CHARGE', typeConstante: 'TENSION_SYSTOLIQUE',
      valeur: 172, seuilFranchi: 'Systolique ≥ 160', message: 'Alerte ELEVEE : tension systolique 172 mmHg', horodatage: '2026-11-24T09:30:00+01:00' },
  ],
  traitements: [
    { __typename: 'Traitement', id: 'TRT-004', medicament: 'Amlodipine 5 mg', posologie: '1 comprimé par jour', dateDebut: '2026-11-14', dateFin: '2027-02-12', prescripteur: 'Dr Ines Haddad' },
  ],
  interventions: [],
};

/** Apollo livre les résultats de façon asynchrone (promesses), hors de ce qu'attend whenStable() sans zone.js. */
async function reponsesApollo(fixture: { detectChanges(): void; whenStable(): Promise<unknown> }): Promise<void> {
  await new Promise((ok) => setTimeout(ok, 20));
  fixture.detectChanges();
  await fixture.whenStable();
}

describe('FichePatient (réponse GraphQL partielle)', () => {
  let apollo: ApolloTestingController;

  beforeEach(() => {
    registerLocaleData(localeFrTn);
    // jsdom ne dessine pas : Chart.js reçoit un contexte nul et n'affiche rien
    HTMLCanvasElement.prototype.getContext = (() => null) as never;
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'fr-TN' },
        { provide: NotificationService, useValue: { succes: () => {}, erreur: () => {} } },
      ],
    });
    apollo = TestBed.inject(ApolloTestingController);
  });

  it('affiche la fiche ET une puce « Service indisponible » à la place de la couverture CNAM', async () => {
    const fixture = TestBed.createComponent(FichePatient);
    fixture.componentRef.setInput('id', 'PAT-0002');
    fixture.detectChanges();

    const operation = apollo.expectOne('FichePatient');
    expect(operation.operation.variables).toEqual({ id: 'PAT-0002' });
    operation.flush({
      data: { patient },
      errors: [
        new GraphQLError('Service dossier-cnam indisponible : Connection refused', {
          path: ['patient', 'couvert'], extensions: { code: 'SERVICE_INDISPONIBLE', service: 'dossier-cnam' },
        }),
        new GraphQLError('Service dossier-cnam indisponible : Connection refused', {
          path: ['patient', 'tauxRemboursement'], extensions: { code: 'SERVICE_INDISPONIBLE', service: 'dossier-cnam' },
        }),
      ],
    });
    apollo.expectOne('HistoriqueSoins').flush({
      data: { historiqueSoins: null },
      errors: [new GraphQLError('Service dossier-cnam indisponible', {
        path: ['historiqueSoins'], extensions: { code: 'SERVICE_INDISPONIBLE', service: 'dossier-cnam' },
      })],
    });
    await reponsesApollo(fixture);

    const el = fixture.nativeElement as HTMLElement;
    // le reste de la page s'affiche normalement…
    expect(el.querySelector('h1')!.textContent).toContain('Leila Trabelsi');
    expect(el.textContent).toContain('Amlodipine 5 mg');
    expect(el.querySelector('[data-alerte="ALR-2026-900002"]')).not.toBeNull();
    expect(el.textContent).toContain('172');
    // …et seuls les champs en panne sont remplacés par la puce
    const puces = el.querySelectorAll('[data-testid="service-indisponible"]');
    expect(puces).toHaveLength(2); // couverture + historique de soins
    expect(el.querySelector('[data-testid="couverture"] [data-testid="service-indisponible"]')!.textContent)
      .toContain('Service indisponible (dossier-cnam)');
    expect(el.textContent).not.toContain('Non couvert');
  });

  it('réponse complète : couverture affichée, aucune puce', async () => {
    const fixture = TestBed.createComponent(FichePatient);
    fixture.componentRef.setInput('id', 'PAT-0002');
    fixture.detectChanges();

    apollo.expectOne('FichePatient').flush({ data: { patient: { ...patient, couvert: false, tauxRemboursement: 0 } } });
    apollo.expectOne('HistoriqueSoins').flush({
      data: { historiqueSoins: { __typename: 'PageHistoriqueSoins', page: 0, taille: 5, totalElements: 0, totalPages: 0, elements: [] } },
    });
    await reponsesApollo(fixture);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-testid="service-indisponible"]')).toHaveLength(0);
    expect(el.querySelector('[data-testid="couverture"]')!.textContent).toContain('Non couvert');
  });
});
