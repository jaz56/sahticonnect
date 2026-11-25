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
    private final Appartenance appartenance;

    @Override
    public ResponseEntity<MissionAmbulanceDto> demanderAmbulance(DemandeAmbulanceDto demandeAmbulance) {
        appartenance.verifier(demandeAmbulance.getIdentifiantPatient());
        MissionAmbulance mission = ambulanceService.demander(mapper.versDemande(demandeAmbulance));
        return ResponseEntity.created(URI.create("/urgences/ambulances/" + mission.getId()))
                .body(mapper.versDto(mission));
    }

    @Override
    public ResponseEntity<MissionAmbulanceDto> consulterMissionAmbulance(String id) {
        MissionAmbulance mission = ambulanceService.consulter(id);
        appartenance.verifier(mission.getPatient().getId());
        return ResponseEntity.ok(mapper.versDto(mission));
    }
}
