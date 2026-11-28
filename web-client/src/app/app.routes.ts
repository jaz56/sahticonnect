import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';

/**
 * Un écran = un composant standalone chargé à la demande (lazy loading). Chaque groupe d'écrans est gardé par les
 * rôles du jeton (mêmes règles que la gateway : PATIENT, MEDECIN, ADMIN pour le portail ; MEDECIN, ADMIN pour
 * GraphQL ; ADMIN pour l'orchestrateur et la CNAM).
 */
export const routes: Routes = [
  { path: '', title: 'Accueil — SahtiConnect', loadComponent: () => import('./pages/accueil/accueil') },

  // Patient (REST, portail-patient via la gateway)
  { path: 'patient/profil', title: 'Mon profil — SahtiConnect', canActivate: [roleGuard('PATIENT', 'MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/patient/profil/profil') },
  { path: 'patient/prendre-rdv', title: 'Prendre RDV — SahtiConnect', canActivate: [roleGuard('PATIENT', 'MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/patient/prise-rdv/prise-rdv') },
  { path: 'patient/rdv', title: 'Mes RDV — SahtiConnect', canActivate: [roleGuard('PATIENT', 'MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/patient/mes-rdv/mes-rdv') },
  { path: 'patient/ordonnances', title: 'Ordonnances — SahtiConnect', canActivate: [roleGuard('PATIENT', 'MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/patient/ordonnances/ordonnances') },
  { path: 'patient/ambulance', title: 'Suivi ambulance — SahtiConnect', canActivate: [roleGuard('PATIENT', 'MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/patient/ambulance/ambulance') },

  // Médecin (GraphQL, dashboard-medical via la gateway)
  { path: 'medecin/tableau-de-bord', title: 'Tableau de bord — SahtiConnect', canActivate: [roleGuard('MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/medecin/tableau-de-bord/tableau-de-bord') },
  { path: 'medecin/patients/:id', title: 'Fiche patient — SahtiConnect', canActivate: [roleGuard('MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/medecin/fiche-patient/fiche-patient') },
  { path: 'medecin/alertes', title: 'Alertes en direct — SahtiConnect', canActivate: [roleGuard('MEDECIN', 'ADMIN')], loadComponent: () => import('./pages/medecin/alertes-direct/alertes-direct') },

  // Admin (REST + SSE orchestrateur, SOAP CNAM)
  { path: 'admin/workflows', title: 'Workflows — SahtiConnect', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/workflows/workflows') },
  { path: 'admin/cnam', title: 'CNAM (SOAP) — SahtiConnect', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/admin/cnam/cnam') },

  { path: 'acces-refuse', title: 'Accès refusé — SahtiConnect', loadComponent: () => import('./pages/acces-refuse/acces-refuse') },

  // Système de design (tokens, composants) : en développement seulement (ng serve), absent du build de production
  { path: 'design', title: 'Système de design — SahtiConnect', canMatch: [() => isDevMode()], loadComponent: () => import('./pages/design/design') },

  { path: '**', redirectTo: '' },
];
