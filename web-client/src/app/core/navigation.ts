import { RoleSahti } from './auth/authentification.service';

export interface EntreeMenu {
  libelle: string;
  icone: string;
  lien: string;
  /** Protocole consommé par l'écran (affiché dans le menu, pour la démo). */
  protocole: 'REST' | 'GraphQL' | 'SOAP' | 'REST + SSE' | '—';
}

export interface SectionMenu {
  titre: string;
  /** Rôles qui voient la section (mêmes règles que les gardes de route et que la gateway). */
  roles: RoleSahti[];
  entrees: EntreeMenu[];
}

/** Menu : chaque section n'apparaît que pour les rôles du jeton. */
export const SECTIONS: readonly SectionMenu[] = [
  {
    titre: 'Dossier patient',
    roles: ['PATIENT', 'MEDECIN', 'ADMIN'],
    entrees: [
      { libelle: 'Profil', icone: 'badge', lien: '/patient/profil', protocole: 'REST' },
      { libelle: 'Prendre RDV', icone: 'event_available', lien: '/patient/prendre-rdv', protocole: 'REST' },
      { libelle: 'Rendez-vous', icone: 'calendar_month', lien: '/patient/rdv', protocole: 'REST' },
      { libelle: 'Ordonnances', icone: 'medication', lien: '/patient/ordonnances', protocole: 'REST' },
      { libelle: 'Suivi ambulance', icone: 'ambulance', lien: '/patient/ambulance', protocole: 'REST' },
    ],
  },
  {
    titre: 'Espace médecin',
    roles: ['MEDECIN', 'ADMIN'],
    entrees: [
      { libelle: 'Tableau de bord', icone: 'monitor_heart', lien: '/medecin/tableau-de-bord', protocole: 'GraphQL' },
      { libelle: 'Alertes en direct', icone: 'notifications_active', lien: '/medecin/alertes', protocole: 'GraphQL' },
    ],
  },
  {
    titre: 'Administration',
    roles: ['ADMIN'],
    entrees: [
      { libelle: 'Workflows', icone: 'account_tree', lien: '/admin/workflows', protocole: 'REST + SSE' },
      { libelle: 'CNAM (SOAP)', icone: 'receipt_long', lien: '/admin/cnam', protocole: 'SOAP' },
    ],
  },
];

export function sectionsPour(roles: readonly RoleSahti[]): SectionMenu[] {
  return SECTIONS.filter((s) => s.roles.some((r) => roles.includes(r)));
}
