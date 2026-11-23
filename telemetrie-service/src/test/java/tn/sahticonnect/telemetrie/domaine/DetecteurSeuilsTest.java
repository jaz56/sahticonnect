package tn.sahticonnect.telemetrie.domaine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Toutes les bornes des seuils de CLAUDE.md, de part et d'autre de chaque limite. */
class DetecteurSeuilsTest {

    private final DetecteurSeuils detecteur = new DetecteurSeuils();

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource(delimiter = ';', value = {
            // ---------- Fréquence cardiaque : < 40 ou > 130 CRITIQUE ; 110 à 130 ÉLEVÉE ----------
            "FREQUENCE_CARDIAQUE; 39;    CRITIQUE; FC < 40",
            "FREQUENCE_CARDIAQUE; 39.9;  CRITIQUE; FC < 40",
            "FREQUENCE_CARDIAQUE; 40;    NORMAL;   ",
            "FREQUENCE_CARDIAQUE; 72;    NORMAL;   ",
            "FREQUENCE_CARDIAQUE; 109;   NORMAL;   ",
            "FREQUENCE_CARDIAQUE; 109.9; NORMAL;   ",
            "FREQUENCE_CARDIAQUE; 110;   ELEVEE;   FC ≥ 110",
            "FREQUENCE_CARDIAQUE; 130;   ELEVEE;   FC ≥ 110",
            "FREQUENCE_CARDIAQUE; 130.1; CRITIQUE; FC > 130",
            "FREQUENCE_CARDIAQUE; 131;   CRITIQUE; FC > 130",
            // ---------- SpO2 : < 90 CRITIQUE ; 90 à 94 ÉLEVÉE ----------
            "SPO2; 89;    CRITIQUE; SpO2 < 90",
            "SPO2; 89.9;  CRITIQUE; SpO2 < 90",
            "SPO2; 90;    ELEVEE;   SpO2 ≤ 94",
            "SPO2; 94;    ELEVEE;   SpO2 ≤ 94",
            "SPO2; 94.1;  NORMAL;   ",
            "SPO2; 95;    NORMAL;   ",
            "SPO2; 100;   NORMAL;   ",
            // ---------- Systolique : > 180 ou < 90 CRITIQUE ; 160 à 180 ÉLEVÉE ----------
            "TENSION_SYSTOLIQUE; 89;    CRITIQUE; Systolique < 90",
            "TENSION_SYSTOLIQUE; 90;    NORMAL;   ",
            "TENSION_SYSTOLIQUE; 159;   NORMAL;   ",
            "TENSION_SYSTOLIQUE; 160;   ELEVEE;   Systolique ≥ 160",
            "TENSION_SYSTOLIQUE; 180;   ELEVEE;   Systolique ≥ 160",
            "TENSION_SYSTOLIQUE; 181;   CRITIQUE; Systolique > 180",
            // ---------- Diastolique : > 120 CRITIQUE ; > 100 ÉLEVÉE ----------
            "TENSION_DIASTOLIQUE; 80;    NORMAL;   ",
            "TENSION_DIASTOLIQUE; 100;   NORMAL;   ",
            "TENSION_DIASTOLIQUE; 101;   ELEVEE;   Diastolique > 100",
            "TENSION_DIASTOLIQUE; 120;   ELEVEE;   Diastolique > 100",
            "TENSION_DIASTOLIQUE; 121;   CRITIQUE; Diastolique > 120",
            // ---------- Température : > 39,5 CRITIQUE ; 38,5 à 39,5 ÉLEVÉE ----------
            "TEMPERATURE; 36.8;  NORMAL;   ",
            "TEMPERATURE; 38.4;  NORMAL;   ",
            "TEMPERATURE; 38.5;  ELEVEE;   Température ≥ 38,5",
            "TEMPERATURE; 39.5;  ELEVEE;   Température ≥ 38,5",
            "TEMPERATURE; 39.6;  CRITIQUE; Température > 39,5",
            // ---------- Chute : 1 → CRITIQUE ----------
            "CHUTE; 0; NORMAL;   ",
            "CHUTE; 1; CRITIQUE; Chute détectée",
    })
    void seuils(Constante constante, double valeur, Niveau niveau, String seuil) {
        ResultatDetection resultat = detecteur.evaluer(constante, valeur);

        assertThat(resultat.niveau()).isEqualTo(niveau);
        assertThat(resultat.seuilFranchi()).isEqualTo(seuil);
    }
}
