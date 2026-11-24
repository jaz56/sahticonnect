/** Libellés français des codes des contrats (affichage seulement). */

export const LIBELLES_CONSTANTES: Record<string, string> = {
  FREQUENCE_CARDIAQUE: 'Fréquence cardiaque',
  SPO2: 'SpO2',
  TENSION_SYSTOLIQUE: 'Tension systolique',
  TENSION_DIASTOLIQUE: 'Tension diastolique',
  TEMPERATURE: 'Température',
  CHUTE: 'Chute',
};

export const LIBELLES_ETAPES: Record<string, string> = {
  ALERTE_RECUE: 'Alerte reçue',
  DROITS_VERIFIES: 'Droits vérifiés',
  INTERVENTION_DECLENCHEE: 'Intervention déclenchée',
  FICHE_PUBLIEE: 'Fiche publiée',
  FACTUREE: 'Facturée',
};

/** Les 5 étapes du workflow d'alerte, dans l'ordre. */
export const ETAPES_WORKFLOW = ['ALERTE_RECUE', 'DROITS_VERIFIES', 'INTERVENTION_DECLENCHEE', 'FICHE_PUBLIEE', 'FACTUREE'] as const;

export const LIBELLES_INTERVENTIONS: Record<string, string> = {
  AMBULANCE: 'Ambulance',
  RDV_URGENCE: "RDV d'urgence",
  TELECONSULTATION: 'Téléconsultation',
};

export function libelle(table: Record<string, string>, code: string | null | undefined): string {
  return code ? (table[code] ?? code) : '—';
}
