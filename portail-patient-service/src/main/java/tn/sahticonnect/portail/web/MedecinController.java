package tn.sahticonnect.portail.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tn.sahticonnect.portail.api.CreneauxApi;
import tn.sahticonnect.portail.api.MedecinsApi;
import tn.sahticonnect.portail.api.model.CreneauDto;
import tn.sahticonnect.portail.api.model.MedecinDto;
import tn.sahticonnect.portail.api.model.TypeConsultationDto;
import tn.sahticonnect.portail.service.MedecinService;

import java.time.LocalDate;
import java.util.List;

/**
 * Annuaire des médecins et créneaux.
 * MedecinsApi / CreneauxApi : interfaces nommées d'après les chemins /medecins et /creneaux
 * (useTags=false) ; les tags accentués du contrat restent utilisés par Swagger UI.
 */
@RestController
@RequiredArgsConstructor
public class MedecinController implements MedecinsApi, CreneauxApi {

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
