import { Injectable, computed, effect, signal } from '@angular/core';

export type Role = 'patient' | 'medecin' | 'admin';

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

export const ROLES: readonly { id: Role; libelle: string; icone: string }[] = [
  { id: 'patient', libelle: 'Patient', icone: 'person' },
  { id: 'medecin', libelle: 'Médecin', icone: 'stethoscope' },
  { id: 'admin', libelle: 'Admin', icone: 'admin_panel_settings' },
];

const CLE_ROLE = 'sahti.role';
const CLE_PATIENT = 'sahti.patient';

/** Rôle et patient courants (barre du haut), mémorisés dans le localStorage. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly role = signal<Role>(lire(CLE_ROLE, ['patient', 'medecin', 'admin'], 'patient') as Role);
  readonly patientId = signal<string>(lire(CLE_PATIENT, PATIENTS.map((p) => p.id), 'PAT-0001'));
  readonly patient = computed(() => PATIENTS.find((p) => p.id === this.patientId()) ?? PATIENTS[0]);

  constructor() {
    effect(() => ecrire(CLE_ROLE, this.role()));
    effect(() => ecrire(CLE_PATIENT, this.patientId()));
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
