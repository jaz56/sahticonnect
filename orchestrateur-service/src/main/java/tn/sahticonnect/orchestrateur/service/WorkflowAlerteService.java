package tn.sahticonnect.orchestrateur.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tn.sahticonnect.orchestrateur.client.CnamClient;
import tn.sahticonnect.orchestrateur.client.CnamClient.Acte;
import tn.sahticonnect.orchestrateur.client.CnamClient.Couverture;
import tn.sahticonnect.orchestrateur.client.CnamClient.FeuilleEmise;
import tn.sahticonnect.orchestrateur.client.DashboardClient;
import tn.sahticonnect.orchestrateur.client.ErreurMetierException;
import tn.sahticonnect.orchestrateur.client.Pannes;
import tn.sahticonnect.orchestrateur.client.PortailClient;
import tn.sahticonnect.orchestrateur.config.OrchestrateurProperties;
import tn.sahticonnect.orchestrateur.correlation.Correlation;
import tn.sahticonnect.orchestrateur.entity.Etape;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.NomEtape;
import tn.sahticonnect.orchestrateur.entity.StatutEtape;
import tn.sahticonnect.orchestrateur.entity.StatutFacturation;
import tn.sahticonnect.orchestrateur.entity.TypeIntervention;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.repository.WorkflowRepository;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow « prise en charge d'une alerte à domicile ». Chaque étape est tracée (nom, statut, horodatage, durée,
 * message), le workflow est enregistré et publié (SSE) après chaque étape, et une relance ne rejoue que les étapes
 * qui n'ont pas réussi.
 * <ol>
 *   <li>ALERTE_RECUE : GraphQL enregistrerAlerte (le dashboard voit l'alerte tout de suite) ;</li>
 *   <li>DROITS_VERIFIES : SOAP consulterHistorique (vignette) puis verifierDroitsCNAM (CRITIQUE : V 1, ELEVEE : CS 1).
 *       SOAP indisponible → ECHEC mais on continue, couverture inconnue, à régulariser : une urgence vitale ne dépend
 *       pas de la facturation ;</li>
 *   <li>INTERVENTION_DECLENCHEE (REST) : CRITIQUE → ambulance (replis : mission en cours réutilisée, aucun véhicule
 *       → RDV d'urgence) ; ELEVEE → RDV d'urgence (repli : téléconsultation). REST indisponible → ECHEC, mais on
 *       publie quand même la fiche pour prévenir le médecin ;</li>
 *   <li>FICHE_PUBLIEE : GraphQL creerIntervention ;</li>
 *   <li>FACTUREE (seulement si l'intervention a été déclenchée) : SOAP emettreFeuilleDeSoins puis GraphQL
 *       mettreAJourIntervention.</li>
 * </ol>
 * Alerte CRITIQUE : une étape ESCALADE, avant l'ambulance, compense le workflow ELEVEE récent du patient
 * (voir {@link SagaEscalade}).
 */
@Service
@Slf4j
public class WorkflowAlerteService {

    private static final Pattern IDENTIFIANT_MISSION = Pattern.compile("AMB-\\d{4}-\\d{6}");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkflowRepository repository;
    private final DashboardClient dashboard;
    private final CnamClient cnam;
    private final PortailClient portail;
    private final PublicationWorkflows publication;
    private final OrchestrateurProperties proprietes;
    private final Clock horloge;
    private final SagaEscalade saga;

    public WorkflowAlerteService(WorkflowRepository repository, DashboardClient dashboard, CnamClient cnam,
                                 PortailClient portail, PublicationWorkflows publication,
                                 OrchestrateurProperties proprietes, Clock horloge, SagaEscalade saga) {
        this.saga = saga;
        this.repository = repository;
        this.dashboard = dashboard;
        this.cnam = cnam;
        this.portail = portail;
        this.publication = publication;
        this.proprietes = proprietes;
        this.horloge = horloge;
    }

    /** Résultat d'une étape. */
    record Resultat(StatutEtape statut, String message) {

        static Resultat ok(String message) {
            return new Resultat(StatutEtape.OK, message);
        }

        static Resultat echec(String message) {
            return new Resultat(StatutEtape.ECHEC, message);
        }

        static Resultat ignoree(String message) {
            return new Resultat(StatutEtape.IGNOREE, message);
        }
    }

    // ===================== Exécution =====================

    /**
     * Exécute (ou reprend) le workflow : les étapes déjà réussies sont sautées. Ne lève jamais d'exception :
     * toute erreur inattendue termine le workflow en ECHEC.
     */
    public Workflow executer(String workflowId) {
        // identifiant de corrélation : celui de la requête HTTP qui a déclenché ou relancé le workflow (recopié
        // par la file du patient), sinon (alerte reçue par gRPC, sans requête) l'identifiant du workflow lui-même
        boolean identifiantPose = Correlation.courant() == null;
        if (identifiantPose) {
            MDC.put(Correlation.CLE_MDC, workflowId);
        }
        Workflow wf = repository.findById(workflowId).orElseThrow(() -> new WorkflowIntrouvableException(workflowId));
        try {
            wf.setEnCours(true);
            wf.setTentative(wf.getTentative() + 1);
            log.info("Workflow {} : exécution n°{} pour l'alerte {} {} ({})", wf.getId(), wf.getTentative(),
                    wf.getNiveau(), wf.getAlerteId(), wf.getPatientId());
            wf = enregistrer(wf, false);

            if (wf.estEnCompensation()) {
                // workflow compensé lors d'une escalade : la relance reprend les compensations manquantes
                wf = saga.reprendre(wf);
            } else {
                wf = etape(wf, NomEtape.ALERTE_RECUE, this::enregistrerAlerte);
                wf = etape(wf, NomEtape.DROITS_VERIFIES, this::verifierDroits);
                wf = escalade(wf);
                wf = etape(wf, NomEtape.INTERVENTION_DECLENCHEE, this::declencherIntervention);
                wf = etape(wf, NomEtape.FICHE_PUBLIEE, this::publierFiche);
                if (wf.getNiveau() == NiveauAlerte.CRITIQUE && wf.estReussie(NomEtape.FICHE_PUBLIEE)) {
                    saga.lierFicheAmbulance(wf);
                }
                wf = etape(wf, NomEtape.FACTUREE, this::facturer);
            }
        } catch (RuntimeException inattendue) {
            log.error("Workflow {} : erreur inattendue", workflowId, inattendue);
        } finally {
            wf.setEnCours(false);
            wf = enregistrer(wf, true);
            log.info("Workflow {} terminé : {}{}", wf.getId(), wf.getEtat(),
                    wf.getEtapeEnEchec() != null ? " (étape en cause : " + wf.getEtapeEnEchec() + ")" : "");
            if (identifiantPose) {
                MDC.remove(Correlation.CLE_MDC);
            }
        }
        return wf;
    }

    /** Exécute une étape si elle n'a pas déjà réussi, trace son résultat, enregistre et publie le workflow. */
    private Workflow etape(Workflow wf, NomEtape nom, Function<Workflow, Resultat> action) {
        if (wf.estReussie(nom)) {
            return wf; // relance : étape déjà faite, pas rejouée
        }
        long debut = System.nanoTime();
        Resultat resultat;
        try {
            resultat = action.apply(wf);
        } catch (RuntimeException e) {
            resultat = Resultat.echec(Pannes.decrire(e));
        }
        long dureeMs = (System.nanoTime() - debut) / 1_000_000;
        wf.ajouterEtape(new Etape(nom, resultat.statut(), maintenant(), dureeMs, wf.getTentative(), resultat.message()));
        log.info("Étape {} : {} ({} ms) – {}", nom, resultat.statut(), dureeMs, resultat.message());
        return enregistrer(wf, false);
    }

    private Workflow enregistrer(Workflow wf, boolean executionTerminee) {
        wf.recalculerEtat(executionTerminee);
        wf.setDateMiseAJour(maintenant());
        Workflow enregistre = repository.save(wf);
        publication.publier(enregistre);
        return enregistre;
    }

    // ===================== 1. ALERTE_RECUE =====================

    private Resultat enregistrerAlerte(Workflow wf) {
        Map<String, Object> alerte = new LinkedHashMap<>();
        alerte.put("id", wf.getAlerteId());
        alerte.put("patientId", wf.getPatientId());
        alerte.put("niveau", wf.getNiveau().name());
        alerte.put("typeConstante", wf.getTypeMesure().name());
        alerte.put("valeur", wf.getValeur());
        alerte.put("seuilFranchi", wf.getSeuilFranchi());
        alerte.put("message", wf.getMessageAlerte());
        alerte.put("horodatage", (wf.getHorodatageAlerte() != null ? wf.getHorodatageAlerte() : wf.getDateCreation()).toString());
        dashboard.enregistrerAlerte(alerte);
        return Resultat.ok("Alerte " + wf.getAlerteId() + " enregistrée sur le dashboard");
    }

    // ===================== 2. DROITS_VERIFIES =====================

    /** CRITIQUE : visite à domicile (V 1) ; ELEVEE : consultation spécialiste (CS 1). */
    static Acte actePrevu(NiveauAlerte niveau) {
        return niveau == NiveauAlerte.CRITIQUE ? Acte.de("V", 1) : Acte.de("CS", 1);
    }

    private Resultat verifierDroits(Workflow wf) {
        Acte acte = actePrevu(wf.getNiveau());
        try {
            wf.setCodeVignette(cnam.consulterVignette(wf.getPatientId()));
            Couverture couverture = cnam.verifierDroits(wf.getPatientId(), wf.getCodeVignette(), acte);
            wf.setCouvert(couverture.couvert());
            wf.setTauxRemboursement(couverture.tauxRemboursement());
            wf.setMotifCouverture(couverture.motif());
            wf.setStatutFacturation(couverture.couvert() ? StatutFacturation.EN_ATTENTE : StatutFacturation.A_REGULARISER);
            return Resultat.ok(couverture.couvert()
                    ? "Couvert : " + couverture.motif() + " (acte " + acte + ")"
                    : "Non couvert : " + couverture.motif() + " → intervention quand même, facturation à régulariser");
        } catch (RuntimeException e) {
            if (!Pannes.estIndisponible(e) && !(e instanceof ErreurMetierException)) {
                throw e;
            }
            // une urgence vitale ne dépend pas de la facturation : on continue, couverture inconnue
            wf.setCouvert(null);
            wf.setTauxRemboursement(null);
            wf.setMotifCouverture("couverture inconnue");
            wf.setStatutFacturation(StatutFacturation.A_REGULARISER);
            return Resultat.echec("Couverture inconnue (" + Pannes.decrire(e)
                    + ") : on continue, facturation à régulariser");
        }
    }

    // ===================== ESCALADE (saga) =====================

    /**
     * Alerte CRITIQUE : si le patient a un workflow ELEVEE récent dont le RDV d'urgence est encore à venir, ce workflow
     * est compensé AVANT l'ambulance (étape ESCALADE). Sans workflow à compenser, aucune étape n'est tracée.
     */
    private Workflow escalade(Workflow wf) {
        if (wf.getNiveau() != NiveauAlerte.CRITIQUE || wf.estReussie(NomEtape.ESCALADE)) {
            return wf;
        }
        var aCompenser = saga.workflowACompenser(wf);
        if (aCompenser.isEmpty()) {
            return wf;
        }
        Workflow elevee = aCompenser.get();
        return etape(wf, NomEtape.ESCALADE, critique -> saga.escalader(critique, elevee));
    }

    // ===================== 3. INTERVENTION_DECLENCHEE =====================

    private Resultat declencherIntervention(Workflow wf) {
        Resultat resultat = wf.getNiveau() == NiveauAlerte.CRITIQUE ? ambulance(wf) : rendezVousUrgence(wf, null);
        // relance : la fiche existe déjà sur le dashboard, on la met à jour avec l'intervention enfin déclenchée
        if (resultat.statut() == StatutEtape.OK && wf.getInterventionId() != null) {
            dashboard.mettreAJourIntervention(wf.getInterventionId(), new DashboardClient.Modification(
                    EtatWorkflow.INTERVENTION_DECLENCHEE.name(), wf.getStatutFacturation().name(),
                    wf.getReferenceExterne(), null, null, null, null, description(wf, true)));
        }
        return resultat;
    }

    private Resultat ambulance(Workflow wf) {
        wf.setTypeIntervention(TypeIntervention.AMBULANCE);
        try {
            PortailClient.Mission mission = portail.demanderAmbulance(wf.getPatientId(), wf.getAlerteId(), wf.getMessageAlerte());
            wf.setReferenceExterne(mission.id());
            return Resultat.ok("Ambulance " + mission.id() + " dépêchée (" + mission.vehicule() + ", "
                    + mission.delaiEstimeMinutes() + " min, vers " + mission.etablissementDestination() + ")");
        } catch (ErreurMetierException e) {
            if ("MISSION_DEJA_EN_COURS".equals(e.getCode())) {
                Matcher id = IDENTIFIANT_MISSION.matcher(e.getMessage());
                wf.setReferenceExterne(id.find() ? id.group() : null);
                return Resultat.ok("Mission " + wf.getReferenceExterne() + " déjà en cours pour ce patient : réutilisée");
            }
            if ("AUCUN_VEHICULE_DISPONIBLE".equals(e.getCode())) {
                return rendezVousUrgence(wf, "aucune ambulance disponible");
            }
            return Resultat.echec("Ambulance refusée : " + Pannes.decrire(e));
        } catch (RuntimeException e) {
            return echecTechnique(e);
        }
    }

    /** RDV d'urgence dans les 2 h ; repli sur une téléconsultation sans ressource REST. */
    private Resultat rendezVousUrgence(Workflow wf, String repliDe) {
        String prefixe = repliDe == null ? "" : "Repli (" + repliDe + ") : ";
        wf.setTypeIntervention(TypeIntervention.RDV_URGENCE);
        try {
            PortailClient.RendezVous rdv = portail.demanderRendezVousUrgence(wf.getPatientId(), wf.getAlerteId(),
                    wf.getMessageAlerte());
            wf.setReferenceExterne(rdv.id());
            String heure = rdv.debut() == null ? "" : " à " + rdv.debut().atZoneSameInstant(horloge.getZone()).format(HEURE);
            return Resultat.ok(prefixe + "RDV d'urgence " + rdv.id() + heure + " avec " + rdv.medecinId());
        } catch (ErreurMetierException e) {
            if ("AUCUN_CRENEAU_URGENCE".equals(e.getCode())) {
                wf.setTypeIntervention(TypeIntervention.TELECONSULTATION);
                wf.setReferenceExterne(null);
                return Resultat.ok(prefixe + "aucun créneau d'urgence dans les 2 h : téléconsultation, "
                        + "rappel du patient par le médecin référent");
            }
            return Resultat.echec(prefixe + "RDV d'urgence refusé : " + Pannes.decrire(e));
        } catch (RuntimeException e) {
            return echecTechnique(e);
        }
    }

    private Resultat echecTechnique(RuntimeException e) {
        if (!Pannes.estIndisponible(e)) {
            throw e;
        }
        return Resultat.echec(Pannes.decrire(e) + " : intervention NON déclenchée, le médecin est prévenu par la fiche");
    }

    // ===================== 4. FICHE_PUBLIEE =====================

    private Resultat publierFiche(Workflow wf) {
        if (wf.getInterventionId() != null) {
            return Resultat.ok("Intervention " + wf.getInterventionId() + " déjà publiée");
        }
        boolean declenchee = wf.estReussie(NomEtape.INTERVENTION_DECLENCHEE);
        if (wf.getTypeIntervention() == null) {
            wf.setTypeIntervention(wf.getNiveau() == NiveauAlerte.CRITIQUE ? TypeIntervention.AMBULANCE : TypeIntervention.RDV_URGENCE);
        }
        if (wf.getStatutFacturation() == null) {
            wf.setStatutFacturation(StatutFacturation.A_REGULARISER);
        }
        String id = dashboard.creerIntervention(new DashboardClient.NouvelleIntervention(
                wf.getPatientId(), wf.getTypeIntervention().name(), wf.getAlerteId(), description(wf, declenchee),
                wf.getReferenceExterne(), declenchee ? EtatWorkflow.INTERVENTION_DECLENCHEE.name() : EtatWorkflow.ECHEC.name(),
                wf.getStatutFacturation().name()));
        wf.setInterventionId(id);
        return Resultat.ok("Intervention " + id + " publiée" + (declenchee ? "" : " à l'état ECHEC : le médecin est prévenu"));
    }

    /** Résumé de la décision et de la couverture, affiché sur le dashboard. */
    private String description(Workflow wf, boolean declenchee) {
        String decision = switch (wf.getTypeIntervention()) {
            case AMBULANCE -> "ambulance " + (wf.getReferenceExterne() != null ? wf.getReferenceExterne() : "");
            case RDV_URGENCE -> "RDV d'urgence " + (wf.getReferenceExterne() != null ? wf.getReferenceExterne() : "");
            case TELECONSULTATION -> "téléconsultation : rappel du patient par le médecin référent";
            case VISITE_DOMICILE -> "visite à domicile";
        };
        String couverture = wf.getCouvert() == null ? "couverture inconnue, facturation à régulariser"
                : wf.getCouvert() ? "couvert (" + wf.getMotifCouverture() + ")"
                : "non couvert (" + wf.getMotifCouverture() + "), facturation à régulariser";
        String texte = "Alerte " + wf.getNiveau() + " " + wf.getSeuilFranchi() + " – "
                + (declenchee ? decision.trim() : "intervention NON déclenchée (portail indisponible)") + " – " + couverture;
        wf.setDescriptionIntervention(texte);
        return texte;
    }

    // ===================== 5. FACTUREE =====================

    /** CRITIQUE : visite + acte technique (V 1 + K 30) ; ELEVEE : consultation spécialiste (CS 1). */
    static List<Acte> actesFactures(NiveauAlerte niveau) {
        return niveau == NiveauAlerte.CRITIQUE ? List.of(Acte.de("V", 1), Acte.de("K", 30)) : List.of(Acte.de("CS", 1));
    }

    private Resultat facturer(Workflow wf) {
        if (!wf.estReussie(NomEtape.INTERVENTION_DECLENCHEE)) {
            return Resultat.ignoree("Intervention non déclenchée : rien à facturer");
        }
        if (wf.getInterventionId() == null) {
            return Resultat.echec("Fiche non publiée sur le dashboard : facturation reportée");
        }
        // feuille déjà émise lors d'une tentative précédente : on ne la réémet pas (pas de doublon côté CNAM)
        if (wf.getNumeroFeuilleSoins() == null) {
            String vignette = wf.getCodeVignette() != null ? wf.getCodeVignette() : cnam.consulterVignette(wf.getPatientId());
            wf.setCodeVignette(vignette);
            FeuilleEmise feuille = cnam.emettreFeuilleDeSoins(wf.getPatientId(), vignette, LocalDate.now(horloge),
                    proprietes.facturation().etablissement(), proprietes.facturation().medecin(),
                    actesFactures(wf.getNiveau()), wf.getInterventionId());
            wf.setNumeroFeuilleSoins(feuille.numero());
            wf.setMontantTotal(feuille.montantTotal());
            wf.setPartCNAM(feuille.partCNAM());
            wf.setPartPatient(feuille.partPatient());
            wf.setStatutFacturation(feuille.estCertifiee() ? StatutFacturation.FACTUREE : StatutFacturation.A_REGULARISER);
            repository.save(wf); // numéro conservé même si la mise à jour du dashboard échoue
        }
        dashboard.mettreAJourIntervention(wf.getInterventionId(), new DashboardClient.Modification(
                EtatWorkflow.FACTUREE.name(), wf.getStatutFacturation().name(), null, wf.getNumeroFeuilleSoins(),
                wf.getMontantTotal(), wf.getPartCNAM(), wf.getPartPatient(), null));
        return Resultat.ok("Feuille " + wf.getNumeroFeuilleSoins() + " "
                + (wf.getStatutFacturation() == StatutFacturation.FACTUREE ? "CERTIFIEE" : "EMISE (à régulariser)") + " : "
                + montant(wf.getMontantTotal()) + " DT (CNAM " + montant(wf.getPartCNAM()) + " / patient "
                + montant(wf.getPartPatient()) + ")");
    }

    // ===================== Utilitaires =====================

    private OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.MILLIS);
    }

    private static String montant(BigDecimal montant) {
        return montant == null ? "?" : new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.FRANCE)).format(montant);
    }
}
