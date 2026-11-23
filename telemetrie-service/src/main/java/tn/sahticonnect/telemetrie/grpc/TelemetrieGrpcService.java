package tn.sahticonnect.telemetrie.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.sahticonnect.telemetrie.AccuseReception;
import tn.sahticonnect.telemetrie.Alerte;
import tn.sahticonnect.telemetrie.DernieresMesuresRequest;
import tn.sahticonnect.telemetrie.DernieresMesuresResponse;
import tn.sahticonnect.telemetrie.Mesure;
import tn.sahticonnect.telemetrie.NiveauAlerte;
import tn.sahticonnect.telemetrie.SouscrireAlertesRequest;
import tn.sahticonnect.telemetrie.StatutReception;
import tn.sahticonnect.telemetrie.TelemetrieGrpc;
import tn.sahticonnect.telemetrie.TypeMesure;
import tn.sahticonnect.telemetrie.domaine.Constante;
import tn.sahticonnect.telemetrie.domaine.MesureRecue;
import tn.sahticonnect.telemetrie.domaine.Niveau;
import tn.sahticonnect.telemetrie.service.DiffuseurAlertes;
import tn.sahticonnect.telemetrie.service.StockageMesures;
import tn.sahticonnect.telemetrie.service.TraitementMesures;
import tn.sahticonnect.telemetrie.service.ValidateurMesures;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implémentation du service gRPC Telemetrie (stub généré : TelemetrieGrpc.TelemetrieImplBase).
 * Spring gRPC enregistre automatiquement ce bean (BindableService) sur le serveur, port 9090.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetrieGrpcService extends TelemetrieGrpc.TelemetrieImplBase {

    static final int NOMBRE_PAR_DEFAUT = 10;
    private static final Pattern FORMAT_PATIENT = Pattern.compile("PAT-\\d{4}");

    private final TraitementMesures traitement;
    private final DiffuseurAlertes diffuseur;
    private final StockageMesures stockage;
    private final ValidateurMesures validateur;
    private final ConvertisseurProto convertisseur;

    // ===================== EnvoyerMesures (bidirectionnel) =====================

    /**
     * Le capteur envoie un flux de mesures ; chaque mesure reçoit son accusé sur le flux de retour,
     * avec l'alerte si elle vient d'être émise. Une mesure invalide ne coupe pas le flux : elle est REJETEE.
     */
    @Override
    public StreamObserver<Mesure> envoyerMesures(StreamObserver<AccuseReception> accuses) {
        return new StreamObserver<>() {
            private int recues;

            @Override
            public void onNext(Mesure mesure) {
                recues++;
                accuses.onNext(traiter(mesure));
            }

            @Override
            public void onError(Throwable erreur) {
                // le client a coupé le flux (annulation, réseau) : il n'y a plus personne à qui répondre
                log.info("Flux de mesures interrompu par le client après {} mesure(s) : {}", recues,
                        Status.fromThrowable(erreur).getCode());
            }

            @Override
            public void onCompleted() {
                log.info("Flux de mesures terminé par le client ({} mesure(s))", recues);
                accuses.onCompleted();
            }
        };
    }

    private AccuseReception traiter(Mesure mesure) {
        try {
            MesureRecue mesureRecue = convertisseur.versMesure(mesure);
            TraitementMesures.Resultat resultat = traitement.traiter(mesureRecue);
            Alerte alerte = null;
            if (resultat.alerte() != null) {
                alerte = convertisseur.versProto(resultat.alerte(), resultat.mesure());
                diffuseur.publier(alerte, resultat.niveau());
            }
            return convertisseur.versAccuse(mesure.getSequence(), resultat, alerte);
        } catch (RuntimeException e) {
            // panne interne (base indisponible…) : on prévient le capteur sans fermer son flux
            log.error("Erreur pendant le traitement de la mesure #{}", mesure.getSequence(), e);
            return AccuseReception.newBuilder()
                    .setSequence(mesure.getSequence())
                    .setStatut(StatutReception.REJETEE)
                    .setMessage("Erreur interne du service, mesure non traitée")
                    .build();
        }
    }

    // ===================== SouscrireAlertes (server streaming) =====================

    /**
     * Flux d'alertes sans fin : le serveur pousse chaque alerte acceptée par le filtre jusqu'à ce que
     * le client se déconnecte. Niveau minimum NON_SPECIFIE (ou NORMAL) = ELEVEE, c'est-à-dire toutes les alertes.
     */
    @Override
    public void souscrireAlertes(SouscrireAlertesRequest requete, StreamObserver<Alerte> flux) {
        if (requete.getNiveauMinimum() == NiveauAlerte.UNRECOGNIZED) {
            flux.onError(Status.INVALID_ARGUMENT.withDescription("niveau_minimum inconnu").asRuntimeException());
            return;
        }
        for (String patient : requete.getIdentifiantsPatientsList()) {
            if (!FORMAT_PATIENT.matcher(patient).matches()) {
                flux.onError(Status.INVALID_ARGUMENT
                        .withDescription("Identifiant patient invalide : " + patient + " (format PAT-0000)")
                        .asRuntimeException());
                return;
            }
        }
        Niveau niveau = convertisseur.versNiveau(requete.getNiveauMinimum());
        Niveau niveauMinimum = niveau == null || niveau == Niveau.NORMAL ? Niveau.ELEVEE : niveau;
        diffuseur.abonner(new DiffuseurAlertes.Abonnement(
                Set.copyOf(requete.getIdentifiantsPatientsList()), niveauMinimum,
                (ServerCallStreamObserver<Alerte>) flux));
    }

    // ===================== DernieresMesures (unaire) =====================

    @Override
    public void dernieresMesures(DernieresMesuresRequest requete, StreamObserver<DernieresMesuresResponse> reponse) {
        String patient = requete.getIdentifiantPatient();
        if (!FORMAT_PATIENT.matcher(patient).matches()) {
            reponse.onError(Status.INVALID_ARGUMENT
                    .withDescription("identifiant_patient invalide : « " + patient + " » (format PAT-0000)")
                    .asRuntimeException());
            return;
        }
        if (requete.getNombre() < 0 || requete.getNombre() > stockage.capacite()) {
            reponse.onError(Status.INVALID_ARGUMENT
                    .withDescription("nombre doit être compris entre 0 et " + stockage.capacite()
                            + " (0 = " + NOMBRE_PAR_DEFAUT + ")")
                    .asRuntimeException());
            return;
        }
        Set<Constante> constantes = new HashSet<>();
        for (TypeMesure type : requete.getTypesList()) {
            Constante constante = convertisseur.versConstante(type);
            if (constante == null) {
                reponse.onError(Status.INVALID_ARGUMENT
                        .withDescription("Type de mesure non spécifié dans types").asRuntimeException());
                return;
            }
            constantes.add(constante);
        }
        if (!validateur.estPatientSuivi(patient)) {
            reponse.onError(Status.NOT_FOUND.withDescription("Patient inconnu : " + patient).asRuntimeException());
            return;
        }

        int nombre = requete.getNombre() == 0 ? NOMBRE_PAR_DEFAUT : requete.getNombre();
        List<Mesure> mesures = new ArrayList<>();
        stockage.dernieres(patient, constantes, nombre).forEach(m -> mesures.add(convertisseur.versProto(m)));
        reponse.onNext(DernieresMesuresResponse.newBuilder()
                .setIdentifiantPatient(patient)
                .addAllMesures(mesures)
                .build());
        reponse.onCompleted();
    }
}
