package tn.sahticonnect.portail.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tn.sahticonnect.portail.api.UrgencesApi;
import tn.sahticonnect.portail.api.model.DemandeAmbulanceDto;
import tn.sahticonnect.portail.api.model.MissionAmbulanceDto;
import tn.sahticonnect.portail.entity.MissionAmbulance;
import tn.sahticonnect.portail.service.AmbulanceService;

import java.net.URI;

/** Demandes et suivi des missions d'ambulance. */
@RestController
@RequiredArgsConstructor
public class UrgenceController implements UrgencesApi {

    private final AmbulanceService ambulanceService;
    private final PortailMapper mapper;

    @Override
    public ResponseEntity<MissionAmbulanceDto> demanderAmbulance(DemandeAmbulanceDto demandeAmbulance) {
        MissionAmbulance mission = ambulanceService.demander(mapper.versDemande(demandeAmbulance));
        return ResponseEntity.created(URI.create("/urgences/ambulances/" + mission.getId()))
                .body(mapper.versDto(mission));
    }

    @Override
    public ResponseEntity<MissionAmbulanceDto> consulterMissionAmbulance(String id) {
        return ResponseEntity.ok(mapper.versDto(ambulanceService.consulter(id)));
    }
}
