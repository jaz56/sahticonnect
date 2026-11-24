import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { CnamSoapService } from './cnam-soap.service';
import { SANS_NOTIFICATION } from '../erreurs';

const URL_CNAM = 'http://localhost:8080/api/cnam/ws';
const NS = 'xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"';

function reponse(corps: string): string {
  return `<SOAP-ENV:Envelope ${NS}><SOAP-ENV:Header/><SOAP-ENV:Body>${corps}</SOAP-ENV:Body></SOAP-ENV:Envelope>`;
}

describe('CnamSoapService', () => {
  let service: CnamSoapService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CnamSoapService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ===================== Construction des enveloppes =====================

  it('verifierDroitsCNAM : enveloppe SOAP 1.1, éléments dans l\'ordre du XSD, via la gateway', async () => {
    const appel = firstValueFrom(service.verifierDroitsCNAM({
      identifiantPatient: 'PAT-0001',
      codeVignetteCNAM: 'VG-2026-000001',
      acte: { lettreCle: 'C', coefficient: 1, libelle: 'Consultation & suivi' },
    }));

    const requete = http.expectOne(URL_CNAM);
    expect(requete.request.method).toBe('POST');
    expect(requete.request.headers.get('Content-Type')).toBe('text/xml; charset=utf-8');
    expect(requete.request.context.get(SANS_NOTIFICATION)).toBe(true);
    const xml = requete.request.body as string;
    expect(xml).toContain('<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cnam="http://sahticonnect.tn/cnam">');
    expect(xml).toContain(
      '<cnam:verifierDroitsCNAMRequest><cnam:identifiantPatient>PAT-0001</cnam:identifiantPatient>'
      + '<cnam:codeVignetteCNAM>VG-2026-000001</cnam:codeVignetteCNAM><cnam:acte><cnam:lettreCle>C</cnam:lettreCle>'
      + '<cnam:coefficient>1</cnam:coefficient><cnam:libelle>Consultation &amp; suivi</cnam:libelle></cnam:acte>'
      + '</cnam:verifierDroitsCNAMRequest>');
    expect(xml).not.toContain('dateActe'); // élément facultatif absent

    requete.flush(reponse(`<ns2:verifierDroitsCNAMResponse xmlns:ns2="http://sahticonnect.tn/cnam">
      <ns2:assure><ns2:identifiantPatient>PAT-0001</ns2:identifiantPatient><ns2:nom>Ben Salah</ns2:nom><ns2:prenom>Ahmed</ns2:prenom>
        <ns2:dateNaissance>1958-03-14</ns2:dateNaissance><ns2:matriculeCNAM>CNAM-1958-0001</ns2:matriculeCNAM>
        <ns2:codeVignetteCNAM>VG-2026-000001</ns2:codeVignetteCNAM><ns2:filiere>PUBLIQUE</ns2:filiere>
        <ns2:dateFinValidite>2027-12-31</ns2:dateFinValidite><ns2:apci>true</ns2:apci><ns2:codeAPCI>APCI-IC-0001</ns2:codeAPCI></ns2:assure>
      <ns2:couverture><ns2:couvert>true</ns2:couvert><ns2:tauxRemboursement>100</ns2:tauxRemboursement><ns2:motif>APCI 100 %</ns2:motif></ns2:couverture>
      <ns2:montantEstime>45.000</ns2:montantEstime><ns2:partCNAMEstimee>45.000</ns2:partCNAMEstimee><ns2:partPatientEstimee>0.000</ns2:partPatientEstimee>
    </ns2:verifierDroitsCNAMResponse>`));

    const resultat = await appel;
    expect(resultat.fault).toBeUndefined();
    expect(resultat.statutHttp).toBe(200);
    expect(resultat.resultat).toEqual({
      assure: expect.objectContaining({ identifiantPatient: 'PAT-0001', nom: 'Ben Salah', apci: true, codeAPCI: 'APCI-IC-0001', filiere: 'PUBLIQUE' }),
      couverture: { couvert: true, tauxRemboursement: 100, motif: 'APCI 100 %' },
      montantEstime: 45,
      partCNAMEstimee: 45,
      partPatientEstimee: 0,
    });
    expect(resultat.reponseXml).toContain('verifierDroitsCNAMResponse');
  });

  it('emettreFeuilleDeSoins : plusieurs actes répétés, lignes et montants relus', async () => {
    const appel = firstValueFrom(service.emettreFeuilleDeSoins({
      identifiantPatient: 'PAT-0003', codeVignetteCNAM: 'VG-2026-000003', dateSoins: '2026-11-22',
      etablissement: 'Hôpital Abderrahmen Mami', medecin: 'Dr Sami Kallel',
      actes: [{ lettreCle: 'CS', coefficient: 1 }, { lettreCle: 'K', coefficient: 10 }],
      referenceIntervention: 'INT-2026-000001',
    }));

    const requete = http.expectOne(URL_CNAM);
    const xml = requete.request.body as string;
    expect(xml).toMatch(/<cnam:medecin>Dr Sami Kallel<\/cnam:medecin><cnam:actes>.*CS.*<\/cnam:actes><cnam:actes>.*K.*10.*<\/cnam:actes><cnam:referenceIntervention>INT-2026-000001/);
    requete.flush(reponse(`<ns2:emettreFeuilleDeSoinsResponse xmlns:ns2="http://sahticonnect.tn/cnam"><ns2:feuilleDeSoins>
      <ns2:numero>FS-2026-000007</ns2:numero><ns2:identifiantPatient>PAT-0003</ns2:identifiantPatient><ns2:dateSoins>2026-11-22</ns2:dateSoins>
      <ns2:etablissement>Hôpital Abderrahmen Mami</ns2:etablissement><ns2:medecin>Dr Sami Kallel</ns2:medecin>
      <ns2:lignes><ns2:acte><ns2:lettreCle>CS</ns2:lettreCle><ns2:coefficient>1</ns2:coefficient></ns2:acte><ns2:montant>60.000</ns2:montant></ns2:lignes>
      <ns2:lignes><ns2:acte><ns2:lettreCle>K</ns2:lettreCle><ns2:coefficient>10</ns2:coefficient></ns2:acte><ns2:montant>30.000</ns2:montant></ns2:lignes>
      <ns2:montantTotal>90.000</ns2:montantTotal><ns2:tauxApplique>80</ns2:tauxApplique><ns2:partCNAM>72.000</ns2:partCNAM>
      <ns2:partPatient>18.000</ns2:partPatient><ns2:statut>CERTIFIEE</ns2:statut><ns2:codeCertification>abc123</ns2:codeCertification>
    </ns2:feuilleDeSoins></ns2:emettreFeuilleDeSoinsResponse>`));

    const feuille = (await appel).resultat!;
    expect(feuille.numero).toBe('FS-2026-000007');
    expect(feuille.lignes).toEqual([
      { acte: { lettreCle: 'CS', coefficient: 1, libelle: undefined }, montant: 60 },
      { acte: { lettreCle: 'K', coefficient: 10, libelle: undefined }, montant: 30 },
    ]);
    expect([feuille.montantTotal, feuille.partCNAM, feuille.partPatient, feuille.statut]).toEqual([90, 72, 18, 'CERTIFIEE']);
    expect(feuille.referenceIntervention).toBeUndefined();
  });

  it('consulterHistorique : entrées répétées ; comparerGaranties : deux offres', async () => {
    const historique = firstValueFrom(service.consulterHistorique({ identifiantPatient: 'PAT-0004' }));
    const r1 = http.expectOne(URL_CNAM);
    expect(r1.request.body).not.toContain('dateDebut');
    r1.flush(reponse(`<ns2:consulterHistoriqueResponse xmlns:ns2="http://sahticonnect.tn/cnam">
      <ns2:assure><ns2:identifiantPatient>PAT-0004</ns2:identifiantPatient><ns2:nom>Mansour</ns2:nom></ns2:assure>
      <ns2:entrees><ns2:date>2026-10-04</ns2:date><ns2:etablissement>INN</ns2:etablissement><ns2:diagnostic>Fond d'œil</ns2:diagnostic>
        <ns2:actes><ns2:lettreCle>CS</ns2:lettreCle><ns2:coefficient>1.000</ns2:coefficient></ns2:actes>
        <ns2:actes><ns2:lettreCle>K</ns2:lettreCle><ns2:coefficient>10.000</ns2:coefficient></ns2:actes></ns2:entrees>
      <ns2:entrees><ns2:date>2026-06-21</ns2:date><ns2:etablissement>Cabinet</ns2:etablissement><ns2:diagnostic>Suivi</ns2:diagnostic>
        <ns2:actes><ns2:lettreCle>C</ns2:lettreCle><ns2:coefficient>1.000</ns2:coefficient></ns2:actes><ns2:numeroFeuilleSoins>FS-2026-900002</ns2:numeroFeuilleSoins></ns2:entrees>
    </ns2:consulterHistoriqueResponse>`));
    const h = (await historique).resultat!;
    expect(h.entrees).toHaveLength(2);
    expect(h.entrees[0].actes.map((a) => `${a.lettreCle} ${a.coefficient}`)).toEqual(['CS 1', 'K 10']);
    expect(h.entrees[1].numeroFeuilleSoins).toBe('FS-2026-900002');

    const comparaison = firstValueFrom(service.comparerGaranties({
      identifiantPatient: 'PAT-0004',
      priseEnChargeA: { libelle: 'Hôpital public', filiere: 'PUBLIQUE', actes: [{ lettreCle: 'C', coefficient: 1 }] },
      priseEnChargeB: { libelle: 'Clinique privée', filiere: 'PRIVEE', actes: [{ lettreCle: 'C', coefficient: 1 }] },
    }));
    const r2 = http.expectOne(URL_CNAM);
    expect(r2.request.body).toContain('<cnam:priseEnChargeA><cnam:libelle>Hôpital public</cnam:libelle><cnam:filiere>PUBLIQUE</cnam:filiere><cnam:actes>');
    r2.flush(reponse(`<ns2:comparerGarantiesResponse xmlns:ns2="http://sahticonnect.tn/cnam">
      <ns2:resultatA><ns2:libelle>Hôpital public</ns2:libelle><ns2:montantTotal>51.000</ns2:montantTotal><ns2:tauxApplique>80</ns2:tauxApplique><ns2:partCNAM>40.800</ns2:partCNAM><ns2:partPatient>10.200</ns2:partPatient></ns2:resultatA>
      <ns2:resultatB><ns2:libelle>Clinique privée</ns2:libelle><ns2:montantTotal>51.000</ns2:montantTotal><ns2:tauxApplique>50</ns2:tauxApplique><ns2:partCNAM>25.500</ns2:partCNAM><ns2:partPatient>25.500</ns2:partPatient></ns2:resultatB>
      <ns2:differencePartPatient>15.300</ns2:differencePartPatient><ns2:recommandation>Hôpital public</ns2:recommandation>
    </ns2:comparerGarantiesResponse>`));
    const c = (await comparaison).resultat!;
    expect([c.resultatA.partPatient, c.resultatB.partPatient, c.differencePartPatient, c.recommandation]).toEqual([10.2, 25.5, 15.3, 'Hôpital public']);
  });

  // ===================== SOAP Faults =====================

  it('SOAP Fault Client (HTTP 500) : faultcode, faultstring et code métier du <detail>', async () => {
    const appel = firstValueFrom(service.verifierDroitsCNAM({
      identifiantPatient: 'PAT-0001', codeVignetteCNAM: 'VG-2026-000003', acte: { lettreCle: 'C', coefficient: 1 },
    }));
    http.expectOne(URL_CNAM).flush(reponse(`<SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode>
      <faultstring xml:lang="fr">La vignette VG-2026-000003 ne correspond pas au patient PAT-0001</faultstring>
      <detail><ns2:verifierDroitsCNAMFault xmlns:ns2="http://sahticonnect.tn/cnam"><ns2:code>VIGNETTE_INVALIDE</ns2:code>
        <ns2:message>La vignette VG-2026-000003 ne correspond pas au patient PAT-0001</ns2:message></ns2:verifierDroitsCNAMFault></detail>
    </SOAP-ENV:Fault>`), { status: 500, statusText: 'Internal Server Error' });

    const resultat = await appel;
    expect(resultat.resultat).toBeUndefined();
    expect(resultat.statutHttp).toBe(500);
    expect(resultat.fault).toEqual({
      faultcode: 'Client',
      faultstring: 'La vignette VG-2026-000003 ne correspond pas au patient PAT-0001',
      code: 'VIGNETTE_INVALIDE',
      detail: 'La vignette VG-2026-000003 ne correspond pas au patient PAT-0001',
    });
  });

  it('Fault Server du fallback de la gateway (CNAM arrêté) : code SERVICE_INDISPONIBLE et service', async () => {
    const appel = firstValueFrom(service.consulterHistorique({ identifiantPatient: 'PAT-0001' }));
    http.expectOne(URL_CNAM).flush(reponse(`<SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode>
      <faultstring xml:lang="fr">Service dossier-cnam indisponible : connection refused</faultstring><faultactor>api-gateway</faultactor>
      <detail><gw:erreurGateway xmlns:gw="http://sahticonnect.tn/gateway"><gw:code>SERVICE_INDISPONIBLE</gw:code>
        <gw:service>dossier-cnam</gw:service></gw:erreurGateway></detail></SOAP-ENV:Fault>`), { status: 500, statusText: 'Internal Server Error' });

    expect((await appel).fault).toEqual(expect.objectContaining({ faultcode: 'Server', code: 'SERVICE_INDISPONIBLE', detail: 'dossier-cnam' }));
  });

  it('gateway injoignable (statut 0, pas d\'enveloppe) : fault « Transport »', async () => {
    const appel = firstValueFrom(service.consulterHistorique({ identifiantPatient: 'PAT-0001' }));
    http.expectOne(URL_CNAM).error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect((await appel).fault).toEqual(expect.objectContaining({ faultcode: 'Transport', code: 'GATEWAY_INJOIGNABLE' }));
  });
});
