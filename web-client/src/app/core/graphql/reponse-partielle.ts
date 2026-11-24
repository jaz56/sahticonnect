import { CombinedGraphQLErrors } from '@apollo/client';

/** Champ d'une réponse GraphQL que le dashboard n'a pas pu remplir (service distant en panne ou trop lent). */
export interface ChampIndisponible {
  /** Chemin du champ dans la réponse, ex. « patient.couvert », « patient.constantes ». */
  chemin: string;
  /** Service en cause (extensions.service), ex. « dossier-cnam », « telemetrie ». */
  service: string;
  message: string;
}

/**
 * Réponse partielle : le dashboard renvoie les données disponibles ET une erreur SERVICE_INDISPONIBLE par champ
 * distant en panne (champ à null). On en tire une table chemin → champ indisponible, pour afficher une puce
 * « Service indisponible » à la place du champ, sans bloquer le reste de la page.
 */
export function champsIndisponibles(erreur: unknown): Map<string, ChampIndisponible> {
  const resultat = new Map<string, ChampIndisponible>();
  if (!CombinedGraphQLErrors.is(erreur)) {
    return resultat;
  }
  for (const e of erreur.errors) {
    const extensions = (e.extensions ?? {}) as Record<string, unknown>;
    if (extensions['code'] === 'SERVICE_INDISPONIBLE' && e.path) {
      const chemin = e.path.filter((p) => typeof p === 'string').join('.');
      resultat.set(chemin, { chemin, service: String(extensions['service'] ?? 'inconnu'), message: e.message });
    }
  }
  return resultat;
}

/** Erreurs GraphQL « métier » (NOT_FOUND, BAD_REQUEST…) : tout sauf les champs indisponibles. */
export function autresErreurs(erreur: unknown): string[] {
  if (!CombinedGraphQLErrors.is(erreur)) {
    return erreur instanceof Error ? [erreur.message] : [];
  }
  return erreur.errors
    .filter((e) => (e.extensions as Record<string, unknown> | undefined)?.['code'] !== 'SERVICE_INDISPONIBLE')
    .map((e) => e.message);
}
