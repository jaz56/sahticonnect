package tn.sahticonnect.telemetrie.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tn.sahticonnect.telemetrie.config.TelemetrieProperties;
import tn.sahticonnect.telemetrie.domaine.Constante;
import tn.sahticonnect.telemetrie.domaine.MesureRecue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Historique borné en mémoire et validation des mesures. */
class StockageEtValidationTest {

    private final TelemetrieProperties proprietes = new TelemetrieProperties("Africa/Tunis",
            Set.of("PAT-0001", "PAT-0002"), 100, Duration.ofSeconds(60));
    private final StockageMesures stockage = new StockageMesures(proprietes);
    private final ValidateurMesures validateur = new ValidateurMesures(proprietes);
    private final Instant debut = Instant.parse("2026-11-24T08:00:00Z");

    private MesureRecue mesure(Constante constante, int i) {
        return new MesureRecue("PAT-0001", "CAP-0001", constante, 70 + i, "bpm", debut.plusSeconds(i), i, null);
    }

    @Test
    void garde100MesuresParTypeLaPlusRecenteEnPremier() {
        for (int i = 1; i <= 150; i++) {
            stockage.ajouter(mesure(Constante.FREQUENCE_CARDIAQUE, i));
        }
        List<MesureRecue> toutes = stockage.dernieres("PAT-0001", List.of(Constante.FREQUENCE_CARDIAQUE), 1000);

        assertThat(toutes).hasSize(100);
        assertThat(toutes.getFirst().sequence()).isEqualTo(150);
        assertThat(toutes.getLast().sequence()).isEqualTo(51);
    }

    @Test
    void nombreParTypeEtTousLesTypesSiListeVide() {
        for (int i = 1; i <= 5; i++) {
            stockage.ajouter(mesure(Constante.FREQUENCE_CARDIAQUE, i));
            stockage.ajouter(mesure(Constante.SPO2, i + 100));
        }
        List<MesureRecue> resultat = stockage.dernieres("PAT-0001", List.of(), 2);

        assertThat(resultat).extracting(MesureRecue::sequence).containsExactly(105L, 104L, 5L, 4L);
        assertThat(stockage.dernieres("PAT-0002", List.of(), 10)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} : {2}")
    @CsvSource(delimiter = ';', value = {
            "PAT-0099; FREQUENCE_CARDIAQUE; 72;  Patient inconnu : PAT-0099",
            "PAT-0001; ;                    72;  Type de mesure non spécifié",
            "PAT-0001; FREQUENCE_CARDIAQUE; 301; Valeur hors plage physique pour fréquence cardiaque : 301 (attendu : 0 à 300 bpm)",
            "PAT-0001; FREQUENCE_CARDIAQUE; -1;  Valeur hors plage physique pour fréquence cardiaque : -1 (attendu : 0 à 300 bpm)",
            "PAT-0001; SPO2;                101; Valeur hors plage physique pour saturation en oxygène : 101 (attendu : 0 à 100 %)",
            "PAT-0001; TENSION_SYSTOLIQUE;  320; Valeur hors plage physique pour tension systolique : 320 (attendu : 0 à 300 mmHg)",
            "PAT-0001; TEMPERATURE;         24.9; Valeur hors plage physique pour température : 24,9 (attendu : 25 à 45 °C)",
            "PAT-0001; TEMPERATURE;         45.1; Valeur hors plage physique pour température : 45,1 (attendu : 25 à 45 °C)",
            "PAT-0001; CHUTE;               0.5; Valeur hors plage physique pour chute : 0,5 (attendu : 0 ou 1)",
    })
    void rejets(String patient, Constante constante, double valeur, String motif) {
        assertThat(validateur.motifDeRejet(patient, constante, valeur)).contains(motif);
    }

    @ParameterizedTest
    @CsvSource({"FREQUENCE_CARDIAQUE, 0", "FREQUENCE_CARDIAQUE, 300", "SPO2, 100", "TEMPERATURE, 25",
            "TEMPERATURE, 45", "TENSION_DIASTOLIQUE, 300", "CHUTE, 0", "CHUTE, 1"})
    void bornesPhysiquesAcceptees(Constante constante, double valeur) {
        assertThat(validateur.motifDeRejet("PAT-0001", constante, valeur)).isEmpty();
    }
}
