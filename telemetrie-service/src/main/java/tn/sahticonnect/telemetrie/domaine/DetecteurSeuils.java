package tn.sahticonnect.telemetrie.domaine;

import org.springframework.stereotype.Component;

/**
 * Détection des seuils (règles de CLAUDE.md). Classe pure : aucun état, aucune dépendance,
 * testable avec {@code new DetecteurSeuils()}.
 *
 * <pre>
 *                CRITIQUE                    ÉLEVÉE
 * FC             &lt; 40 ou &gt; 130               110 à 130
 * SpO2           &lt; 90                        90 à 94
 * Systolique     &gt; 180 ou &lt; 90               160 à 180
 * Diastolique    &gt; 120                       &gt; 100 (101 à 120)
 * Température    &gt; 39,5                      38,5 à 39,5
 * Chute          valeur 1                    –
 * </pre>
 * Les bornes indiquées « à » sont incluses : FC 130 → ÉLEVÉE, FC 131 → CRITIQUE.
 */
@Component
public class DetecteurSeuils {

    public ResultatDetection evaluer(Constante constante, double valeur) {
        return switch (constante) {
            case FREQUENCE_CARDIAQUE -> frequenceCardiaque(valeur);
            case SPO2 -> spo2(valeur);
            case TENSION_SYSTOLIQUE -> systolique(valeur);
            case TENSION_DIASTOLIQUE -> diastolique(valeur);
            case TEMPERATURE -> temperature(valeur);
            case CHUTE -> valeur == 1 ? ResultatDetection.critique("Chute détectée") : ResultatDetection.NORMAL;
        };
    }

    private static ResultatDetection frequenceCardiaque(double fc) {
        if (fc < 40) {
            return ResultatDetection.critique("FC < 40");
        }
        if (fc > 130) {
            return ResultatDetection.critique("FC > 130");
        }
        if (fc >= 110) {
            return ResultatDetection.elevee("FC ≥ 110");
        }
        return ResultatDetection.NORMAL;
    }

    private static ResultatDetection spo2(double spo2) {
        if (spo2 < 90) {
            return ResultatDetection.critique("SpO2 < 90");
        }
        if (spo2 <= 94) {
            return ResultatDetection.elevee("SpO2 ≤ 94");
        }
        return ResultatDetection.NORMAL;
    }

    private static ResultatDetection systolique(double systolique) {
        if (systolique > 180) {
            return ResultatDetection.critique("Systolique > 180");
        }
        if (systolique < 90) {
            return ResultatDetection.critique("Systolique < 90");
        }
        if (systolique >= 160) {
            return ResultatDetection.elevee("Systolique ≥ 160");
        }
        return ResultatDetection.NORMAL;
    }

    private static ResultatDetection diastolique(double diastolique) {
        if (diastolique > 120) {
            return ResultatDetection.critique("Diastolique > 120");
        }
        if (diastolique > 100) {
            return ResultatDetection.elevee("Diastolique > 100");
        }
        return ResultatDetection.NORMAL;
    }

    private static ResultatDetection temperature(double temperature) {
        if (temperature > 39.5) {
            return ResultatDetection.critique("Température > 39,5");
        }
        if (temperature >= 38.5) {
            return ResultatDetection.elevee("Température ≥ 38,5");
        }
        return ResultatDetection.NORMAL;
    }
}
