import { Workflow } from '../../../api/orchestrateur';

/** Instant de la dernière modification (dateMiseAJour, sinon dateCreation), en millisecondes. */
function instant(wf: Workflow): number {
  return Date.parse(wf.dateMiseAJour ?? wf.dateCreation);
}

/** La version la plus récente d'un même workflow (à égalité : celle déjà affichée). */
export function plusRecent(affiche: Workflow, recu: Workflow): Workflow {
  return instant(recu) > instant(affiche) ? recu : affiche;
}

/**
 * Fusionne une liste reçue par REST (instantané pris au moment de la requête) avec ce qui est affiché, mis à jour
 * entre-temps par le flux SSE. Une réponse REST arrivée APRÈS un événement SSE plus récent ne doit pas faire
 * revenir un workflow à une étape antérieure (ex. FACTUREE → FICHE_PUBLIEE) :
 * <ul>
 *   <li>workflow présent des deux côtés : la version la plus récente ;</li>
 *   <li>absent de la liste mais affiché (créé après l'instantané, reçu par SSE) : gardé s'il passe les filtres ;</li>
 *   <li>tri du plus récent au plus ancien (date de création).</li>
 * </ul>
 */
export function fusionnerWorkflows(liste: Workflow[], affiches: Workflow[], passeLesFiltres: (wf: Workflow) => boolean): Workflow[] {
  const parId = new Map(affiches.map((wf) => [wf.id, wf]));
  const fusion = liste.map((recu) => {
    const affiche = parId.get(recu.id);
    return affiche ? plusRecent(affiche, recu) : recu;
  });
  const dansLaListe = new Set(liste.map((wf) => wf.id));
  const recusParSse = affiches.filter((wf) => !dansLaListe.has(wf.id) && passeLesFiltres(wf));
  return [...fusion, ...recusParSse].sort((a, b) => b.dateCreation.localeCompare(a.dateCreation));
}
