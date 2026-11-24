import { HttpContextToken, HttpErrorResponse } from '@angular/common/http';

/** Schéma « Erreur » commun aux contrats REST (portail, orchestrateur) et aux fallbacks de la gateway. */
export interface ErreurApi {
  statut: number;
  code: string;
  message: string;
  chemin?: string;
  service?: string;
  details: { champ: string; message: string }[];
  correlationId: string | null;
}

/**
 * À mettre dans le contexte d'une requête dont le composant affiche lui-même l'erreur
 * (ex. SOAP : la Fault est montrée dans l'écran CNAM) : l'intercepteur n'ouvre alors pas de snackbar.
 */
export const SANS_NOTIFICATION = new HttpContextToken<boolean>(() => false);

/** Lit une réponse d'erreur HTTP au format Erreur (ou en déduit une à défaut). */
export function lireErreur(reponse: HttpErrorResponse): ErreurApi {
  const correlationId = reponse.headers?.get('X-Correlation-Id') ?? null;
  const corps = typeof reponse.error === 'string' ? essayerJson(reponse.error) : reponse.error;
  if (corps && typeof corps === 'object' && typeof corps.code === 'string') {
    return {
      statut: Number(corps.statut ?? reponse.status),
      code: corps.code,
      message: String(corps.message ?? reponse.statusText),
      chemin: corps.chemin,
      service: corps.service,
      details: Array.isArray(corps.details) ? corps.details : [],
      correlationId,
    };
  }
  if (reponse.status === 0) {
    return {
      statut: 0,
      code: 'GATEWAY_INJOIGNABLE',
      message: "L'API Gateway (http://localhost:8080) ne répond pas",
      details: [],
      correlationId,
    };
  }
  return {
    statut: reponse.status,
    code: `HTTP_${reponse.status}`,
    message: reponse.statusText || 'Erreur inattendue',
    details: [],
    correlationId,
  };
}

/** Erreurs de validation (400) indexées par champ, pour les afficher sous les champs d'un formulaire. */
export function erreursParChamp(erreur: ErreurApi): Record<string, string> {
  return Object.fromEntries(erreur.details.map((d) => [d.champ, d.message]));
}

function essayerJson(texte: string): any {
  try {
    return JSON.parse(texte);
  } catch {
    return null;
  }
}
