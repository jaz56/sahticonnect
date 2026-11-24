package tn.sahticonnect.orchestrateur.service;

import tn.sahticonnect.orchestrateur.entity.NiveauAlerte;
import tn.sahticonnect.orchestrateur.entity.TypeMesure;

import java.time.OffsetDateTime;

/** Alerte à traiter, reçue par gRPC (SouscrireAlertes) ou par POST /workflows/declencher. */
public record AlerteRecue(String alerteId, String patientId, NiveauAlerte niveau, TypeMesure typeMesure,
                          double valeur, String seuilFranchi, String message, OffsetDateTime horodatage) {
}
