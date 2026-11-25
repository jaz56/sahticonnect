package tn.sahticonnect.orchestrateur.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.sahticonnect.orchestrateur.client.CnamClient;
import tn.sahticonnect.orchestrateur.client.DashboardClient;
import tn.sahticonnect.orchestrateur.client.ErreurMetierException;
import tn.sahticonnect.orchestrateur.client.Pannes;
import tn.sahticonnect.orchestrateur.client.PortailClient;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;
import tn.sahticonnect.orchestrateur.entity.Etape;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.NomEtape;
import tn.sahticonnect.orchestrateur.entity.StatutEtape;
import tn.sahticonnect.orchestrateur.entity.StatutFacturation;
import tn.sahticonnect.orchestrateur.entity.TypeEtape;
import tn.sahticonnect.orchestrateur.entity.TypeIntervention;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.repository.WorkflowRepository;
import tn.sahticonnect.orchestrateur.service.WorkflowAlerteService.Resultat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;

/**
 * Saga d'escalade : une alerte CRITIQUE arrive alors que le patient a, depuis moins de {@code sahti.escalade.fenetre},
 * un workflow ELEVEE dont le RDV d'urgence est encore à venir. L'ambulance remplace ce RDV : le workflow ELEVEE est
 * <b>compensé</b> (ses effets sont annulés, chacun dans son service) avant le déclenchement de l'ambulance.
 * <p>
 * Pas de rollback possible : chaque étape du workflow ELEVEE a été validée dans un service différent, avec sa propre
 * base. On exécute donc, dans l'ORDRE INVERSE des étapes d'origine, une action qui annule l'effet de chacune :
 * <ol>
 *   <li>FACTUREE → SOAP annulerFeuilleDeSoins ;</li>
 *   <li>FICHE_PUBLIEE → GraphQL mettreAJourIntervention (COMPENSE, ANNULEE) puis resoudreAlerte ;</li>
 *   <li>INTERVENTION_DECLENCHEE → REST DELETE /rendez-vous/{id}.</li>
 * </ol>
 * Seuls les effets qui existent sont compensés (une feuille jamais émise n'est pas annulée). Chaque compensation est
 * tracée comme une étape de type COMPENSATION, est idempotente (rejouée sans risque) et suit la politique de retry
 * des opérations idempotentes. Si l'une échoue après les retries, le workflow ELEVEE passe à ECHEC et sa relance
 * reprend les compensations manquantes.
 */
@Service
@Slf4j
public class SagaEscalade {

    private final WorkflowRepository repository;
    private final CnamClient cnam;
    private final DashboardClient dashboard;
    private final PortailClient portail;
    private final PublicationWorkflows publication;
    private final OrchestrateurProperties proprietes;
    private final Clock horloge;

    public SagaEscalade(WorkflowRepository repository, CnamClient cnam, DashboardClient dashboard, PortailClient portail,
                        PublicationWorkflows publication, OrchestrateurProperties proprietes, Clock horloge) {
        this.repository = repository;
        this.cnam = cnam;
        this.dashboard = dashboard;
        this.portail = portail;
        this.publication = publication;
        this.proprietes = proprietes;
        this.horloge = horloge;
    }

    // ===================== Règle d'escalade =====================

    /**
     * Workflow ELEVEE à compenser pour ce workflow CRITIQUE : celui qu'il a déjà commencé à compenser (relance), ou
     * un workflow ELEVEE du même patient, créé dans la fenêtre, dont le RDV d'urgence est CONFIRME et pas encore passé.
     */
    public Optional<Workflow> workflowACompenser(Workflow critique) {
        if (critique.getNiveau() != NiveauAlerte.CRITIQUE) {
            return Optional.empty();
        }
        Optional<Workflow> dejaLie = repository.findFirstByCompensePar(critique.getId());
        if (dejaLie.isPresent()) {
            return dejaLie;
        }
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        OffsetDateTime limite = maintenant.minus(proprietes.escalade().fenetre());
        return repository.findByPatientIdAndNiveauAndDateCreationAfterOrderByDateCreationDesc(
                        critique.getPatientId(), NiveauAlerte.ELEVEE, limite).stream()
                .filter(w -> !w.getId().equals(critique.getId()))
                .filter(w -> w.getDateCreation().isAfter(limite))
                .filter(w -> w.getCompensePar() == null && w.getEtat() != EtatWorkflow.COMPENSE && !w.isEnCours())
                .filter(w -> w.getTypeIntervention() == TypeIntervention.RDV_URGENCE && w.getReferenceExterne() != null
                        && w.estReussie(NomEtape.INTERVENTION_DECLENCHEE))
                .filter(w -> rendezVousAVenir(w.getReferenceExterne(), maintenant))
                .findFirst();
    }

    /** Le RDV est-il encore CONFIRME et à venir ? Un RDV TERMINE (il a eu lieu) n'est pas compensé. */
    private boolean rendezVousAVenir(String rdvId, OffsetDateTime maintenant) {
        try {
            PortailClient.EtatRendezVous rdv = portail.consulterRendezVous(rdvId);
            boolean aVenir = rdv.estConfirme() && (rdv.debut() == null || rdv.debut().isAfter(maintenant));
            if (!aVenir) {
                log.info("Escalade : RDV {} à l'état {} ({}) : pas de compensation", rdvId, rdv.statut(), rdv.debut());
            }
            return aVenir;
        } catch (RuntimeException e) {
            // RDV introuvable ou portail en panne : on ne compense pas à l'aveugle
            log.warn("Escalade : état du RDV {} inconnu ({}) : pas de compensation", rdvId, Pannes.decrire(e));
            return false;
        }
    }

    // ===================== Étape ESCALADE du workflow CRITIQUE =====================

    /** Compense le workflow ELEVEE ; résultat de l'étape ESCALADE du workflow CRITIQUE. */
    Resultat escalader(Workflow critique, Workflow elevee) {
        if (elevee.getCompensePar() == null) {
            elevee.setCompensePar(critique.getId());
            elevee.setEnCompensation(true);
            log.info("Escalade : alerte CRITIQUE {} → compensation du workflow ELEVEE {} (RDV {})",
                    critique.getAlerteId(), elevee.getId(), elevee.getReferenceExterne());
        }
        Workflow resultat = compenser(elevee, critique.getAlerteId());
        if (resultat.getEtat() == EtatWorkflow.COMPENSE) {
            return Resultat.ok(resultat.getId() + " compensé (RDV " + resultat.getReferenceExterne() + " annulé, alerte "
                    + resultat.getAlerteId() + " remplacée) : l'ambulance prend le relais");
        }
        return Resultat.echec(resultat.getId() + " : compensation en échec à l'étape " + resultat.getEtapeEnEchec()
                + " (relancer " + resultat.getId() + ") ; l'ambulance est déclenchée quand même");
    }

    // ===================== Compensation du workflow ELEVEE =====================

    /** Relance d'un workflow ELEVEE dont la compensation a échoué : reprend les compensations manquantes. */
    public Workflow reprendre(Workflow elevee) {
        Workflow critique = repository.findById(elevee.getCompensePar()).orElse(null);
        return compenser(elevee, critique != null ? critique.getAlerteId() : null);
    }

    /**
     * Compense, dans l'ordre inverse (FACTUREE, FICHE_PUBLIEE, INTERVENTION_DECLENCHEE), les étapes dont l'effet existe
     * et qui ne sont pas déjà compensées. S'arrête à la première compensation en échec (ECHEC, relançable).
     */
    private Workflow compenser(Workflow elevee, String alerteCritique) {
        String critiqueId = elevee.getCompensePar();
        Workflow wf = enregistrer(elevee);
        for (NomEtape nom : NomEtape.A_COMPENSER) {
            if (wf.estCompensee(nom) || !effetExiste(wf, nom)) {
                continue; // déjà compensée (relance) ou rien à annuler (étape jamais réussie)
            }
            Function<Workflow, Resultat> action = switch (nom) {
                case FACTUREE -> w -> annulerFeuille(w, critiqueId);
                case FICHE_PUBLIEE -> w -> annulerFiche(w, critiqueId, alerteCritique);
                case INTERVENTION_DECLENCHEE -> this::annulerRendezVous;
                default -> throw new IllegalStateException("Étape non compensable : " + nom);
            };
            wf = compensation(wf, nom, action);
            if (!wf.estCompensee(nom)) {
                wf.setEtat(EtatWorkflow.ECHEC);
                wf.setEtapeEnEchec(nom);
                log.warn("Compensation de {} en échec à l'étape {} : relance nécessaire", wf.getId(), nom);
                return enregistrer(wf);
            }
        }
        wf.setEtat(EtatWorkflow.COMPENSE);
        wf.setEtapeEnEchec(null);
        wf.setStatutFacturation(StatutFacturation.ANNULEE);
        log.info("Workflow {} compensé (escalade vers {})", wf.getId(), critiqueId);
        return enregistrer(wf);
    }

    /** L'effet de l'étape existe-t-il dans le service concerné ? (seul ce qui existe est compensé) */
    private static boolean effetExiste(Workflow wf, NomEtape nom) {
        return switch (nom) {
            case FACTUREE -> wf.getNumeroFeuilleSoins() != null;
            case FICHE_PUBLIEE -> wf.getInterventionId() != null;
            case INTERVENTION_DECLENCHEE -> wf.estReussie(NomEtape.INTERVENTION_DECLENCHEE) && wf.getReferenceExterne() != null
                    && wf.getTypeIntervention() == TypeIntervention.RDV_URGENCE;
            default -> false;
        };
    }

    /** Exécute une compensation, la trace (type COMPENSATION), enregistre et publie le workflow. */
    private Workflow compensation(Workflow wf, NomEtape nom, Function<Workflow, Resultat> action) {
        long debut = System.nanoTime();
        Resultat resultat;
        try {
            resultat = action.apply(wf);
        } catch (RuntimeException e) {
            resultat = Resultat.echec("Compensation impossible : " + Pannes.decrire(e));
        }
        long dureeMs = (System.nanoTime() - debut) / 1_000_000;
        wf.ajouterEtape(new Etape(nom, TypeEtape.COMPENSATION, resultat.statut(), maintenant(), dureeMs,
                Math.max(wf.getTentative(), 1), resultat.message()));
        log.info("Compensation {} de {} : {} ({} ms) – {}", nom, wf.getId(), resultat.statut(), dureeMs, resultat.message());
        return enregistrer(wf);
    }

    // ----- a. FACTUREE → SOAP annulerFeuilleDeSoins -----

    private Resultat annulerFeuille(Workflow wf, String critiqueId) {
        try {
            CnamClient.FeuilleEmise feuille = cnam.annulerFeuilleDeSoins(wf.getNumeroFeuilleSoins(),
                    "Escalade vers ambulance " + critiqueId);
            return Resultat.ok("Feuille " + feuille.numero() + " " + feuille.statut() + " (escalade vers " + critiqueId + ")");
        } catch (ErreurMetierException e) {
            if ("FEUILLE_INCONNUE".equals(e.getCode())) {
                return Resultat.ignoree("Feuille " + wf.getNumeroFeuilleSoins() + " inconnue de la CNAM : rien à annuler");
            }
            return Resultat.echec("Annulation de la feuille refusée : " + Pannes.decrire(e));
        }
    }

    // ----- b. FICHE_PUBLIEE → GraphQL mettreAJourIntervention + resoudreAlerte -----

    private Resultat annulerFiche(Workflow wf, String critiqueId, String alerteCritique) {
        dashboard.mettreAJourIntervention(wf.getInterventionId(), new DashboardClient.Modification(
                EtatWorkflow.COMPENSE.name(), StatutFacturation.ANNULEE.name(), null, null, null, null, null,
                descriptionAnnulation(critiqueId, null)));
        dashboard.resoudreAlerte(wf.getAlerteId(), "Remplacée par l'alerte CRITIQUE "
                + (alerteCritique != null ? alerteCritique : "(workflow " + critiqueId + ")"));
        return Resultat.ok("Intervention " + wf.getInterventionId() + " COMPENSE (facturation ANNULEE), alerte "
                + wf.getAlerteId() + " résolue");
    }

    /** « Annulée : escalade vers ambulance INT-… » ; tant que la fiche de l'ambulance n'existe pas, le workflow. */
    static String descriptionAnnulation(String critiqueId, String interventionAmbulance) {
        return "Annulée : escalade vers ambulance "
                + (interventionAmbulance != null ? interventionAmbulance : "(workflow " + critiqueId + ")");
    }

    /**
     * Après la publication de la fiche de l'ambulance : la description de l'intervention compensée cite sa fiche
     * (« Annulée : escalade vers ambulance INT-… »). Best effort : un échec ne remet pas en cause la prise en charge.
     */
    void lierFicheAmbulance(Workflow critique) {
        if (critique.getInterventionId() == null) {
            return;
        }
        repository.findFirstByCompensePar(critique.getId())
                .filter(elevee -> elevee.getInterventionId() != null && elevee.estCompensee(NomEtape.FICHE_PUBLIEE))
                .ifPresent(elevee -> {
                    try {
                        dashboard.mettreAJourIntervention(elevee.getInterventionId(), new DashboardClient.Modification(
                                null, null, null, null, null, null, null,
                                descriptionAnnulation(critique.getId(), critique.getInterventionId())));
                    } catch (RuntimeException e) {
                        log.warn("Description de l'intervention compensée {} non mise à jour : {}",
                                elevee.getInterventionId(), Pannes.decrire(e));
                    }
                });
    }

    // ----- c. INTERVENTION_DECLENCHEE → REST DELETE /rendez-vous/{id} -----

    private Resultat annulerRendezVous(Workflow wf) {
        String rdv = wf.getReferenceExterne();
        try {
            portail.annulerRendezVous(rdv);
            return Resultat.ok("RDV " + rdv + " annulé");
        } catch (ErreurMetierException e) {
            if ("RDV_INTROUVABLE".equals(e.getCode())) {
                return Resultat.ignoree("RDV " + rdv + " introuvable : rien à annuler");
            }
            if (!"RDV_NON_ANNULABLE".equals(e.getCode())) {
                return Resultat.echec("Annulation du RDV refusée : " + Pannes.decrire(e));
            }
            // déjà annulé (compensation rejouée) ou déjà passé : on lit son état pour trancher
            PortailClient.EtatRendezVous etat = portail.consulterRendezVous(rdv);
            if ("ANNULE".equals(etat.statut())) {
                return Resultat.ok("RDV " + rdv + " déjà annulé");
            }
            return Resultat.ignoree("RDV " + rdv + " à l'état " + etat.statut()
                    + " : le RDV a eu lieu, rien à compenser pour lui (escalade arrêtée)");
        }
    }

    // ===================== Utilitaires =====================

    private Workflow enregistrer(Workflow wf) {
        wf.setDateMiseAJour(maintenant());
        Workflow enregistre = repository.save(wf);
        publication.publier(enregistre);
        return enregistre;
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.MILLIS);
    }
}
