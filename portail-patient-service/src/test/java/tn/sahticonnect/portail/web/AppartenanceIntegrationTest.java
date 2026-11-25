package tn.sahticonnect.portail.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.RendezVous;
import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.repository.RendezVousRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrôle d'appartenance : avec l'en-tête X-Patient-Id (posé par la gateway pour un PATIENT), un patient ne lit et ne
 * modifie que ses données ; sans en-tête (orchestrateur, médecin, admin), accès complet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppartenanceIntegrationTest {

    private static final String AHMED = "PAT-0001";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RendezVousRepository rendezVousRepository;

    private ResultActions enTantQueAhmed(MockHttpServletRequestBuilder requete) throws Exception {
        return mvc.perform(requete.header(Appartenance.ENTETE, AHMED));
    }

    private ResultActions enTantQueAhmed(MockHttpServletRequestBuilder requete, String json) throws Exception {
        return enTantQueAhmed(requete.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void accesRefuse(ResultActions resultat) throws Exception {
        resultat.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statut").value(403))
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PAT-0001")));
    }

    /** Un rendez-vous de démonstration d'un autre patient (Sonia, PAT-0004). */
    private RendezVous rendezVousDeSonia() {
        return rendezVousRepository.findAll().stream()
                .filter(r -> r.getPatient().getId().equals("PAT-0004") && r.getStatut() == StatutRendezVous.CONFIRME)
                .findFirst().orElseThrow();
    }

    // ===================== Profil et ordonnances =====================

    @Test
    void patient_sonProfil_ok_celuiDUnAutre_refuse() throws Exception {
        enTantQueAhmed(get("/patients/PAT-0001")).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Ben Salah"));
        accesRefuse(enTantQueAhmed(get("/patients/PAT-0002")));
        accesRefuse(enTantQueAhmed(put("/patients/PAT-0002"), "{\"telephone\":\"+216 20 000 000\"}"));
        accesRefuse(enTantQueAhmed(get("/patients/PAT-0002/ordonnances")));
        enTantQueAhmed(get("/patients/PAT-0001/ordonnances")).andExpect(status().isOk());
    }

    // ===================== Rendez-vous =====================

    @Test
    void listeDesRendezVous_filtreeSurLePatientConnecte() throws Exception {
        enTantQueAhmed(get("/rendez-vous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[*].identifiantPatient", everyItem(is(AHMED))));
        accesRefuse(enTantQueAhmed(get("/rendez-vous").param("patientId", "PAT-0004")));
    }

    @Test
    void rendezVousDUnAutrePatient_niLu_niModifie_niAnnule() throws Exception {
        RendezVous rdv = rendezVousDeSonia();

        accesRefuse(enTantQueAhmed(get("/rendez-vous/" + rdv.getId())));
        accesRefuse(enTantQueAhmed(put("/rendez-vous/" + rdv.getId()), "{\"motif\":\"piratage\"}"));
        accesRefuse(enTantQueAhmed(delete("/rendez-vous/" + rdv.getId())));

        RendezVous apres = rendezVousRepository.findById(rdv.getId()).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo(StatutRendezVous.CONFIRME);
        assertThat(apres.getMotif()).isNotEqualTo("piratage");
    }

    @Test
    void priseDeRendezVous_seulementASonNom() throws Exception {
        accesRefuse(enTantQueAhmed(post("/rendez-vous"),
                "{\"identifiantPatient\":\"PAT-0002\",\"type\":\"URGENCE\",\"motif\":\"au nom d'un autre\"}"));
        enTantQueAhmed(post("/rendez-vous"), "{\"identifiantPatient\":\"PAT-0001\",\"type\":\"URGENCE\",\"motif\":\"douleur\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifiantPatient").value(AHMED));
    }

    // ===================== Ambulances =====================

    @Test
    void ambulance_seulementASonNom_etSesMissions() throws Exception {
        accesRefuse(enTantQueAhmed(post("/urgences/ambulances"),
                "{\"identifiantPatient\":\"PAT-0005\",\"motif\":\"chute\"}"));

        // mission de Hedi créée par l'orchestrateur (appel interne, sans en-tête)
        String reponse = mvc.perform(post("/urgences/ambulances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiantPatient\":\"PAT-0005\",\"motif\":\"Alerte CRITIQUE : chute détectée\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mission = reponse.replaceAll(".*\"id\":\"(AMB-\\d{4}-\\d{6})\".*", "$1");

        accesRefuse(enTantQueAhmed(get("/urgences/ambulances/" + mission)));
    }

    // ===================== Sans en-tête : accès complet =====================

    @Test
    void sansEnTete_appelInterneMedecinOuAdmin_accesComplet() throws Exception {
        mvc.perform(get("/patients/PAT-0002")).andExpect(status().isOk());
        mvc.perform(get("/rendez-vous").param("patientId", "PAT-0004")).andExpect(status().isOk());
        mvc.perform(get("/rendez-vous/" + rendezVousDeSonia().getId())).andExpect(status().isOk());
    }

    @Test
    void donneesPubliques_medecinsEtCreneaux_accessiblesAuPatient() throws Exception {
        enTantQueAhmed(get("/medecins")).andExpect(status().isOk());
    }
}
