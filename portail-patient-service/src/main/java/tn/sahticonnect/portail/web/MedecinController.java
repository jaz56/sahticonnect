package tn.sahticonnect.portail.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tn.sahticonnect.portail.api.CrneauxApi;
import tn.sahticonnect.portail.api.MdecinsApi;
import tn.sahticonnect.portail.api.model.CreneauDto;
import tn.sahticonnect.portail.api.model.MedecinDto;
import tn.sahticonnect.portail.api.model.TypeConsultationDto;
import tn.sahticonnect.portail.service.MedecinService;

import java.time.LocalDate;
import java.util.List;

/**
 * Annuaire des médecins et créneaux.
 * MdecinsApi / CrneauxApi : noms générés depuis les tags « Médecins » / « Créneaux » du contrat
 * (le générateur supprime les lettres accentuées ; le contrat n'a pas été modifié).
 */
@RestController
@RequiredArgsConstructor
public class MedecinController implements MdecinsApi, CrneauxApi {

    private final MedecinService medecinService;
    private final PortailMapper mapper;

    @Override
    public ResponseEntity<List<MedecinDto>> listerMedecins(String specialite, String ville) {
        return ResponseEntity.ok(medecinService.lister(specialite, ville).stream().map(mapper::versDto).toList());
    }

    @Override
    public ResponseEntity<List<CreneauDto>> rechercherCreneaux(String medecinId, LocalDate date,
                                                               TypeConsultationDto type, Boolean disponibles) {
        boolean libresSeulement = disponibles == null || disponibles; // défaut du contrat : true
        return ResponseEntity.ok(medecinService.creneaux(medecinId, date, mapper.versType(type), libresSeulement)
                .stream().map(mapper::versDto).toList());
    }
}
