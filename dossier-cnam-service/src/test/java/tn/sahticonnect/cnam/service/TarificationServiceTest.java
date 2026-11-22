package tn.sahticonnect.cnam.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.Assure;
import tn.sahticonnect.cnam.entity.Filiere;
import tn.sahticonnect.cnam.entity.LettreCle;
import tn.sahticonnect.cnam.exception.ActeInvalideException;
import tn.sahticonnect.cnam.service.model.Montants;
import tn.sahticonnect.cnam.service.model.ResultatCouverture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitaires des règles de tarification (sans Spring). */
class TarificationServiceTest {

    private static final LocalDate DATE_ACTE = LocalDate.of(2026, 11, 22);

    private final TarificationService tarification = new TarificationService();

    private static ActeCote acte(LettreCle lettreCle, String coefficient) {
        return new ActeCote(lettreCle, new BigDecimal(coefficient), null);
    }

    private static Assure assure(Filiere filiere, boolean apci, LocalDate finValidite) {
        return Assure.builder().identifiantPatient("PAT-0099").filiere(filiere).apci(apci)
                .dateFinValidite(finValidite).build();
    }

    @Nested
    @DisplayName("Montant des actes")
    class MontantActes {

        @ParameterizedTest(name = "{0} {1} = {2} DT")
        @CsvSource({
                "C,  1,   45.000",
                "CS, 1,   60.000",
                "V,  1,   55.000",
                "K,  20,  60.000",
                "B,  7,   2.100",
                "Z,  30,  60.000",
                "B,  3.3335, 1.000"   // 0,3 × 3,3335 = 1,00005 → 1,000 (HALF_UP à 3 décimales)
        })
        void valeurLettreCleFoisCoefficient(LettreCle lettreCle, String coefficient, String attendu) {
            assertThat(tarification.montantActe(acte(lettreCle, coefficient))).isEqualByComparingTo(attendu)
                    .hasScaleOf(3);
        }

        @Test
        void totalEstLaSommeDesActes() {
            BigDecimal total = tarification.montantTotal(List.of(acte(LettreCle.CS, "1"), acte(LettreCle.K, "10")));
            assertThat(total).isEqualByComparingTo("90.000").hasScaleOf(3);
        }

        @ParameterizedTest
        @CsvSource({"0", "-2"})
        void coefficientNulOuNegatifRefuse(String coefficient) {
            assertThatThrownBy(() -> tarification.montantActe(acte(LettreCle.K, coefficient)))
                    .isInstanceOf(ActeInvalideException.class)
                    .hasMessageContaining("strictement positif");
        }
    }

    @Nested
    @DisplayName("Taux de remboursement")
    class Taux {

        @Test
        void apciDonne100MemeEnFilierePrivee() {
            ResultatCouverture c = tarification.evaluerCouverture(
                    assure(Filiere.PRIVEE, true, DATE_ACTE.plusYears(1)), DATE_ACTE);
            assertThat(c.couvert()).isTrue();
            assertThat(c.taux()).isEqualTo(100);
            assertThat(c.motif()).isEqualTo("APCI 100 %");
        }

        @Test
        void filierePubliqueDonne80() {
            ResultatCouverture c = tarification.evaluerCouverture(
                    assure(Filiere.PUBLIQUE, false, DATE_ACTE.plusYears(1)), DATE_ACTE);
            assertThat(c.taux()).isEqualTo(80);
            assertThat(c.motif()).isEqualTo("Filière publique 80 %");
        }

        @ParameterizedTest
        @CsvSource({"PRIVEE", "REMBOURSEMENT"})
        void autresFilieresDonnent50(Filiere filiere) {
            ResultatCouverture c = tarification.evaluerCouverture(
                    assure(filiere, false, DATE_ACTE.plusYears(1)), DATE_ACTE);
            assertThat(c.couvert()).isTrue();
            assertThat(c.taux()).isEqualTo(50);
        }

        @Test
        void vignetteExpireeDonneNonCouvertEtTaux0() {
            ResultatCouverture c = tarification.evaluerCouverture(
                    assure(Filiere.PUBLIQUE, true, LocalDate.of(2026, 6, 30)), DATE_ACTE);
            assertThat(c.couvert()).isFalse();
            assertThat(c.taux()).isZero();
            assertThat(c.motif()).isEqualTo("Vignette expirée le 30/06/2026");
        }

        @Test
        void vignetteValableJusquAuDernierJourInclus() {
            ResultatCouverture c = tarification.evaluerCouverture(
                    assure(Filiere.PUBLIQUE, false, DATE_ACTE), DATE_ACTE);
            assertThat(c.couvert()).isTrue();
            assertThat(c.taux()).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("Répartition et arrondis")
    class Repartition {

        @Test
        void repartitionSimple() {
            Montants m = tarification.repartir(new BigDecimal("60.000"), 80);
            assertThat(m.partCNAM()).isEqualByComparingTo("48.000");
            assertThat(m.partPatient()).isEqualByComparingTo("12.000");
        }

        @Test
        void arrondiHalfUpSurLaPartCnam() {
            // 0,375 × 50 % = 0,1875 → 0,188 (HALF_UP) ; part patient = 0,375 − 0,188 = 0,187
            Montants m = tarification.repartir(new BigDecimal("0.375"), 50);
            assertThat(m.partCNAM()).isEqualByComparingTo("0.188").hasScaleOf(3);
            assertThat(m.partPatient()).isEqualByComparingTo("0.187").hasScaleOf(3);
        }

        @Test
        void partsToujoursEgalesAuTotal() {
            Montants m = tarification.repartir(new BigDecimal("13.337"), 80);
            assertThat(m.partCNAM().add(m.partPatient())).isEqualByComparingTo(m.total());
        }

        @Test
        void taux0ToutALaChargeDuPatient() {
            Montants m = tarification.repartir(new BigDecimal("45"), 0);
            assertThat(m.total()).isEqualByComparingTo("45.000").hasScaleOf(3);
            assertThat(m.partCNAM()).isEqualByComparingTo("0.000");
            assertThat(m.partPatient()).isEqualByComparingTo("45.000");
        }
    }
}
