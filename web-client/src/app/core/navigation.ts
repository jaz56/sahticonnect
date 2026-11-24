import { Role } from './session.service';

export interface EntreeMenu {
  libelle: string;
  icone: string;
  lien: string;
  /** Protocole consommé par l'écran (affiché dans le menu, pour la démo). */
  protocole: 'REST' | 'GraphQL' | 'SOAP' | 'REST + SSE' | '—';
}

/** Menu propre à chaque rôle. */
export const MENUS: Record<Role, EntreeMenu[]> = {
  patient: [
    { libelle: 'Mon profil', icone: 'badge', lien: '/patient/profil', protocole: 'REST' },
    { libelle: 'Prendre RDV', icone: 'event_available', lien: '/patient/prendre-rdv', protocole: 'REST' },
    { libelle: 'Mes RDV', icone: 'calendar_month', lien: '/patient/rdv', protocole: 'REST' },
    { libelle: 'Ordonnances', icone: 'medication', lien: '/patient/ordonnances', protocole: 'REST' },
    { libelle: 'Suivi ambulance', icone: 'ambulance', lien: '/patient/ambulance', protocole: 'REST' },
  ],
  medecin: [
    { libelle: 'Tableau de bord', icone: 'monitor_heart', lien: '/medecin/tableau-de-bord', protocole: 'GraphQL' },
    { libelle: 'Alertes en direct', icone: 'notifications_active', lien: '/medecin/alertes', protocole: 'GraphQL' },
  ],
  admin: [
    { libelle: 'Workflows', icone: 'account_tree', lien: '/admin/workflows', protocole: 'REST + SSE' },
    { libelle: 'CNAM (SOAP)', icone: 'receipt_long', lien: '/admin/cnam', protocole: 'SOAP' },
  ],
};
