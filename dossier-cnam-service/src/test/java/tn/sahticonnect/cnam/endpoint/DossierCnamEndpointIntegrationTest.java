package tn.sahticonnect.cnam.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import tn.sahticonnect.cnam.repository.FeuilleDeSoinsRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.validPayload;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/**
 * Tests d'intégration SOAP : contexte Spring complet (H2 + données de test),
 * messages envoyés au MessageDispatcher via MockWebServiceClient.
 * Chaque test utilise un patient différent pour rester indépendant des autres.
 */
@SpringBootTest
class DossierCnamEndpointIntegrationTest {

    private static final String NS = "http://sahticonnect.tn/cnam";
    private static final Map<String, String> PREFIXES = Map.of("c", NS);

    @Autowired
    private ApplicationContext contexte;

    @Autowired
    private FeuilleDeSoinsRepository feuilleRepository;

    private MockWebServiceClient client;

    @BeforeEach
    void creerClient() {
        client = MockWebServiceClient.createClient(contexte);
    }

    private static StringSource payload(String xml) {
        return new StringSource(xml.replace("{ns}", NS));
    }

    private static String acte(String lettre, String coefficient) {
        return "<c:lettreCle>" + lettre + "</c:lettreCle><c:coefficient>" + coefficient + "</c:coefficient>";
    }

    // ===================== verifierDroitsCNAM =====================

    @Test
    void verifierDroits_patientApci_couvertA100() throws Exception {
        client.sendRequest(withPayload(payload("""
                <c:verifierDroitsCNAMRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0001</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000001</c:codeVignetteCNAM>
                  <c:acte>%s</c:acte>
                  <c:dateActe>2026-11-22</c:dateActe>
                </c:verifierDroitsCNAMRequest>""".formatted(acte("C", "1")))))
                .andExpect(noFault())
                .andExpect(validPayload(new ClassPathResource("xsd/dossier-cnam.xsd")))
                .andExpect(xpath("//c:assure/c:nom", PREFIXES).evaluatesTo("Ben Salah"))
                .andExpect(xpath("//c:couverture/c:couvert", PREFIXES).evaluatesTo("true"))
                .andExpect(xpath("//c:couverture/c:tauxRemboursement", PREFIXES).evaluatesTo("100"))
                .andExpect(xpath("//c:couverture/c:motif", PREFIXES).evaluatesTo("APCI 100 %"))
                .andExpect(xpath("//c:montantEstime", PREFIXES).evaluatesTo("45.000"))
                .andExpect(xpath("//c:partCNAMEstimee", PREFIXES).evaluatesTo("45.000"))
                .andExpect(xpath("//c:partPatientEstimee", PREFIXES).evaluatesTo("0.000"));
    }

    @Test
    void verifierDroits_vignetteExpiree_nonCouvert() {
        client.sendRequest(withPayload(payload("""
                <c:verifierDroitsCNAMRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0002</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000002</c:codeVignetteCNAM>
                  <c:acte>%s</c:acte>
                  <c:dateActe>2026-11-22</c:dateActe>
                </c:verifierDroitsCNAMRequest>""".formatted(acte("CS", "1")))))
                .andExpect(noFault())
                .andExpect(xpath("//c:couverture/c:couvert", PREFIXES).evaluatesTo("false"))
                .andExpect(xpath("//c:couverture/c:tauxRemboursement", PREFIXES).evaluatesTo("0"))
                .andExpect(xpath("//c:couverture/c:motif", PREFIXES).evaluatesTo("Vignette expirée le 30/06/2026"))
                .andExpect(xpath("//c:partCNAMEstimee", PREFIXES).evaluatesTo("0.000"))
                .andExpect(xpath("//c:partPatientEstimee", PREFIXES).evaluatesTo("60.000"));
    }

    // ===================== emettreFeuilleDeSoins =====================

    @Test
    void emettreFeuille_filierePublique_certifieeEtAjouteeALHistorique() {
        client.sendRequest(withPayload(payload("""
                <c:emettreFeuilleDeSoinsRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0003</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000003</c:codeVignetteCNAM>
                  <c:dateSoins>2026-11-20</c:dateSoins>
                  <c:etablissement>Hôpital Abderrahmen Mami</c:etablissement>
                  <c:medecin>Dr Sami Kallel</c:medecin>
                  <c:actes>%s</c:actes>
                  <c:actes>%s</c:actes>
                  <c:referenceIntervention>INT-2026-000001</c:referenceIntervention>
                </c:emettreFeuilleDeSoinsRequest>""".formatted(acte("CS", "1"), acte("K", "10")))))
                .andExpect(noFault())
                .andExpect(xpath("//c:feuilleDeSoins/c:numero", PREFIXES).evaluatesTo(true)) // présent
                .andExpect(xpath("starts-with(//c:feuilleDeSoins/c:numero, 'FS-2026-')", PREFIXES).evaluatesTo(true))
                .andExpect(xpath("count(//c:feuilleDeSoins/c:lignes)", PREFIXES).evaluatesTo(2))
                .andExpect(xpath("//c:feuilleDeSoins/c:lignes[2]/c:montant", PREFIXES).evaluatesTo("30.000"))
                .andExpect(xpath("//c:feuilleDeSoins/c:montantTotal", PREFIXES).evaluatesTo("90.000"))
                .andExpect(xpath("//c:feuilleDeSoins/c:tauxApplique", PREFIXES).evaluatesTo("80"))
                .andExpect(xpath("//c:feuilleDeSoins/c:partCNAM", PREFIXES).evaluatesTo("72.000"))
                .andExpect(xpath("//c:feuilleDeSoins/c:partPatient", PREFIXES).evaluatesTo("18.000"))
                .andExpect(xpath("//c:feuilleDeSoins/c:statut", PREFIXES).evaluatesTo("CERTIFIEE"))
                .andExpect(xpath("string-length(//c:feuilleDeSoins/c:codeCertification)", PREFIXES).evaluatesTo(64));

        // la feuille est enregistrée et figure dans l'historique du patient
        assertThat(feuilleRepository.findAll())
                .anyMatch(f -> f.getAssure().getIdentifiantPatient().equals("PAT-0003")
                        && f.getNumero().startsWith("FS-2026-"));
        client.sendRequest(withPayload(payload("""
                <c:consulterHistoriqueRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0003</c:identifiantPatient>
                  <c:dateDebut>2026-11-20</c:dateDebut>
                </c:consulterHistoriqueRequest>""")))
                .andExpect(noFault())
                .andExpect(xpath("count(//c:entrees)", PREFIXES).evaluatesTo(1))
                .andExpect(xpath("starts-with(//c:entrees[1]/c:numeroFeuilleSoins, 'FS-2026-')", PREFIXES)
                        .evaluatesTo(true))
                .andExpect(xpath("count(//c:entrees[1]/c:actes)", PREFIXES).evaluatesTo(2));
    }

    @Test
    void emettreFeuille_patientNonCouvert_emiseATaux0() {
        client.sendRequest(withPayload(payload("""
                <c:emettreFeuilleDeSoinsRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0002</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000002</c:codeVignetteCNAM>
                  <c:dateSoins>2026-11-21</c:dateSoins>
                  <c:etablissement>Clinique Les Oliviers</c:etablissement>
                  <c:medecin>Dr Ines Haddad</c:medecin>
                  <c:actes>%s</c:actes>
                </c:emettreFeuilleDeSoinsRequest>""".formatted(acte("V", "1")))))
                .andExpect(noFault())
                .andExpect(xpath("//c:feuilleDeSoins/c:statut", PREFIXES).evaluatesTo("EMISE"))
                .andExpect(xpath("//c:feuilleDeSoins/c:tauxApplique", PREFIXES).evaluatesTo("0"))
                .andExpect(xpath("//c:feuilleDeSoins/c:partCNAM", PREFIXES).evaluatesTo("0.000"))
                .andExpect(xpath("//c:feuilleDeSoins/c:partPatient", PREFIXES).evaluatesTo("55.000"));
    }

    // ===================== consulterHistorique =====================

    @Test
    void consulterHistorique_trieDuPlusRecentAuPlusAncien() {
        client.sendRequest(withPayload(payload("""
                <c:consulterHistoriqueRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0004</c:identifiantPatient>
                </c:consulterHistoriqueRequest>""")))
                .andExpect(noFault())
                .andExpect(xpath("//c:assure/c:filiere", PREFIXES).evaluatesTo("REMBOURSEMENT"))
                .andExpect(xpath("count(//c:entrees)", PREFIXES).evaluatesTo(3))
                .andExpect(xpath("//c:entrees[1]/c:date", PREFIXES).evaluatesTo("2026-10-04"))
                .andExpect(xpath("//c:entrees[2]/c:date", PREFIXES).evaluatesTo("2026-06-21"))
                .andExpect(xpath("//c:entrees[3]/c:date", PREFIXES).evaluatesTo("2026-03-15"));
    }

    @Test
    void consulterHistorique_filtreParDates() {
        client.sendRequest(withPayload(payload("""
                <c:consulterHistoriqueRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0004</c:identifiantPatient>
                  <c:dateDebut>2026-04-01</c:dateDebut>
                  <c:dateFin>2026-09-30</c:dateFin>
                </c:consulterHistoriqueRequest>""")))
                .andExpect(noFault())
                .andExpect(xpath("count(//c:entrees)", PREFIXES).evaluatesTo(1))
                .andExpect(xpath("//c:entrees[1]/c:diagnostic", PREFIXES).evaluatesTo("Diabète – suivi trimestriel"));
    }

    // ===================== comparerGaranties =====================

    @Test
    void comparerGaranties_recommandeLaMoinsChere() {
        client.sendRequest(withPayload(payload(comparaison("PAT-0004"))))
                .andExpect(noFault())
                .andExpect(xpath("//c:resultatA/c:tauxApplique", PREFIXES).evaluatesTo("80"))
                .andExpect(xpath("//c:resultatA/c:montantTotal", PREFIXES).evaluatesTo("51.000"))
                .andExpect(xpath("//c:resultatA/c:partPatient", PREFIXES).evaluatesTo("10.200"))
                .andExpect(xpath("//c:resultatB/c:tauxApplique", PREFIXES).evaluatesTo("50"))
                .andExpect(xpath("//c:resultatB/c:partPatient", PREFIXES).evaluatesTo("25.500"))
                .andExpect(xpath("//c:differencePartPatient", PREFIXES).evaluatesTo("15.300"))
                .andExpect(xpath("//c:recommandation", PREFIXES).evaluatesTo("Hôpital public"));
    }

    @Test
    void comparerGaranties_patientApci_equivalent() {
        client.sendRequest(withPayload(payload(comparaison("PAT-0001"))))
                .andExpect(noFault())
                .andExpect(xpath("//c:resultatA/c:tauxApplique", PREFIXES).evaluatesTo("100"))
                .andExpect(xpath("//c:resultatB/c:tauxApplique", PREFIXES).evaluatesTo("100"))
                .andExpect(xpath("//c:differencePartPatient", PREFIXES).evaluatesTo("0.000"))
                .andExpect(xpath("//c:recommandation", PREFIXES).evaluatesTo("Équivalent"));
    }

    /** Hôpital public (PUBLIQUE) vs clinique privée (PRIVEE), mêmes actes : C 1 + B 20 = 51 DT. */
    private static String comparaison(String patient) {
        return """
                <c:comparerGarantiesRequest xmlns:c="{ns}">
                  <c:identifiantPatient>%s</c:identifiantPatient>
                  <c:priseEnChargeA>
                    <c:libelle>Hôpital public</c:libelle>
                    <c:filiere>PUBLIQUE</c:filiere>
                    <c:actes>%s</c:actes>
                    <c:actes>%s</c:actes>
                  </c:priseEnChargeA>
                  <c:priseEnChargeB>
                    <c:libelle>Clinique privée</c:libelle>
                    <c:filiere>PRIVEE</c:filiere>
                    <c:actes>%s</c:actes>
                    <c:actes>%s</c:actes>
                  </c:priseEnChargeB>
                </c:comparerGarantiesRequest>""".formatted(patient,
                acte("C", "1"), acte("B", "20"), acte("C", "1"), acte("B", "20"));
    }

    // ===================== SOAP Faults =====================

    @Test
    void fault_patientInconnu() {
        client.sendRequest(withPayload(payload("""
                <c:consulterHistoriqueRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0099</c:identifiantPatient>
                </c:consulterHistoriqueRequest>""")))
                .andExpect(clientOrSenderFault("Patient inconnu : PAT-0099"))
                .andExpect(xpath("//detail/c:consulterHistoriqueFault/c:code", PREFIXES).evaluatesTo("PATIENT_INCONNU"))
                .andExpect(xpath("//detail/c:consulterHistoriqueFault/c:message", PREFIXES)
                        .evaluatesTo("Patient inconnu : PAT-0099"));
    }

    @Test
    void fault_vignetteNeCorrespondPasAuPatient() {
        client.sendRequest(withPayload(payload("""
                <c:verifierDroitsCNAMRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0001</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000003</c:codeVignetteCNAM>
                  <c:acte>%s</c:acte>
                </c:verifierDroitsCNAMRequest>""".formatted(acte("C", "1")))))
                .andExpect(clientOrSenderFault("La vignette VG-2026-000003 ne correspond pas au patient PAT-0001"))
                .andExpect(xpath("//detail/c:verifierDroitsCNAMFault/c:code", PREFIXES).evaluatesTo("VIGNETTE_INVALIDE"));
    }

    @Test
    void fault_acteAvecCoefficientNul() {
        client.sendRequest(withPayload(payload("""
                <c:emettreFeuilleDeSoinsRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0005</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000005</c:codeVignetteCNAM>
                  <c:dateSoins>2026-11-21</c:dateSoins>
                  <c:etablissement>Hôpital Habib Thameur</c:etablissement>
                  <c:medecin>Dr Sami Kallel</c:medecin>
                  <c:actes>%s</c:actes>
                </c:emettreFeuilleDeSoinsRequest>""".formatted(acte("K", "0")))))
                .andExpect(clientOrSenderFault(
                        "Acte invalide : le coefficient de K doit être strictement positif (reçu : 0)"))
                .andExpect(xpath("//detail/c:emettreFeuilleDeSoinsFault/c:code", PREFIXES).evaluatesTo("ACTE_INVALIDE"));
    }

    @Test
    void fault_patientInconnu_comparerGaranties() {
        client.sendRequest(withPayload(payload(comparaison("PAT-0042"))))
                .andExpect(clientOrSenderFault("Patient inconnu : PAT-0042"))
                .andExpect(xpath("//detail/c:comparerGarantiesFault/c:code", PREFIXES).evaluatesTo("PATIENT_INCONNU"));
    }

    @Test
    void fault_requeteNonConformeAuXsd() {
        // identifiant au mauvais format : rejeté par la validation XSD avant d'atteindre l'endpoint
        client.sendRequest(withPayload(payload("""
                <c:consulterHistoriqueRequest xmlns:c="{ns}">
                  <c:identifiantPatient>P1</c:identifiantPatient>
                </c:consulterHistoriqueRequest>""")))
                .andExpect(clientOrSenderFault());
    }
}
