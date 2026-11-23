package tn.sahticonnect.portail.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.sahticonnect.portail.api.model.ContactUrgenceDto;
import tn.sahticonnect.portail.api.model.CreneauDto;
import tn.sahticonnect.portail.api.model.DemandeAmbulanceDto;
import tn.sahticonnect.portail.api.model.FiliereDto;
import tn.sahticonnect.portail.api.model.LigneOrdonnanceDto;
import tn.sahticonnect.portail.api.model.MedecinDto;
import tn.sahticonnect.portail.api.model.MissionAmbulanceDto;
import tn.sahticonnect.portail.api.model.OrdonnanceDto;
import tn.sahticonnect.portail.api.model.PatientDto;
import tn.sahticonnect.portail.api.model.PatientMiseAJourDto;
import tn.sahticonnect.portail.api.model.PositionDto;
import tn.sahticonnect.portail.api.model.RendezVousCreationDto;
import tn.sahticonnect.portail.api.model.RendezVousDto;
import tn.sahticonnect.portail.api.model.RendezVousMiseAJourDto;
import tn.sahticonnect.portail.api.model.StatutMissionDto;
import tn.sahticonnect.portail.api.model.StatutRendezVousDto;
import tn.sahticonnect.portail.api.model.TypeConsultationDto;
import tn.sahticonnect.portail.entity.ContactUrgence;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.MissionAmbulance;
import tn.sahticonnect.portail.entity.Ordonnance;
import tn.sahticonnect.portail.entity.Patient;
import tn.sahticonnect.portail.entity.RendezVous;
import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.service.model.CoordonneesPatient;
import tn.sahticonnect.portail.service.model.DemandeAmbulance;
import tn.sahticonnect.portail.service.model.DemandeRendezVous;
import tn.sahticonnect.portail.service.model.ModificationRendezVous;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Conversion entre les DTO générés depuis le contrat (tn.sahticonnect.portail.api.model)
 * et le modèle métier (entités JPA, records de service). Les enums sont convertis par leur nom.
 * Les dates sont stockées en heure locale (fuseau de l'horloge) et exposées avec leur décalage
 * (2026-11-24T09:00:00+01:00).
 */
@Component
@RequiredArgsConstructor
public class PortailMapper {

    private final Clock horloge;

    // ===================== Entités → DTO =====================

    public PatientDto versDto(Patient p) {
        return new PatientDto()
                .id(p.getId())
                .nom(p.getNom())
                .prenom(p.getPrenom())
                .dateNaissance(p.getDateNaissance())
                .sexe(p.getSexe() == null ? null : PatientDto.SexeEnum.fromValue(p.getSexe().name()))
                .telephone(p.getTelephone())
                .email(p.getEmail())
                .adresse(p.getAdresse())
                .pathologie(p.getPathologie())
                .medecinTraitantId(p.getMedecinTraitant() == null ? null : p.getMedecinTraitant().getId())
                .contactUrgence(versDto(p.getContactUrgence()));
    }

    public MedecinDto versDto(Medecin m) {
        return new MedecinDto()
                .id(m.getId())
                .nom(m.getNom())
                .prenom(m.getPrenom())
                .specialite(m.getSpecialite())
                .etablissement(m.getEtablissement())
                .ville(m.getVille())
                .filiere(m.getFiliere() == null ? null : FiliereDto.fromValue(m.getFiliere().name()))
                .teleconsultation(m.isTeleconsultation())
                .tarifConsultation(m.getTarifConsultation() == null ? null : m.getTarifConsultation().doubleValue());
    }

    public CreneauDto versDto(Creneau c) {
        return new CreneauDto()
                .id(c.getId())
                .medecinId(c.getMedecin().getId())
                .debut(versOffset(c.getDebut()))
                .fin(versOffset(c.getFin()))
                .type(TypeConsultationDto.fromValue(c.getType().name()))
                .disponible(c.isDisponible());
    }

    public RendezVousDto versDto(RendezVous r) {
        return new RendezVousDto()
                .id(r.getId())
                .identifiantPatient(r.getPatient().getId())
                .medecinId(r.getMedecin().getId())
                .creneauId(r.getCreneau() == null ? null : r.getCreneau().getId())
                .debut(versOffset(r.getDebut()))
                .fin(versOffset(r.getFin()))
                .type(TypeConsultationDto.fromValue(r.getType().name()))
                .statut(StatutRendezVousDto.fromValue(r.getStatut().name()))
                .motif(r.getMotif())
                .referenceAlerte(r.getReferenceAlerte())
                .dateCreation(versOffset(r.getDateCreation()));
    }

    public OrdonnanceDto versDto(Ordonnance o) {
        return new OrdonnanceDto()
                .id(o.getId())
                .identifiantPatient(o.getPatient().getId())
                .medecinId(o.getMedecin().getId())
                .medecinNom(o.getMedecin().nomComplet())
                .dateEmission(o.getDateEmission())
                .dateFinValidite(o.getDateFinValidite())
                .renouvelable(o.isRenouvelable())
                .lignes(o.getLignes().stream()
                        .map(l -> new LigneOrdonnanceDto()
                                .medicament(l.getMedicament())
                                .posologie(l.getPosologie())
                                .dureeJours(l.getDureeJours()))
                        .toList())
                .remarques(o.getRemarques());
    }

    public MissionAmbulanceDto versDto(MissionAmbulance m) {
        return new MissionAmbulanceDto()
                .id(m.getId())
                .identifiantPatient(m.getPatient().getId())
                .referenceAlerte(m.getReferenceAlerte())
                .statut(StatutMissionDto.fromValue(m.getStatut().name()))
                .motif(m.getMotif())
                .adresse(m.getAdresse())
                .position(m.getLatitude() == null || m.getLongitude() == null ? null
                        : new PositionDto().latitude(m.getLatitude()).longitude(m.getLongitude()))
                .vehicule(m.getVehicule())
                .etablissementDestination(m.getEtablissementDestination())
                .delaiEstimeMinutes(m.getDelaiEstimeMinutes())
                .dateDemande(versOffset(m.getDateDemande()))
                .dateMiseAJour(versOffset(m.getDateMiseAJour()));
    }

    private ContactUrgenceDto versDto(ContactUrgence c) {
        return c == null || c.getNom() == null ? null
                : new ContactUrgenceDto().nom(c.getNom()).lien(c.getLien()).telephone(c.getTelephone());
    }

    // ===================== DTO → modèle métier =====================

    public CoordonneesPatient versCoordonnees(PatientMiseAJourDto dto) {
        ContactUrgenceDto c = dto.getContactUrgence();
        return new CoordonneesPatient(dto.getTelephone(), dto.getEmail(), dto.getAdresse(),
                c == null ? null : new ContactUrgence(c.getNom(), c.getLien(), c.getTelephone()));
    }

    public DemandeRendezVous versDemande(RendezVousCreationDto dto) {
        return new DemandeRendezVous(dto.getIdentifiantPatient(), dto.getMedecinId(), dto.getCreneauId(),
                versType(dto.getType()), dto.getMotif(), dto.getReferenceAlerte());
    }

    public ModificationRendezVous versModification(RendezVousMiseAJourDto dto) {
        return new ModificationRendezVous(dto.getCreneauId(), versType(dto.getType()), dto.getMotif(),
                dto.getStatut() == null ? null : StatutRendezVous.valueOf(dto.getStatut().getValue()));
    }

    public DemandeAmbulance versDemande(DemandeAmbulanceDto dto) {
        PositionDto p = dto.getPosition();
        return new DemandeAmbulance(dto.getIdentifiantPatient(), dto.getReferenceAlerte(), dto.getMotif(),
                dto.getAdresse(), p == null ? null : p.getLatitude(), p == null ? null : p.getLongitude());
    }

    public TypeConsultation versType(TypeConsultationDto dto) {
        return dto == null ? null : TypeConsultation.valueOf(dto.getValue());
    }

    public StatutRendezVous versStatut(StatutRendezVousDto dto) {
        return dto == null ? null : StatutRendezVous.valueOf(dto.getValue());
    }

    /** Heure locale stockée → date avec décalage du fuseau de l'horloge. */
    public OffsetDateTime versOffset(LocalDateTime date) {
        return date == null ? null : date.atZone(horloge.getZone()).toOffsetDateTime();
    }
}
