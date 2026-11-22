package tn.sahticonnect.cnam.service.model;

import tn.sahticonnect.cnam.entity.ActeCote;

import java.time.LocalDate;
import java.util.List;

/** Données d'entrée de emettreFeuilleDeSoins. */
public record DemandeFeuilleDeSoins(
        String identifiantPatient,
        String codeVignetteCNAM,
        LocalDate dateSoins,
        String etablissement,
        String medecin,
        List<ActeCote> actes,
        String referenceIntervention) {
}
