package tn.sahticonnect.portail.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.RendezVousRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de tous les endpoints du contrat et de tous ses codes d'erreur (MockMvc,
 * contexte complet, H2 + données de test). @Transactional : chaque test est annulé à la fin,
 * les tests restent donc indépendants. MED-003 est réservé au test de concurrence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortailPatientApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CreneauRepository creneauRepository;
    @Autowired
    private RendezVousRepository rendezVousRepository;
    @Autowired
    private Clock horloge;

    // ===================== Utilitaires =====================

    private ResultActions envoyer(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete,
                                  String json) throws Exception {
        return mvc.perform(requete.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Vérifie le schéma Erreur du contrat. */
    private static void erreur(ResultActions resultat, int statut, String code, String chemin) throws Exception {
        resultat.andExpect(status().is(statut))
                .andExpect(jsonPath("$.statut").value(statut))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.chemin").value(chemin))
                .andExpect(jsonPath("$.horodatage").isNotEmpty());
    }

    /** Jour de consultation (hors dimanche) dans {@code decalage} jours ou juste après. */
    private LocalDate jourDeConsultation(int decalage) {
        LocalDate jour = LocalDate.now(horloge).plusDays(decalage);
        return jour.getDayOfWeek() == DayOfWeek.SUNDAY ? jour.plusDays(1) : jour;
    }

    /** Premier créneau libre d'un type, à partir de demain. */
    private Creneau creneauLibre(String medecinId, TypeConsultation type, int aPartirDe) {
        LocalDate debut = LocalDate.now(horloge).plusDays(aPartirDe);
        return creneauRepository.findByMedecinIdAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
                        medecinId, debut.atStartOfDay(), debut.plusDays(12).atStartOfDay()).stream()
                .filter(c -> c.getType() == type && c.isDisponible())
                .findFirst().orElseThrow();
    }

    private String creerRendezVous(String patientId, Creneau creneau) throws Exception {
        String location = envoyer(post("/rendez-vous"), """
                {"identifiantPatient":"%s","medecinId":"%s","creneauId":"%s","type":"%s","motif":"Test"}"""
                .formatted(patientId, creneau.getMedecin().getId(), creneau.getId(), creneau.getType()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    // ===================== Patients =====================

    @Nested
    class Patients {

        @Test
        void consulter_200() throws Exception {
            mvc.perform(get("/patients/PAT-0001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("PAT-0001"))
                    .andExpect(jsonPath("$.nom").value("Ben Salah"))
                    .andExpect(jsonPath("$.dateNaissance").value("1958-03-14"))
                    .andExpect(jsonPath("$.medecinTraitantId").value("MED-001"))
                    .andExpect(jsonPath("$.contactUrgence.lien").value("Épouse"));
        }

        @Test
        void consulter_404() throws Exception {
            erreur(mvc.perform(get("/patients/PAT-0099")), 404, "PATIENT_INTROUVABLE", "/patients/PAT-0099");
        }

        @Test
        void consulter_identifiantMalForme_400() throws Exception {
            ResultActions r = mvc.perform(get("/patients/P1"));
            erreur(r, 400, "REQUETE_INVALIDE", "/patients/P1");
            r.andExpect(jsonPath("$.details[0].champ").value("id"));
        }

        @Test
        void modifier_seulesLesCoordonneesChangent_200() throws Exception {
            envoyer(put("/patients/PAT-0003"), """
                    {"telephone":"+216 20 111 222","adresse":"1 rue de Carthage, 2016 Carthage",
                     "contactUrgence":{"nom":"Amel Jaziri","lien":"Fille","telephone":"+216 23 000 111"},
                     "nom":"Ignoré"}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.telephone").value("+216 20 111 222"))
                    .andExpect(jsonPath("$.adresse").value("1 rue de Carthage, 2016 Carthage"))
                    .andExpect(jsonPath("$.contactUrgence.telephone").value("+216 23 000 111"))
                    .andExpect(jsonPath("$.nom").value("Jaziri"))
                    .andExpect(jsonPath("$.email").value("karim.jaziri@exemple.tn"));
        }

        @Test
        void modifier_emailDejaUtilise_409() throws Exception {
            erreur(envoyer(put("/patients/PAT-0001"), """
                    {"email":"leila.trabelsi@exemple.tn"}"""), 409, "EMAIL_DEJA_UTILISE", "/patients/PAT-0001");
        }

        @Test
        void modifier_emailEtTelephoneInvalides_400AvecDetails() throws Exception {
            ResultActions r = envoyer(put("/patients/PAT-0001"), """
                    {"email":"pas-un-email","telephone":"abc"}""");
            erreur(r, 400, "REQUETE_INVALIDE", "/patients/PAT-0001");
            r.andExpect(jsonPath("$.details", hasSize(2)))
                    .andExpect(jsonPath("$.details[*].champ", hasItem("email")))
                    .andExpect(jsonPath("$.details[*].champ", hasItem("telephone")));
        }

        @Test
        void modifier_corpsVide_400() throws Exception {
            erreur(envoyer(put("/patients/PAT-0001"), "{}"), 400, "REQUETE_INVALIDE", "/patients/PAT-0001");
        }

        @Test
        void modifier_404() throws Exception {
            erreur(envoyer(put("/patients/PAT-0099"), """
                    {"telephone":"+216 20 111 222"}"""), 404, "PATIENT_INTROUVABLE", "/patients/PAT-0099");
        }
    }

    // ===================== Ordonnances =====================

    @Nested
    class Ordonnances {

        @Test
        void toutes_dontUneExpiree() throws Exception {
            mvc.perform(get("/patients/PAT-0001/ordonnances"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id", matchesPattern("ORD-\\d{4}-\\d{6}")))
                    .andExpect(jsonPath("$[0].medecinNom").value("Dr Mohamed Chaabane"))
                    .andExpect(jsonPath("$[0].lignes", hasSize(2)));
        }

        @Test
        void activesSeulement() throws Exception {
            mvc.perform(get("/patients/PAT-0001/ordonnances").param("actives", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].renouvelable").value(true));
            mvc.perform(get("/patients/PAT-0005/ordonnances").param("actives", "true"))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void patientInconnu_404() throws Exception {
            erreur(mvc.perform(get("/patients/PAT-0099/ordonnances")), 404, "PATIENT_INTROUVABLE",
                    "/patients/PAT-0099/ordonnances");
        }

        @Test
        void identifiantMalForme_400() throws Exception {
            erreur(mvc.perform(get("/patients/X/ordonnances")), 400, "REQUETE_INVALIDE", "/patients/X/ordonnances");
        }
    }

    // ===================== Médecins et créneaux =====================

    @Nested
    class MedecinsEtCreneaux {

        @Test
        void listerMedecins() throws Exception {
            mvc.perform(get("/medecins"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].id").value("MED-001"))
                    .andExpect(jsonPath("$[0].tarifConsultation").value(60.0));
            mvc.perform(get("/medecins").param("specialite", "cardiologie"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].nom").value("Chaabane"));
            mvc.perform(get("/medecins").param("ville", "Ariana"))
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void creneauxDUnJourDeConsultation() throws Exception {
            LocalDate jour = jourDeConsultation(5);
            mvc.perform(get("/creneaux").param("medecinId", "MED-002").param("date", jour.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(12 + 48)))
                    .andExpect(jsonPath("$[*].disponible", everyItem(is(true))));
            mvc.perform(get("/creneaux").param("medecinId", "MED-002").param("date", jour.toString())
                            .param("type", "TELECONSULTATION"))
                    .andExpect(jsonPath("$", hasSize(6)))
                    .andExpect(jsonPath("$[0].id").value("CRN-" + jour.toString().replace("-", "") + "-002-0930"))
                    .andExpect(jsonPath("$[0].debut", startsWith(jour + "T09:30:00+01:00")));
            mvc.perform(get("/creneaux").param("medecinId", "MED-002").param("date", jour.toString())
                            .param("type", "URGENCE"))
                    .andExpect(jsonPath("$", hasSize(48)))
                    .andExpect(jsonPath("$[0].id", matchesPattern("CRN-\\d{8}-002-0015")));
        }

        @Test
        void creneauxReservesExclusParDefaut() throws Exception {
            // RDV de test : PAT-0001 avec MED-001, 09:00, deux jours de consultation plus tard
            LocalDate jour = jourDeConsultation(2);
            mvc.perform(get("/creneaux").param("medecinId", "MED-001").param("date", jour.toString())
                            .param("type", "PRESENTIEL"))
                    .andExpect(jsonPath("$", hasSize(5)));
            mvc.perform(get("/creneaux").param("medecinId", "MED-001").param("date", jour.toString())
                            .param("type", "PRESENTIEL").param("disponibles", "false"))
                    .andExpect(jsonPath("$", hasSize(6)))
                    .andExpect(jsonPath("$[0].disponible").value(false));
        }

        @Test
        void medecinInconnu_404() throws Exception {
            erreur(mvc.perform(get("/creneaux").param("medecinId", "MED-999").param("date", "2026-11-25")),
                    404, "MEDECIN_INTROUVABLE", "/creneaux");
        }

        @Test
        void parametresManquantsOuInvalides_400() throws Exception {
            erreur(mvc.perform(get("/creneaux").param("medecinId", "MED-001")), 400, "REQUETE_INVALIDE", "/creneaux");
            erreur(mvc.perform(get("/creneaux").param("medecinId", "M1").param("date", "2026-11-25")),
                    400, "REQUETE_INVALIDE", "/creneaux");
            erreur(mvc.perform(get("/creneaux").param("medecinId", "MED-001").param("date", "25/11/2026")),
                    400, "REQUETE_INVALIDE", "/creneaux");
            erreur(mvc.perform(get("/creneaux").param("medecinId", "MED-001").param("date", "2026-11-25")
                    .param("type", "INCONNU")), 400, "REQUETE_INVALIDE", "/creneaux");
        }
    }

    // ===================== Rendez-vous =====================

    @Nested
    class RendezVous {

        @Test
        void creerStandard_201AvecLocation_puisConsulter() throws Exception {
            Creneau creneau = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            String location = envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","medecinId":"MED-001","creneauId":"%s",
                     "type":"PRESENTIEL","motif":"Contrôle"}""".formatted(creneau.getId()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern("/rendez-vous/RDV-\\d{4}-\\d{6}")))
                    .andExpect(jsonPath("$.id", matchesPattern("RDV-\\d{4}-\\d{6}")))
                    .andExpect(jsonPath("$.creneauId").value(creneau.getId()))
                    .andExpect(jsonPath("$.statut").value("CONFIRME"))
                    .andExpect(jsonPath("$.debut").isNotEmpty())
                    .andReturn().getResponse().getHeader("Location");

            mvc.perform(get(location))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.identifiantPatient").value("PAT-0001"))
                    .andExpect(jsonPath("$.motif").value("Contrôle"));
            assertThat(creneauRepository.findById(creneau.getId()).orElseThrow().isDisponible()).isFalse();
        }

        @Test
        void creneauDejaReserve_409() throws Exception {
            Creneau creneau = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            creerRendezVous("PAT-0001", creneau);

            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0004","medecinId":"MED-001","creneauId":"%s","type":"PRESENTIEL"}"""
                    .formatted(creneau.getId())), 409, "CRENEAU_DEJA_RESERVE", "/rendez-vous");
        }

        @Test
        void creneauInconnu_404() throws Exception {
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","medecinId":"MED-001","creneauId":"CRN-20990101-001-0900",
                     "type":"PRESENTIEL"}"""), 404, "CRENEAU_INTROUVABLE", "/rendez-vous");
        }

        @Test
        void patientOuMedecinInconnu_404() throws Exception {
            Creneau creneau = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0099","medecinId":"MED-001","creneauId":"%s","type":"PRESENTIEL"}"""
                    .formatted(creneau.getId())), 404, "PATIENT_INTROUVABLE", "/rendez-vous");
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","medecinId":"MED-999","creneauId":"%s","type":"PRESENTIEL"}"""
                    .formatted(creneau.getId())), 404, "MEDECIN_INTROUVABLE", "/rendez-vous");
        }

        @Test
        void standardSansCreneauNiMedecin_400() throws Exception {
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","type":"PRESENTIEL"}"""), 400, "REQUETE_INVALIDE", "/rendez-vous");
        }

        @Test
        void typeIncompatibleOuMedecinIncoherent_400() throws Exception {
            Creneau presentiel = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","medecinId":"MED-001","creneauId":"%s","type":"TELECONSULTATION"}"""
                    .formatted(presentiel.getId())), 400, "TYPE_CRENEAU_INCOMPATIBLE", "/rendez-vous");
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","medecinId":"MED-002","creneauId":"%s","type":"PRESENTIEL"}"""
                    .formatted(presentiel.getId())), 400, "CRENEAU_INCOHERENT", "/rendez-vous");
        }

        @Test
        void corpsNonConformeAuContrat_400() throws Exception {
            ResultActions r = envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"P1","type":"PRESENTIEL"}""");
            erreur(r, 400, "REQUETE_INVALIDE", "/rendez-vous");
            r.andExpect(jsonPath("$.details[0].champ").value("identifiantPatient"));
            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0001","type":"VISIO"}"""), 400, "REQUETE_INVALIDE", "/rendez-vous");
            erreur(envoyer(post("/rendez-vous"), "{pas du json"), 400, "REQUETE_INVALIDE", "/rendez-vous");
        }

        @Test
        void urgenceSansMedecin_medecinTraitantEtCreneauDansLes2h() throws Exception {
            LocalDateTime avant = LocalDateTime.now(horloge).withNano(0);
            String json = envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0002","type":"URGENCE",
                     "motif":"Alerte ÉLEVÉE : tension systolique 172 mmHg","referenceAlerte":"ALR-2026-000317"}""")
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.medecinId").value("MED-002"))
                    .andExpect(jsonPath("$.type").value("URGENCE"))
                    .andExpect(jsonPath("$.referenceAlerte").value("ALR-2026-000317"))
                    .andReturn().getResponse().getContentAsString();
            String creneauId = com.jayway.jsonpath.JsonPath.read(json, "$.creneauId");
            LocalDateTime debut = creneauRepository.findById(creneauId).orElseThrow().getDebut();
            assertThat(debut).isAfterOrEqualTo(avant).isBeforeOrEqualTo(avant.plusHours(2));
        }

        @Test
        void urgenceSansCreneauLibreDansLes2h_409() throws Exception {
            LocalDateTime maintenant = LocalDateTime.now(horloge);
            creneauRepository.findByMedecinIdAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
                            "MED-002", maintenant.minusMinutes(1), maintenant.plusHours(3)).stream()
                    .filter(c -> c.getType() == TypeConsultation.URGENCE)
                    .forEach(c -> c.setDisponible(false));
            creneauRepository.flush();

            erreur(envoyer(post("/rendez-vous"), """
                    {"identifiantPatient":"PAT-0002","type":"URGENCE"}"""), 409, "AUCUN_CRENEAU_URGENCE", "/rendez-vous");
        }

        @Test
        void listerAvecFiltres() throws Exception {
            mvc.perform(get("/rendez-vous").param("patientId", "PAT-0001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].motif").value("Contrôle trimestriel"));
            mvc.perform(get("/rendez-vous").param("statut", "TERMINE"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].identifiantPatient").value("PAT-0005"));
            mvc.perform(get("/rendez-vous").param("medecinId", "MED-002")
                            .param("date", jourDeConsultation(3).toString()))
                    .andExpect(jsonPath("$[*].identifiantPatient", hasItem("PAT-0004")));
            erreur(mvc.perform(get("/rendez-vous").param("patientId", "X")), 400, "REQUETE_INVALIDE", "/rendez-vous");
        }

        @Test
        void consulter_404Et400() throws Exception {
            erreur(mvc.perform(get("/rendez-vous/RDV-2026-999999")), 404, "RDV_INTROUVABLE",
                    "/rendez-vous/RDV-2026-999999");
            erreur(mvc.perform(get("/rendez-vous/RDV-42")), 400, "REQUETE_INVALIDE", "/rendez-vous/RDV-42");
        }

        @Test
        void modifierLeCreneau_libereLAncien() throws Exception {
            Creneau ancien = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            Creneau nouveau = creneauLibre("MED-001", TypeConsultation.TELECONSULTATION, 2);
            String id = creerRendezVous("PAT-0001", ancien);

            envoyer(put("/rendez-vous/" + id), """
                    {"creneauId":"%s","type":"TELECONSULTATION","motif":"Contrôle (à distance)"}"""
                    .formatted(nouveau.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creneauId").value(nouveau.getId()))
                    .andExpect(jsonPath("$.type").value("TELECONSULTATION"))
                    .andExpect(jsonPath("$.motif").value("Contrôle (à distance)"));
            assertThat(creneauRepository.findById(ancien.getId()).orElseThrow().isDisponible()).isTrue();
            assertThat(creneauRepository.findById(nouveau.getId()).orElseThrow().isDisponible()).isFalse();
        }

        @Test
        void modifierVersUnCreneauDUnAutreMedecin_400() throws Exception {
            Creneau chezChaabane = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            Creneau chezHaddad = creneauLibre("MED-002", TypeConsultation.PRESENTIEL, 1);
            String id = creerRendezVous("PAT-0001", chezChaabane);

            ResultActions r = envoyer(put("/rendez-vous/" + id), """
                    {"creneauId":"%s"}""".formatted(chezHaddad.getId()));
            erreur(r, 400, "CRENEAU_INCOHERENT", "/rendez-vous/" + id);
            r.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("annuler puis recréer")));

            // rien n'a changé : le RDV garde son créneau, l'autre créneau reste libre
            mvc.perform(get("/rendez-vous/" + id))
                    .andExpect(jsonPath("$.creneauId").value(chezChaabane.getId()))
                    .andExpect(jsonPath("$.medecinId").value("MED-001"));
            assertThat(creneauRepository.findById(chezHaddad.getId()).orElseThrow().isDisponible()).isTrue();
            assertThat(creneauRepository.findById(chezChaabane.getId()).orElseThrow().isDisponible()).isFalse();
        }

        @Test
        void modifierVersUnCreneauPris_409() throws Exception {
            Creneau premier = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            String id = creerRendezVous("PAT-0001", premier);
            Creneau pris = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            creerRendezVous("PAT-0004", pris);

            erreur(envoyer(put("/rendez-vous/" + id), """
                    {"creneauId":"%s"}""".formatted(pris.getId())), 409, "CRENEAU_DEJA_RESERVE", "/rendez-vous/" + id);
            assertThat(creneauRepository.findById(premier.getId()).orElseThrow().isDisponible()).isFalse();
        }

        @Test
        void modifier_404() throws Exception {
            erreur(envoyer(put("/rendez-vous/RDV-2026-999999"), """
                    {"motif":"x"}"""), 404, "RDV_INTROUVABLE", "/rendez-vous/RDV-2026-999999");
        }

        @Test
        void annuler_204_puis409() throws Exception {
            Creneau creneau = creneauLibre("MED-001", TypeConsultation.PRESENTIEL, 1);
            String id = creerRendezVous("PAT-0001", creneau);

            mvc.perform(delete("/rendez-vous/" + id)).andExpect(status().isNoContent());
            mvc.perform(get("/rendez-vous/" + id)).andExpect(jsonPath("$.statut").value("ANNULE"));
            assertThat(creneauRepository.findById(creneau.getId()).orElseThrow().isDisponible()).isTrue();

            erreur(mvc.perform(delete("/rendez-vous/" + id)), 409, "RDV_NON_ANNULABLE", "/rendez-vous/" + id);
        }

        @Test
        void annulerUnRdvTermine_409() throws Exception {
            String termine = rendezVousRepository.findAll().stream()
                    .filter(r -> r.getStatut() == StatutRendezVous.TERMINE).findFirst().orElseThrow().getId();
            erreur(mvc.perform(delete("/rendez-vous/" + termine)), 409, "RDV_NON_ANNULABLE", "/rendez-vous/" + termine);
        }

        @Test
        void annuler_404() throws Exception {
            erreur(mvc.perform(delete("/rendez-vous/RDV-2026-999999")), 404, "RDV_INTROUVABLE",
                    "/rendez-vous/RDV-2026-999999");
        }
    }

    // ===================== Urgences =====================

    @Nested
    class Ambulances {

        private static final String DEMANDE = """
                {"identifiantPatient":"PAT-0001","referenceAlerte":"ALR-2026-000318",
                 "motif":"Alerte CRITIQUE : fréquence cardiaque 142 bpm",
                 "position":{"latitude":36.8008,"longitude":10.18}}""";

        @Test
        void demander_201_puisSuivre() throws Exception {
            String location = envoyer(post("/urgences/ambulances"), DEMANDE)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", matchesPattern("/urgences/ambulances/AMB-\\d{4}-\\d{6}")))
                    .andExpect(jsonPath("$.statut").value("DEPECHEE"))
                    .andExpect(jsonPath("$.adresse").value("12 rue de Marseille, 1000 Tunis"))
                    .andExpect(jsonPath("$.vehicule", matchesPattern("SAMU-TN-0[1-5]")))
                    .andExpect(jsonPath("$.delaiEstimeMinutes", org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.greaterThanOrEqualTo(8), org.hamcrest.Matchers.lessThanOrEqualTo(20))))
                    .andExpect(jsonPath("$.etablissementDestination").value("Hôpital Charles Nicolle"))
                    .andExpect(jsonPath("$.position.latitude").value(36.8008))
                    .andReturn().getResponse().getHeader("Location");

            mvc.perform(get(location))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.referenceAlerte").value("ALR-2026-000318"));
        }

        @Test
        void missionDejaEnCours_409() throws Exception {
            envoyer(post("/urgences/ambulances"), DEMANDE).andExpect(status().isCreated());
            erreur(envoyer(post("/urgences/ambulances"), DEMANDE), 409, "MISSION_DEJA_EN_COURS", "/urgences/ambulances");
        }

        @Test
        void patientInconnu_404() throws Exception {
            erreur(envoyer(post("/urgences/ambulances"), """
                    {"identifiantPatient":"PAT-0099","motif":"Chute"}"""), 404, "PATIENT_INTROUVABLE",
                    "/urgences/ambulances");
        }

        @Test
        void motifManquant_400() throws Exception {
            ResultActions r = envoyer(post("/urgences/ambulances"), """
                    {"identifiantPatient":"PAT-0001"}""");
            erreur(r, 400, "REQUETE_INVALIDE", "/urgences/ambulances");
            r.andExpect(jsonPath("$.details[0].champ").value("motif"));
        }

        @Test
        void consulter_404Et400() throws Exception {
            erreur(mvc.perform(get("/urgences/ambulances/AMB-2026-999999")), 404, "MISSION_INTROUVABLE",
                    "/urgences/ambulances/AMB-2026-999999");
            erreur(mvc.perform(get("/urgences/ambulances/AMB-7")), 400, "REQUETE_INVALIDE", "/urgences/ambulances/AMB-7");
        }
    }

    // ===================== Divers =====================

    @Test
    void routeInconnue_404AuFormatErreur() throws Exception {
        erreur(mvc.perform(get("/inconnu")), 404, "RESSOURCE_INTROUVABLE", "/inconnu");
    }

    @Test
    void contratPublie() throws Exception {
        mvc.perform(get("/openapi/portail-patient.yaml"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("openapi: 3.0.3")));
    }
}
