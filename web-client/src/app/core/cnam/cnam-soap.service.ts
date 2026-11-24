import { HttpClient, HttpContext, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SANS_NOTIFICATION } from '../erreurs';
import {
  ActeCote,
  Assure,
  ComparerGarantiesReponse,
  ComparerGarantiesRequete,
  ConsulterHistoriqueReponse,
  ConsulterHistoriqueRequete,
  EmettreFeuilleDeSoinsRequete,
  EntreeHistorique,
  FaultSoap,
  FeuilleDeSoins,
  GarantieCalculee,
  NAMESPACE_CNAM,
  PriseEnCharge,
  ResultatSoap,
  VerifierDroitsCnamReponse,
  VerifierDroitsCnamRequete,
} from './cnam.types';

const NAMESPACE_SOAP = 'http://schemas.xmlsoap.org/soap/envelope/';

/**
 * Client SOAP du service dossier-cnam, via la gateway (http://localhost:8080/api/cnam/ws).
 * Le navigateur n'a pas de pile SOAP : les enveloppes XML sont construites ici (ordre des xs:sequence du XSD)
 * et les réponses lues avec DOMParser, y compris les SOAP Faults (code métier du <detail>).
 */
@Injectable({ providedIn: 'root' })
export class CnamSoapService {
  private readonly http = inject(HttpClient);

  verifierDroitsCNAM(requete: VerifierDroitsCnamRequete): Observable<ResultatSoap<VerifierDroitsCnamReponse>> {
    const corps =
      element('identifiantPatient', requete.identifiantPatient) +
      element('codeVignetteCNAM', requete.codeVignetteCNAM) +
      `<cnam:acte>${acte(requete.acte)}</cnam:acte>` +
      element('dateActe', requete.dateActe);
    return this.appeler('verifierDroitsCNAM', corps, (reponse) => ({
      assure: lireAssure(enfant(reponse, 'assure')),
      couverture: {
        couvert: booleen(enfant(reponse, 'couverture'), 'couvert'),
        tauxRemboursement: nombre(enfant(reponse, 'couverture'), 'tauxRemboursement'),
        motif: texte(enfant(reponse, 'couverture'), 'motif'),
      },
      montantEstime: nombre(reponse, 'montantEstime'),
      partCNAMEstimee: nombre(reponse, 'partCNAMEstimee'),
      partPatientEstimee: nombre(reponse, 'partPatientEstimee'),
    }));
  }

  emettreFeuilleDeSoins(requete: EmettreFeuilleDeSoinsRequete): Observable<ResultatSoap<FeuilleDeSoins>> {
    const corps =
      element('identifiantPatient', requete.identifiantPatient) +
      element('codeVignetteCNAM', requete.codeVignetteCNAM) +
      element('dateSoins', requete.dateSoins) +
      element('etablissement', requete.etablissement) +
      element('medecin', requete.medecin) +
      requete.actes.map((a) => `<cnam:actes>${acte(a)}</cnam:actes>`).join('') +
      element('referenceIntervention', requete.referenceIntervention);
    return this.appeler('emettreFeuilleDeSoins', corps, (reponse) => lireFeuille(enfant(reponse, 'feuilleDeSoins')));
  }

  consulterHistorique(requete: ConsulterHistoriqueRequete): Observable<ResultatSoap<ConsulterHistoriqueReponse>> {
    const corps =
      element('identifiantPatient', requete.identifiantPatient) +
      element('dateDebut', requete.dateDebut) +
      element('dateFin', requete.dateFin);
    return this.appeler('consulterHistorique', corps, (reponse) => ({
      assure: lireAssure(enfant(reponse, 'assure')),
      entrees: enfants(reponse, 'entrees').map(lireEntree),
    }));
  }

  comparerGaranties(requete: ComparerGarantiesRequete): Observable<ResultatSoap<ComparerGarantiesReponse>> {
    const corps =
      element('identifiantPatient', requete.identifiantPatient) +
      `<cnam:priseEnChargeA>${priseEnCharge(requete.priseEnChargeA)}</cnam:priseEnChargeA>` +
      `<cnam:priseEnChargeB>${priseEnCharge(requete.priseEnChargeB)}</cnam:priseEnChargeB>`;
    return this.appeler('comparerGaranties', corps, (reponse) => ({
      resultatA: lireGarantie(enfant(reponse, 'resultatA')),
      resultatB: lireGarantie(enfant(reponse, 'resultatB')),
      differencePartPatient: nombre(reponse, 'differencePartPatient'),
      recommandation: texte(reponse, 'recommandation') ?? '',
    }));
  }

  /** Envoie l'enveloppe xxxRequest et lit xxxResponse, ou la SOAP Fault (HTTP 500), ou une erreur de transport. */
  private appeler<T>(operation: string, corps: string, lire: (reponse: Element) => T): Observable<ResultatSoap<T>> {
    const requeteXml = enveloppe(`<cnam:${operation}Request>${corps}</cnam:${operation}Request>`);
    return this.http
      .post(environment.cnamUrl, requeteXml, {
        headers: new HttpHeaders({ 'Content-Type': 'text/xml; charset=utf-8' }),
        responseType: 'text',
        observe: 'response',
        // la Fault est affichée dans l'écran CNAM, pas dans un snackbar
        context: new HttpContext().set(SANS_NOTIFICATION, true),
      })
      .pipe(
        map((reponse) => lireReponse(requeteXml, reponse.body ?? '', reponse.status, operation, lire)),
        catchError((erreur: HttpErrorResponse) => {
          const reponseXml = typeof erreur.error === 'string' ? erreur.error : '';
          return of(lireReponse(requeteXml, reponseXml, erreur.status, operation, lire));
        }),
      );
  }
}

// ===================== Construction des enveloppes =====================

export function enveloppe(contenuBody: string): string {
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    `<soapenv:Envelope xmlns:soapenv="${NAMESPACE_SOAP}" xmlns:cnam="${NAMESPACE_CNAM}">` +
    `<soapenv:Header/><soapenv:Body>${contenuBody}</soapenv:Body></soapenv:Envelope>`
  );
}

/** Élément simple ; absent si la valeur est vide (éléments minOccurs="0"). */
function element(nom: string, valeur: string | number | undefined | null): string {
  return valeur === undefined || valeur === null || valeur === '' ? '' : `<cnam:${nom}>${echapper(String(valeur))}</cnam:${nom}>`;
}

function acte(a: ActeCote): string {
  return element('lettreCle', a.lettreCle) + element('coefficient', a.coefficient) + element('libelle', a.libelle);
}

function priseEnCharge(p: PriseEnCharge): string {
  return element('libelle', p.libelle) + element('filiere', p.filiere) + p.actes.map((a) => `<cnam:actes>${acte(a)}</cnam:actes>`).join('');
}

export function echapper(texte: string): string {
  return texte.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

// ===================== Lecture des réponses =====================

export function lireReponse<T>(
  requeteXml: string,
  reponseXml: string,
  statutHttp: number,
  operation: string,
  lire: (reponse: Element) => T,
): ResultatSoap<T> {
  const base = { requeteXml, reponseXml, statutHttp };
  const document = reponseXml ? new DOMParser().parseFromString(reponseXml, 'text/xml') : null;
  if (!document || document.getElementsByTagName('parsererror').length > 0) {
    return { ...base, fault: transport(statutHttp) };
  }
  const fault = document.getElementsByTagNameNS(NAMESPACE_SOAP, 'Fault')[0];
  if (fault) {
    return { ...base, fault: lireFault(fault) };
  }
  const reponse = document.getElementsByTagNameNS(NAMESPACE_CNAM, `${operation}Response`)[0];
  if (!reponse) {
    return { ...base, fault: { faultcode: 'Client', faultstring: `Réponse ${operation}Response absente` } };
  }
  return { ...base, resultat: lire(reponse) };
}

/** faultcode / faultstring (sans namespace, SOAP 1.1), puis le code métier du premier élément de <detail>. */
export function lireFault(fault: Element): FaultSoap {
  const enfantSimple = (nom: string) => Array.from(fault.children).find((e) => e.localName === nom);
  const faultcode = (enfantSimple('faultcode')?.textContent ?? '').trim();
  const detail = enfantSimple('detail')?.firstElementChild ?? null;
  const champDetail = (nom: string) =>
    detail ? Array.from(detail.children).find((e) => e.localName === nom)?.textContent?.trim() : undefined;
  return {
    faultcode: faultcode.includes(':') ? faultcode.split(':')[1] : faultcode,
    faultstring: (enfantSimple('faultstring')?.textContent ?? '').trim(),
    code: champDetail('code'),
    detail: champDetail('message') ?? champDetail('service'),
  };
}

function transport(statutHttp: number): FaultSoap {
  return {
    faultcode: 'Transport',
    faultstring: statutHttp === 0 ? "L'API Gateway (http://localhost:8080) ne répond pas" : `Réponse HTTP ${statutHttp} sans enveloppe SOAP`,
    code: statutHttp === 0 ? 'GATEWAY_INJOIGNABLE' : `HTTP_${statutHttp}`,
  };
}

/** Enfants directs portant ce nom local dans le namespace CNAM (getElementsByTagNameNS descendrait trop loin). */
function enfants(parent: Element | null, nom: string): Element[] {
  return parent ? Array.from(parent.children).filter((e) => e.localName === nom && e.namespaceURI === NAMESPACE_CNAM) : [];
}

function enfant(parent: Element | null, nom: string): Element | null {
  return enfants(parent, nom)[0] ?? null;
}

function texte(parent: Element | null, nom: string): string | undefined {
  const e = enfant(parent, nom);
  return e ? (e.textContent ?? '').trim() : undefined;
}

function nombre(parent: Element | null, nom: string): number {
  return Number(texte(parent, nom) ?? Number.NaN);
}

function booleen(parent: Element | null, nom: string): boolean {
  return texte(parent, nom) === 'true';
}

function lireActe(e: Element): ActeCote {
  return { lettreCle: texte(e, 'lettreCle') as ActeCote['lettreCle'], coefficient: nombre(e, 'coefficient'), libelle: texte(e, 'libelle') };
}

function lireAssure(e: Element | null): Assure {
  return {
    identifiantPatient: texte(e, 'identifiantPatient') ?? '',
    nom: texte(e, 'nom') ?? '',
    prenom: texte(e, 'prenom') ?? '',
    dateNaissance: texte(e, 'dateNaissance') ?? '',
    matriculeCNAM: texte(e, 'matriculeCNAM') ?? '',
    codeVignetteCNAM: texte(e, 'codeVignetteCNAM') ?? '',
    filiere: texte(e, 'filiere') as Assure['filiere'],
    dateFinValidite: texte(e, 'dateFinValidite') ?? '',
    apci: booleen(e, 'apci'),
    codeAPCI: texte(e, 'codeAPCI'),
  };
}

function lireFeuille(e: Element | null): FeuilleDeSoins {
  return {
    numero: texte(e, 'numero') ?? '',
    identifiantPatient: texte(e, 'identifiantPatient') ?? '',
    dateSoins: texte(e, 'dateSoins') ?? '',
    etablissement: texte(e, 'etablissement') ?? '',
    medecin: texte(e, 'medecin') ?? '',
    lignes: enfants(e, 'lignes').map((l) => ({ acte: lireActe(enfant(l, 'acte')!), montant: nombre(l, 'montant') })),
    montantTotal: nombre(e, 'montantTotal'),
    tauxApplique: nombre(e, 'tauxApplique'),
    partCNAM: nombre(e, 'partCNAM'),
    partPatient: nombre(e, 'partPatient'),
    statut: texte(e, 'statut') as FeuilleDeSoins['statut'],
    codeCertification: texte(e, 'codeCertification') ?? '',
    referenceIntervention: texte(e, 'referenceIntervention'),
  };
}

function lireEntree(e: Element): EntreeHistorique {
  return {
    date: texte(e, 'date') ?? '',
    etablissement: texte(e, 'etablissement') ?? '',
    diagnostic: texte(e, 'diagnostic') ?? '',
    actes: enfants(e, 'actes').map(lireActe),
    numeroFeuilleSoins: texte(e, 'numeroFeuilleSoins'),
  };
}

function lireGarantie(e: Element | null): GarantieCalculee {
  return {
    libelle: texte(e, 'libelle') ?? '',
    montantTotal: nombre(e, 'montantTotal'),
    tauxApplique: nombre(e, 'tauxApplique'),
    partCNAM: nombre(e, 'partCNAM'),
    partPatient: nombre(e, 'partPatient'),
  };
}
