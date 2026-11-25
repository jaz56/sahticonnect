package tn.sahticonnect.portail.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tn.sahticonnect.portail.api.RendezVousApi;
import tn.sahticonnect.portail.api.model.RendezVousCreationDto;
import tn.sahticonnect.portail.api.model.RendezVousDto;
import tn.sahticonnect.portail.api.model.RendezVousMiseAJourDto;
import tn.sahticonnect.portail.api.model.StatutRendezVousDto;
import tn.sahticonnect.portail.entity.RendezVous;
import tn.sahticonnect.portail.service.RendezVousService;
import tn.sahticonnect.portail.service.model.FiltreRendezVous;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** CRUD des rendez-vous (DELETE = annulation logique). */
@RestController
@RequiredArgsConstructor
public class RendezVousController implements RendezVousApi {

    private final RendezVousService rendezVousService;
    private final PortailMapper mapper;
    private final Appartenance appartenance;

    @Override
    public ResponseEntity<List<RendezVousDto>> listerRendezVous(String patientId, String medecinId, LocalDate date,
                                                                StatutRendezVousDto statut) {
        // patient connecté : la liste est toujours filtrée sur lui
        FiltreRendezVous filtre = new FiltreRendezVous(appartenance.filtrer(patientId), medecinId, date, mapper.versStatut(statut));
        return ResponseEntity.ok(rendezVousService.lister(filtre).stream().map(mapper::versDto).toList());
    }

    @Override
    public ResponseEntity<RendezVousDto> creerRendezVous(RendezVousCreationDto rendezVousCreation) {
        appartenance.verifier(rendezVousCreation.getIdentifiantPatient());
        RendezVous rdv = rendezVousService.creer(mapper.versDemande(rendezVousCreation));
        return ResponseEntity.created(URI.create("/rendez-vous/" + rdv.getId())).body(mapper.versDto(rdv));
    }

    @Override
    public ResponseEntity<RendezVousDto> consulterRendezVous(String id) {
        RendezVous rdv = rendezVousService.consulter(id);
        appartenance.verifier(rdv.getPatient().getId());
        return ResponseEntity.ok(mapper.versDto(rdv));
    }

    @Override
    public ResponseEntity<RendezVousDto> modifierRendezVous(String id, RendezVousMiseAJourDto rendezVousMiseAJour) {
        appartenance.verifier(rendezVousService.consulter(id).getPatient().getId());
        return ResponseEntity.ok(mapper.versDto(
                rendezVousService.modifier(id, mapper.versModification(rendezVousMiseAJour))));
    }

    @Override
    public ResponseEntity<Void> annulerRendezVous(String id) {
        appartenance.verifier(rendezVousService.consulter(id).getPatient().getId());
        rendezVousService.annuler(id);
        return ResponseEntity.noContent().build();
    }
}
