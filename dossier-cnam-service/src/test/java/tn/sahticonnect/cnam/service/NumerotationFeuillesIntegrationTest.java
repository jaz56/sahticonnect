package tn.sahticonnect.cnam.service;

import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.FeuilleDeSoins;
import tn.sahticonnect.cnam.entity.LettreCle;
import tn.sahticonnect.cnam.entity.StatutFeuille;
import tn.sahticonnect.cnam.repository.AssureRepository;
import tn.sahticonnect.cnam.repository.FeuilleDeSoinsRepository;
import tn.sahticonnect.cnam.repository.NumerotationRepository;
import tn.sahticonnect.cnam.service.model.DemandeFeuilleDeSoins;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Numérotation des feuilles de soins : séquence de base de données + contrainte d'unicité. */
@SpringBootTest
class NumerotationFeuillesIntegrationTest {

    @Autowired
    private DossierCnamService service;

    @Autowired
    private FeuilleDeSoinsRepository feuilleRepository;

    @Autowired
    private AssureRepository assureRepository;

    private static DemandeFeuilleDeSoins demande() {
        return new DemandeFeuilleDeSoins("PAT-0005", "VG-2026-000005", LocalDate.of(2026, 11, 23),
                "Hôpital Habib Thameur", "Dr Sami Kallel",
                List.of(new ActeCote(LettreCle.V, BigDecimal.ONE, "Visite à domicile")), null);
    }

    /** Partie numérique d'un numéro FS-AAAA-NNNNNN. */
    private static long rang(String numero) {
        return Long.parseLong(numero.substring("FS-2026-".length()));
    }

    @Test
    void deuxFeuillesSuccessivesOntDesNumerosDifferentsEtConsecutifs() {
        String premier = service.emettreFeuilleDeSoins(demande()).getNumero();
        String second = service.emettreFeuilleDeSoins(demande()).getNumero();

        assertThat(premier).matches("FS-2026-\\d{6}");
        assertThat(second).matches("FS-2026-\\d{6}").isNotEqualTo(premier);
        assertThat(rang(second)).isEqualTo(rang(premier) + 1);
        // les deux feuilles sont bien enregistrées sous ces numéros
        assertThat(feuilleRepository.findByNumero(premier)).isPresent();
        assertThat(feuilleRepository.findByNumero(second)).isPresent();
    }

    @Test
    void laRequeteDeSequenceEstAdapteeAPostgresql() {
        // profil docker : même séquence, syntaxe PostgreSQL fournie par le dialecte
        String requete = new PostgreSQLDialect().getSequenceSupport()
                .getSequenceNextValString(NumerotationRepository.SEQUENCE_FEUILLES);
        assertThat(requete).isEqualToIgnoringCase("select nextval('feuille_soins_numero_seq')");
    }

    @Test
    void laBaseRefuseUnNumeroEnDouble() {
        String existant = service.emettreFeuilleDeSoins(demande()).getNumero();

        FeuilleDeSoins doublon = new FeuilleDeSoins();
        doublon.setAssure(assureRepository.findById("PAT-0005").orElseThrow());
        doublon.setDateSoins(LocalDate.of(2026, 11, 23));
        doublon.setEtablissement("Hôpital Habib Thameur");
        doublon.setMedecin("Dr Sami Kallel");
        doublon.setMontantTotal(new BigDecimal("55.000"));
        doublon.setTauxApplique(80);
        doublon.setPartCNAM(new BigDecimal("44.000"));
        doublon.setPartPatient(new BigDecimal("11.000"));
        doublon.setStatut(StatutFeuille.CERTIFIEE);
        doublon.setCodeCertification("0".repeat(64));
        doublon.setNumero(existant);

        assertThatThrownBy(() -> feuilleRepository.saveAndFlush(doublon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
