import { Routes } from '@angular/router';

/** Un écran = un composant standalone chargé à la demande (lazy loading). */
export const routes: Routes = [
  { path: '', title: 'Accueil — SahtiConnect', loadComponent: () => import('./pages/accueil/accueil') },

  // Patient (REST, portail-patient via la gateway)
  { path: 'patient/profil', title: 'Mon profil — SahtiConnect', loadComponent: () => import('./pages/patient/profil/profil') },
  { path: 'patient/prendre-rdv', title: 'Prendre RDV — SahtiConnect', loadComponent: () => import('./pages/patient/prise-rdv/prise-rdv') },
  { path: 'patient/rdv', title: 'Mes RDV — SahtiConnect', loadComponent: () => import('./pages/patient/mes-rdv/mes-rdv') },
  { path: 'patient/ordonnances', title: 'Ordonnances — SahtiConnect', loadComponent: () => import('./pages/patient/ordonnances/ordonnances') },
  { path: 'patient/ambulance', title: 'Suivi ambulance — SahtiConnect', loadComponent: () => import('./pages/patient/ambulance/ambulance') },

  // Médecin (GraphQL, dashboard-medical via la gateway)
  { path: 'medecin/tableau-de-bord', title: 'Tableau de bord — SahtiConnect', loadComponent: () => import('./pages/medecin/tableau-de-bord/tableau-de-bord') },
  { path: 'medecin/patients/:id', title: 'Fiche patient — SahtiConnect', loadComponent: () => import('./pages/medecin/fiche-patient/fiche-patient') },
  { path: 'medecin/alertes', title: 'Alertes en direct — SahtiConnect', loadComponent: () => import('./pages/medecin/alertes-direct/alertes-direct') },

  // Admin (REST + SSE orchestrateur, SOAP CNAM)
  { path: 'admin/workflows', title: 'Workflows — SahtiConnect', loadComponent: () => import('./pages/admin/workflows/workflows') },
  { path: 'admin/cnam', title: 'CNAM (SOAP) — SahtiConnect', loadComponent: () => import('./pages/admin/cnam/cnam') },

  { path: '**', redirectTo: '' },
];
