package tn.sahticonnect.portail.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tn.sahticonnect.portail.api.PatientsApi;
import tn.sahticonnect.portail.api.model.OrdonnanceDto;
import tn.sahticonnect.portail.api.model.PatientDto;
import tn.sahticonnect.portail.api.model.PatientMiseAJourDto;
import tn.sahticonnect.portail.service.PatientService;

import java.util.List;

/**
 * Profil patient et ordonnances. Les routes, paramètres et règles de validation viennent de
 * l'interface générée depuis le contrat : ce contrôleur ne fait que mapper et déléguer.
 * Les ordonnances (/patients/{id}/ordonnances) sont une sous-ressource du patient :
 * elles sont donc générées dans PatientsApi.
 */
@RestController
@RequiredArgsConstructor
public class PatientController implements PatientsApi {

    private final PatientService patientService;
    private final PortailMapper mapper;

    @Override
    public ResponseEntity<PatientDto> consulterPatient(String id) {
        return ResponseEntity.ok(mapper.versDto(patientService.consulter(id)));
    }

    @Override
    public ResponseEntity<PatientDto> modifierPatient(String id, PatientMiseAJourDto patientMiseAJour) {
        return ResponseEntity.ok(mapper.versDto(
                patientService.modifierCoordonnees(id, mapper.versCoordonnees(patientMiseAJour))));
    }

    @Override
    public ResponseEntity<List<OrdonnanceDto>> listerOrdonnancesPatient(String id, Boolean actives) {
        return ResponseEntity.ok(patientService.ordonnances(id, Boolean.TRUE.equals(actives)).stream()
                .map(mapper::versDto).toList());
    }
}
