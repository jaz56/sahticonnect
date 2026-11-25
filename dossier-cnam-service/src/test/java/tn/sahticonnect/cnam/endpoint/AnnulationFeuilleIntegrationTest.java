package tn.sahticonnect.cnam.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import tn.sahticonnect.cnam.entity.StatutFeuille;
import tn.sahticonnect.cnam.repository.EntreeHistoriqueRepository;
import tn.sahticonnect.cnam.repository.FeuilleDeSoinsRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.validPayload;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/** annulerFeuilleDeSoins (compensation d'une saga) : cas normal, feuille inconnue, feuille déjà annulée. */
@SpringBootTest
class AnnulationFeuilleIntegrationTest {

    private static final String NS = "http://sahticonnect.tn/cnam";
    private static final Map<String, String> PREFIXES = Map.of("c", NS);

    @Autowired
    private ApplicationContext contexte;
    @Autowired
    private FeuilleDeSoinsRepository feuilleRepository;
    @Autowired
    private EntreeHistoriqueRepository historiqueRepository;

    private MockWebServiceClient client;

    @BeforeEach
    void creerClient() {
        client = MockWebServiceClient.createClient(contexte);
    }

    private static StringSource payload(String xml) {
        return new StringSource(xml.replace("{ns}", NS));
    }

    /** Émet une feuille CS 1 pour PAT-0005 (filière publique) et renvoie son numéro. */
    private String emettreFeuille() throws Exception {
        client.sendRequest(withPayload(payload("""
                <c:emettreFeuilleDeSoinsRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0005</c:identifiantPatient>
                  <c:codeVignetteCNAM>VG-2026-000005</c:codeVignetteCNAM>
                  <c:dateSoins>2026-11-24</c:dateSoins>
                  <c:etablissement>SahtiConnect – intervention à domicile</c:etablissement>
                  <c:medecin>Dr Ines Haddad</c:medecin>
                  <c:actes><c:lettreCle>CS</c:lettreCle><c:coefficient>1</c:coefficient></c:actes>
                  <c:referenceIntervention>INT-2026-000042</c:referenceIntervention>
                </c:emettreFeuilleDeSoinsRequest>""")))
                .andExpect(noFault());
        // le numéro est relu en base (dernière feuille de PAT-0005)
        return feuilleRepository.findAll().stream()
                .filter(f -> f.getAssure().getIdentifiantPatient().equals("PAT-0005"))
                .map(f -> f.getNumero())
                .filter(n -> n.matches("FS-\\d{4}-0\\d{5}"))
                .reduce((premier, second) -> second)
                .orElseThrow();
    }

    private String annuler(String numero) {
        return """
                <c:annulerFeuilleDeSoinsRequest xmlns:c="{ns}">
                  <c:numero>%s</c:numero>
                  <c:motif>Escalade vers ambulance WF-2026-000002</c:motif>
                </c:annulerFeuilleDeSoinsRequest>""".formatted(numero);
    }

    @Test
    void annulation_feuilleAnnulee_etLigneAjouteeALHistorique() throws Exception {
        String numero = emettreFeuille();
        long historiqueAvant = historiqueRepository.count();

        client.sendRequest(withPayload(payload(annuler(numero))))
                .andExpect(noFault())
                .andExpect(validPayload(new ClassPathResource("xsd/dossier-cnam.xsd")))
                .andExpect(xpath("//c:annulerFeuilleDeSoinsResponse/c:feuilleDeSoins/c:numero", PREFIXES).evaluatesTo(numero))
                .andExpect(xpath("//c:feuilleDeSoins/c:statut", PREFIXES).evaluatesTo("ANNULEE"))
                .andExpect(xpath("//c:feuilleDeSoins/c:montantTotal", PREFIXES).evaluatesTo("60.000"))
                .andExpect(xpath("count(//c:feuilleDeSoins/c:lignes)", PREFIXES).evaluatesTo(1));

        assertThat(feuilleRepository.findByNumero(numero)).get()
                .extracting(f -> f.getStatut()).isEqualTo(StatutFeuille.ANNULEE);
        assertThat(historiqueRepository.count()).isEqualTo(historiqueAvant + 1);

        // la ligne d'annulation apparaît dans consulterHistorique
        client.sendRequest(withPayload(payload("""
                <c:consulterHistoriqueRequest xmlns:c="{ns}">
                  <c:identifiantPatient>PAT-0005</c:identifiantPatient>
                </c:consulterHistoriqueRequest>""")))
                .andExpect(noFault())
                .andExpect(xpath("count(//c:entrees[c:numeroFeuilleSoins='%s'])".formatted(numero), PREFIXES).evaluatesTo(2))
                .andExpect(xpath("//c:entrees[starts-with(c:diagnostic, 'Annulation de la feuille %s')]/c:actes/c:lettreCle"
                        .formatted(numero), PREFIXES).evaluatesTo("CS"));
    }

    @Test
    void annulation_feuilleDejaAnnulee_renvoyeeTelleQuelle_sansNouvelleLigne() throws Exception {
        String numero = emettreFeuille();
        client.sendRequest(withPayload(payload(annuler(numero)))).andExpect(noFault());
        long historiqueApresPremiere = historiqueRepository.count();

        client.sendRequest(withPayload(payload(annuler(numero))))
                .andExpect(noFault())
                .andExpect(xpath("//c:feuilleDeSoins/c:statut", PREFIXES).evaluatesTo("ANNULEE"));

        assertThat(historiqueRepository.count()).as("idempotent : pas de 2e ligne").isEqualTo(historiqueApresPremiere);
    }

    @Test
    void annulation_feuilleInconnue_faultFeuilleInconnue() throws Exception {
        client.sendRequest(withPayload(payload(annuler("FS-2026-999999"))))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//faultcode", Map.of()).evaluatesTo("SOAP-ENV:Client"))
                .andExpect(xpath("//detail/c:annulerFeuilleDeSoinsFault/c:code", PREFIXES).evaluatesTo("FEUILLE_INCONNUE"))
                .andExpect(xpath("//detail/c:annulerFeuilleDeSoinsFault/c:message", PREFIXES)
                        .evaluatesTo("Feuille de soins inconnue : FS-2026-999999"));
    }
}
