import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Authentification } from './auth/authentification.service';

export interface PatientDeTest {
  id: string;
  nom: string;
  pathologie: string;
  couverture: string;
  vignette: string;
}

/** Les 5 patients de test, communs à tous les services (CLAUDE.md). */
export const PATIENTS: readonly PatientDeTest[] = [
  { id: 'PAT-0001', nom: 'Ahmed Ben Salah', pathologie: 'Insuffisance cardiaque', couverture: 'APCI 100 %', vignette: 'VG-2026-000001' },
  { id: 'PAT-0002', nom: 'Leila Trabelsi', pathologie: 'Hypertension', couverture: 'Vignette expirée', vignette: 'VG-2026-000002' },
  { id: 'PAT-0003', nom: 'Karim Jaziri', pathologie: 'BPCO', couverture: 'Filière publique 80 %', vignette: 'VG-2026-000003' },
  { id: 'PAT-0004', nom: 'Sonia Mansour', pathologie: 'Diabète', couverture: 'Remboursement 50 %', vignette: 'VG-2026-000004' },
  { id: 'PAT-0005', nom: 'Hedi Gharbi', pathologie: 'Personne âgée (chutes)', couverture: 'Filière publique 80 %', vignette: 'VG-2026-000005' },
];

const CLE_PATIENT = 'sahti.patient';

/**
 * Patient courant des écrans. Un utilisateur PATIENT est TOUJOURS sur son propre dossier (claim patient_id du
 * jeton) ; le médecin et l'admin choisissent le patient dans la barre du haut (mémorisé dans le localStorage).
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly authentification = inject(Authentification);
  private readonly patientChoisi = signal<string>(lire(CLE_PATIENT, PATIENTS.map((p) => p.id), 'PAT-0001'));

  readonly patientId = computed(() =>
    this.authentification.estPatient()
      ? (this.authentification.utilisateur()?.patientId ?? this.patientChoisi())
      : this.patientChoisi(),
  );
  readonly patient = computed(() => PATIENTS.find((p) => p.id === this.patientId()) ?? PATIENTS[0]);

  constructor() {
    effect(() => ecrire(CLE_PATIENT, this.patientChoisi()));
  }

  /** Sélecteur de patient (médecin, admin) ; sans effet pour un patient, limité à son dossier. */
  choisirPatient(id: string): void {
    if (!this.authentification.estPatient()) {
      this.patientChoisi.set(id);
    }
  }
}

function lire(cle: string, valeursPermises: string[], defaut: string): string {
  try {
    const valeur = localStorage.getItem(cle);
    return valeur && valeursPermises.includes(valeur) ? valeur : defaut;
  } catch {
    return defaut; // navigation privée, stockage bloqué
  }
}

function ecrire(cle: string, valeur: string): void {
  try {
    localStorage.setItem(cle, valeur);
  } catch {
    /* stockage indisponible : la valeur reste en mémoire */
  }
}
