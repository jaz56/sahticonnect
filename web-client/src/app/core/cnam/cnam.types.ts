/**
 * Types du service SOAP dossier-cnam, écrits à la main d'après docs/wsdl/dossier-cnam.xsd
 * (namespace http://sahticonnect.tn/cnam). L'ordre des champs suit les xs:sequence du XSD.
 */

export const NAMESPACE_CNAM = 'http://sahticonnect.tn/cnam';

export type LettreCle = 'C' | 'CS' | 'V' | 'K' | 'B' | 'Z';
export type Filiere = 'PUBLIQUE' | 'PRIVEE' | 'REMBOURSEMENT';
export type StatutFeuille = 'EMISE' | 'CERTIFIEE';

/** Valeurs fictives des lettres-clés (DT), pour l'affichage des estimations. */
export const VALEURS_LETTRES_CLES: Record<LettreCle, number> = { C: 45, CS: 60, V: 55, K: 3, B: 0.3, Z: 2 };

export interface ActeCote {
  lettreCle: LettreCle;
  coefficient: number;
  libelle?: string;
}

export interface LigneActe {
  acte: ActeCote;
  montant: number;
}

export interface Assure {
  identifiantPatient: string;
  nom: string;
  prenom: string;
  dateNaissance: string;
  matriculeCNAM: string;
  codeVignetteCNAM: string;
  filiere: Filiere;
  dateFinValidite: string;
  apci: boolean;
  codeAPCI?: string;
}

export interface Couverture {
  couvert: boolean;
  tauxRemboursement: number;
  motif?: string;
}

export interface FeuilleDeSoins {
  numero: string;
  identifiantPatient: string;
  dateSoins: string;
  etablissement: string;
  medecin: string;
  lignes: LigneActe[];
  montantTotal: number;
  tauxApplique: number;
  partCNAM: number;
  partPatient: number;
  statut: StatutFeuille;
  codeCertification: string;
  referenceIntervention?: string;
}

export interface EntreeHistorique {
  date: string;
  etablissement: string;
  diagnostic: string;
  actes: ActeCote[];
  numeroFeuilleSoins?: string;
}

export interface PriseEnCharge {
  libelle: string;
  filiere: Filiere;
  actes: ActeCote[];
}

export interface GarantieCalculee {
  libelle: string;
  montantTotal: number;
  tauxApplique: number;
  partCNAM: number;
  partPatient: number;
}

// ===================== Requêtes / réponses des 4 opérations =====================

export interface VerifierDroitsCnamRequete {
  identifiantPatient: string;
  codeVignetteCNAM: string;
  acte: ActeCote;
  dateActe?: string;
}

export interface VerifierDroitsCnamReponse {
  assure: Assure;
  couverture: Couverture;
  montantEstime: number;
  partCNAMEstimee: number;
  partPatientEstimee: number;
}

export interface EmettreFeuilleDeSoinsRequete {
  identifiantPatient: string;
  codeVignetteCNAM: string;
  dateSoins: string;
  etablissement: string;
  medecin: string;
  actes: ActeCote[];
  referenceIntervention?: string;
}

export interface ConsulterHistoriqueRequete {
  identifiantPatient: string;
  dateDebut?: string;
  dateFin?: string;
}

export interface ConsulterHistoriqueReponse {
  assure: Assure;
  entrees: EntreeHistorique[];
}

export interface ComparerGarantiesRequete {
  identifiantPatient: string;
  priseEnChargeA: PriseEnCharge;
  priseEnChargeB: PriseEnCharge;
}

export interface ComparerGarantiesReponse {
  resultatA: GarantieCalculee;
  resultatB: GarantieCalculee;
  differencePartPatient: number;
  recommandation: string;
}

// ===================== Résultat d'un appel =====================

/** SOAP Fault : faultcode (Client / Server), faultstring, et le code métier lu dans le <detail>. */
export interface FaultSoap {
  faultcode: string;
  faultstring: string;
  /** Code stable du <detail> (ex. VIGNETTE_INVALIDE, PATIENT_INCONNU, SERVICE_INDISPONIBLE). */
  code?: string;
  /** Message du <detail> (fault de dossier-cnam) ou service en panne (fallback de la gateway). */
  detail?: string;
}

/** Résultat d'un appel SOAP, avec les XML échangés (onglet « XML » de l'écran CNAM). */
export interface ResultatSoap<T> {
  requeteXml: string;
  reponseXml: string;
  statutHttp: number;
  resultat?: T;
  fault?: FaultSoap;
}
