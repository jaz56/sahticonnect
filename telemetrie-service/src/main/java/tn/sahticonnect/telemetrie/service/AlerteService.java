package tn.sahticonnect.telemetrie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.telemetrie.domaine.MesureRecue;
import tn.sahticonnect.telemetrie.domaine.ResultatDetection;
import tn.sahticonnect.telemetrie.entity.Alerte;
import tn.sahticonnect.telemetrie.repository.AlerteRepository;
import tn.sahticonnect.telemetrie.repository.NumerotationRepository;

import java.time.Clock;
import java.time.LocalDate;

/** Création et persistance des alertes (identifiant ALR-AAAA-NNNNNN tiré d'une séquence de base). */
@Service
@RequiredArgsConstructor
public class AlerteService {

    private final AlerteRepository alerteRepository;
    private final NumerotationRepository numerotation;
    private final Clock horloge;

    @Transactional
    public Alerte enregistrer(MesureRecue mesure, ResultatDetection detection) {
        Alerte alerte = new Alerte();
        alerte.setId("ALR-%d-%06d".formatted(LocalDate.now(horloge).getYear(),
                numerotation.prochaineValeur(NumerotationRepository.SEQUENCE_ALERTES)));
        alerte.setIdentifiantPatient(mesure.identifiantPatient());
        alerte.setNiveau(detection.niveau());
        alerte.setConstante(mesure.constante());
        alerte.setValeur(mesure.valeur());
        alerte.setUnite(mesure.unite());
        alerte.setSeuilFranchi(detection.seuilFranchi());
        alerte.setMessage(message(mesure, detection));
        alerte.setHorodatage(mesure.horodatage());
        alerte.setCapteurId(mesure.capteurId());
        alerte.setSequenceMesure(mesure.sequence());
        return alerteRepository.save(alerte);
    }

    /** « Alerte CRITIQUE : fréquence cardiaque 142 bpm (FC > 130) », « Alerte CRITIQUE : chute détectée » */
    static String message(MesureRecue mesure, ResultatDetection detection) {
        String description = mesure.description();
        boolean seuilRedondant = description.equalsIgnoreCase(detection.seuilFranchi());
        return "Alerte " + detection.niveau() + " : " + description
                + (seuilRedondant ? "" : " (" + detection.seuilFranchi() + ")");
    }
}
