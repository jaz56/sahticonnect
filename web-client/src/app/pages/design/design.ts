import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Workflow } from '../../api/orchestrateur';
import { FriseWorkflow } from '../../shared/frise-workflow';
import {
  BadgeNiveau, BlocSection, EnTetePage, EtatErreur, EtatVide, MoniteurConstante, ServiceIndisponible, ValeurMono,
} from '../../shared/ui';

interface Couleur { nom: string; variable: string; hex: string; usage: string; contraste?: string }

/**
 * Page de démonstration du système de design (route /design, en développement seulement : canMatch isDevMode).
 * Couleurs (avec contraste mesuré), typographie, espacements, formes et composants, avec des données fictives.
 */
@Component({
  selector: 'app-design',
  imports: [
    MatButtonModule, FriseWorkflow, BadgeNiveau, BlocSection, EnTetePage, EtatErreur, EtatVide, MoniteurConstante,
    ServiceIndisponible, ValeurMono,
  ],
  templateUrl: './design.html',
  styleUrl: './design.scss',
})
export default class Design {
  protected readonly couleurs: Couleur[] = [
    { nom: 'Fond', variable: '--sc-fond', hex: '#F6F5F2', usage: 'Fond de page, blanc cassé chaud' },
    { nom: 'Surface', variable: '--sc-surface', hex: '#FFFFFF', usage: 'Blocs, tableaux, barres' },
    { nom: 'Encre', variable: '--sc-encre', hex: '#17222E', usage: 'Texte principal', contraste: '16,1:1 AAA' },
    { nom: 'Encre secondaire', variable: '--sc-encre-secondaire', hex: '#5E5A54', usage: 'Texte secondaire', contraste: '6,9:1 AA' },
    { nom: 'Encre tertiaire', variable: '--sc-encre-tertiaire', hex: '#76716A', usage: 'Unités, métadonnées', contraste: '4,8:1 AA' },
    { nom: 'Bordure', variable: '--sc-bordure', hex: '#E2DFD9', usage: 'Filets de 1 px' },
    { nom: 'Bordure forte', variable: '--sc-bordure-forte', hex: '#8F8A82', usage: 'Contour des contrôles', contraste: '3,4:1 non-texte' },
    { nom: 'Accent', variable: '--sc-accent', hex: '#0F5C63', usage: 'Seul accent : actions, sélection, élément actif', contraste: '7,7:1 AAA' },
    { nom: 'Accent pâle', variable: '--sc-accent-pale', hex: '#E4EFEF', usage: 'Fond sélectionné', contraste: 'accent 6,6:1' },
    { nom: 'Critique', variable: '--sc-critique', hex: '#A4161A', usage: 'Niveau CRITIQUE, échec', contraste: '6,7:1 sur pâle' },
    { nom: 'Critique pâle', variable: '--sc-critique-pale', hex: '#FBEAEA', usage: 'Fond du niveau CRITIQUE' },
    { nom: 'Élevée', variable: '--sc-elevee', hex: '#8A4B00', usage: 'Niveau ÉLEVÉE, à régulariser', contraste: '6,1:1 sur pâle' },
    { nom: 'Élevée pâle', variable: '--sc-elevee-pale', hex: '#FCF1DF', usage: 'Fond du niveau ÉLEVÉE' },
    { nom: 'Neutre', variable: '--sc-neutre', hex: '#4A4F57', usage: 'NORMAL, FAIBLE, MODÉRÉ, statuts', contraste: '7,0:1 sur pâle' },
    { nom: 'Neutre pâle', variable: '--sc-neutre-pale', hex: '#EEEDEA', usage: 'Fond des badges neutres' },
  ];

  protected readonly tailles = [
    { taille: 32, ligne: 40, usage: 'Valeur d\'un moniteur', mono: true, texte: '142,5' },
    { taille: 24, ligne: 32, usage: 'Titre de page', texte: 'Ahmed Ben Salah' },
    { taille: 20, ligne: 28, usage: 'Logo, titre du détail', texte: 'SahtiConnect' },
    { taille: 16, ligne: 24, usage: 'Titre de bloc, lecture', texte: 'Constantes vitales' },
    { taille: 14, ligne: 20, usage: 'Texte courant, cellules', texte: 'Alerte ÉLEVÉE : tension systolique au-dessus du seuil' },
    { taille: 12, ligne: 16, usage: 'Métadonnées, badges, en-têtes', texte: 'Dernière mesure à 14:46:21' },
  ];

  protected readonly espacements = [4, 8, 12, 16, 24, 32, 48, 64];

  protected readonly serieNormale = [72, 74, 73, 75, 76, 74, 73, 75, 77, 76, 75, 74];
  protected readonly serieMontante = [96, 102, 108, 112, 118, 124, 129, 133, 138, 142];

  /** Workflow fictif : escalade réussie, une étape en échec relancée, compensation d'un autre workflow. */
  protected readonly workflow = {
    id: 'WF-2026-000042', alerteId: 'ALR-2026-000017', patientId: 'PAT-0001', niveau: 'CRITIQUE', etat: 'INTERVENTION_DECLENCHEE',
    enCours: true, compensePar: 'WF-2026-000043', dateCreation: '2026-11-28T14:46:07+01:00',
    etapes: [
      { nom: 'ALERTE_RECUE', statut: 'OK', type: 'EXECUTION', horodatage: '2026-11-28T14:46:07+01:00', dureeMs: 51, tentative: 1, message: 'Alerte ALR-2026-000017 enregistrée sur le dashboard' },
      { nom: 'DROITS_VERIFIES', statut: 'ECHEC', type: 'EXECUTION', horodatage: '2026-11-28T14:46:08+01:00', dureeMs: 3004, tentative: 1, message: 'Service dossier-cnam indisponible : délai de 3 s dépassé' },
      { nom: 'DROITS_VERIFIES', statut: 'OK', type: 'EXECUTION', horodatage: '2026-11-28T14:47:30+01:00', dureeMs: 83, tentative: 2, message: 'Couvert : APCI 100 % (acte V 1)' },
      { nom: 'FACTUREE', statut: 'OK', type: 'COMPENSATION', horodatage: '2026-11-28T14:47:31+01:00', dureeMs: 61, tentative: 1, message: 'Feuille FS-2026-000056 ANNULEE (escalade vers WF-2026-000043)' },
      { nom: 'INTERVENTION_DECLENCHEE', statut: 'OK', type: 'COMPENSATION', horodatage: '2026-11-28T14:47:31+01:00', dureeMs: 29, tentative: 1, message: 'RDV RDV-2026-000051 annulé' },
    ],
  } as unknown as Workflow;
}
