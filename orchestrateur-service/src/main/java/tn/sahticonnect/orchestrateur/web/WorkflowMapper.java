package tn.sahticonnect.orchestrateur.web;

import org.springframework.stereotype.Component;
import tn.sahticonnect.orchestrateur.api.model.DeclenchementAlerteDto;
import tn.sahticonnect.orchestrateur.api.model.EtapeDto;
import tn.sahticonnect.orchestrateur.api.model.EtatWorkflowDto;
import tn.sahticonnect.orchestrateur.api.model.NiveauAlerteDto;
import tn.sahticonnect.orchestrateur.api.model.NomEtapeDto;
import tn.sahticonnect.orchestrateur.api.model.StatutEtapeDto;
import tn.sahticonnect.orchestrateur.api.model.StatutFacturationDto;
import tn.sahticonnect.orchestrateur.api.model.TypeInterventionDto;
import tn.sahticonnect.orchestrateur.api.model.TypeMesureDto;
import tn.sahticonnect.orchestrateur.api.model.WorkflowDto;
import tn.sahticonnect.orchestrateur.entity.EtatWorkflow;
import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.TypeMesure;
import tn.sahticonnect.orchestrateur.entity.Workflow;
import tn.sahticonnect.orchestrateur.service.AlerteRecue;

import java.math.BigDecimal;

/** Conversion entre les DTO générés depuis docs/openapi/orchestrateur.yaml et le modèle métier. */
@Component
public class WorkflowMapper {

    public WorkflowDto versDto(Workflow wf) {
        WorkflowDto dto = new WorkflowDto()
                .id(wf.getId())
                .alerteId(wf.getAlerteId())
                .patientId(wf.getPatientId())
                .niveau(NiveauAlerteDto.fromValue(wf.getNiveau().name()))
                .typeMesure(wf.getTypeMesure() == null ? null : TypeMesureDto.fromValue(wf.getTypeMesure().name()))
                .valeur(wf.getValeur())
                .seuilFranchi(wf.getSeuilFranchi())
                .etat(EtatWorkflowDto.fromValue(wf.getEtat().name()))
                .etapeEnEchec(wf.getEtapeEnEchec() == null ? null : NomEtapeDto.fromValue(wf.getEtapeEnEchec().name()))
                .enCours(wf.isEnCours())
                .couvert(wf.getCouvert())
                .tauxRemboursement(wf.getTauxRemboursement())
                .statutFacturation(wf.getStatutFacturation() == null ? null
                        : StatutFacturationDto.fromValue(wf.getStatutFacturation().name()))
                .typeIntervention(wf.getTypeIntervention() == null ? null
                        : TypeInterventionDto.fromValue(wf.getTypeIntervention().name()))
                .referenceExterne(wf.getReferenceExterne())
                .interventionId(wf.getInterventionId())
                .numeroFeuilleSoins(wf.getNumeroFeuilleSoins())
                .montantTotal(versDouble(wf.getMontantTotal()))
                .partCNAM(versDouble(wf.getPartCNAM()))
                .partPatient(versDouble(wf.getPartPatient()))
                .dateCreation(wf.getDateCreation())
                .dateMiseAJour(wf.getDateMiseAJour());
        dto.setEtapes(wf.getEtapes().stream()
                .map(e -> new EtapeDto()
                        .nom(NomEtapeDto.fromValue(e.getNom().name()))
                        .statut(StatutEtapeDto.fromValue(e.getStatut().name()))
                        .horodatage(e.getHorodatage())
                        .dureeMs(e.getDureeMs())
                        .tentative(e.getTentative())
                        .message(e.getMessage()))
                .toList());
        return dto;
    }

    public AlerteRecue versAlerte(DeclenchementAlerteDto dto) {
        return new AlerteRecue(dto.getAlerteId(), dto.getPatientId(), NiveauAlerte.valueOf(dto.getNiveau().getValue()),
                TypeMesure.valueOf(dto.getTypeMesure().getValue()), dto.getValeur(), dto.getSeuilFranchi(),
                dto.getMessage(), dto.getHorodatage());
    }

    public EtatWorkflow versEtat(EtatWorkflowDto dto) {
        return dto == null ? null : EtatWorkflow.valueOf(dto.getValue());
    }

    private static Double versDouble(BigDecimal montant) {
        return montant == null ? null : montant.doubleValue();
    }
}
