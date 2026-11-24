import { DeclenchementAlerte, NiveauAlerte, TypeMesure } from '../api/orchestrateur';

/**
 * Alerte de démonstration pour POST /workflows/declencher (bouton « Simuler une alerte »).
 * Identifiant ALR-AAAA-8NNNNN : série distincte de telemetrie (000001…), des données de démo (9…) et des scripts (6…).
 */
export function alerteSimulee(patientId: string, niveau: NiveauAlerte, maintenant = new Date()): DeclenchementAlerte {
  const critique = niveau === NiveauAlerte.CRITIQUE;
  const valeur = critique ? 142 : 118;
  const seuil = critique ? 'FC > 130' : 'FC ≥ 110';
  const numero = 800000 + Math.floor(Math.random() * 100000);
  return {
    alerteId: `ALR-${maintenant.getFullYear()}-${numero}`,
    patientId,
    niveau,
    typeMesure: TypeMesure.FREQUENCE_CARDIAQUE,
    valeur,
    seuilFranchi: seuil,
    message: `Alerte ${niveau} : fréquence cardiaque ${valeur} bpm (${seuil}) — simulée depuis le client web`,
    horodatage: horodatageTunis(maintenant),
  };
}

/** Date-heure ISO 8601 à l'heure de Tunis (UTC+1, pas d'heure d'été) : 2026-11-24T14:30:00+01:00. */
export function horodatageTunis(date: Date): string {
  const tunis = new Date(date.getTime() + 60 * 60 * 1000);
  return tunis.toISOString().replace(/\.\d{3}Z$/, '+01:00');
}
