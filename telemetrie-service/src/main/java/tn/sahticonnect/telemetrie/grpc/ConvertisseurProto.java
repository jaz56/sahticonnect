package tn.sahticonnect.telemetrie.grpc;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.sahticonnect.telemetrie.AccuseReception;
import tn.sahticonnect.telemetrie.Mesure;
import tn.sahticonnect.telemetrie.NiveauAlerte;
import tn.sahticonnect.telemetrie.StatutReception;
import tn.sahticonnect.telemetrie.TypeMesure;
import tn.sahticonnect.telemetrie.domaine.Constante;
import tn.sahticonnect.telemetrie.domaine.MesureRecue;
import tn.sahticonnect.telemetrie.domaine.Niveau;
import tn.sahticonnect.telemetrie.entity.Alerte;
import tn.sahticonnect.telemetrie.service.TraitementMesures;

import java.time.Clock;
import java.time.Instant;

/**
 * Conversion entre les messages Protobuf (générés depuis le .proto) et le modèle métier.
 * Les enums correspondent par leur nom ; les valeurs *_NON_SPECIFIE et UNRECOGNIZED n'ont pas d'équivalent.
 */
@Component
@RequiredArgsConstructor
public class ConvertisseurProto {

    private final Clock horloge;

    /** Mesure Protobuf → mesure métier (constante null si le type n'est pas exploitable). */
    public MesureRecue versMesure(Mesure mesure) {
        Instant horodatage = mesure.hasHorodatage() ? versInstant(mesure.getHorodatage()) : horloge.instant();
        return new MesureRecue(mesure.getIdentifiantPatient(), mesure.getCapteurId(), versConstante(mesure.getType()),
                mesure.getValeur(), mesure.getUnite(), horodatage, mesure.getSequence());
    }

    public Mesure versProto(MesureRecue mesure) {
        return Mesure.newBuilder()
                .setIdentifiantPatient(mesure.identifiantPatient())
                .setCapteurId(nonNul(mesure.capteurId()))
                .setType(versProto(mesure.constante()))
                .setValeur(mesure.valeur())
                .setUnite(mesure.unite() == null || mesure.unite().isBlank() ? mesure.constante().unite() : mesure.unite())
                .setHorodatage(versTimestamp(mesure.horodatage()))
                .setSequence(mesure.sequence())
                .build();
    }

    /** Alerte persistée → message diffusé ; la mesure déclenchante est jointe. */
    public tn.sahticonnect.telemetrie.Alerte versProto(Alerte alerte, MesureRecue mesure) {
        return tn.sahticonnect.telemetrie.Alerte.newBuilder()
                .setId(alerte.getId())
                .setIdentifiantPatient(alerte.getIdentifiantPatient())
                .setNiveau(versProto(alerte.getNiveau()))
                .setTypeMesure(versProto(alerte.getConstante()))
                .setValeur(alerte.getValeur())
                .setSeuilFranchi(alerte.getSeuilFranchi())
                .setMessage(alerte.getMessage())
                .setHorodatage(versTimestamp(alerte.getHorodatage()))
                .setMesure(versProto(mesure))
                .build();
    }

    public AccuseReception versAccuse(long sequence, TraitementMesures.Resultat resultat,
                                      tn.sahticonnect.telemetrie.Alerte alerte) {
        AccuseReception.Builder accuse = AccuseReception.newBuilder()
                .setSequence(sequence)
                .setStatut(resultat.acceptee() ? StatutReception.ACCEPTEE : StatutReception.REJETEE)
                .setMessage(resultat.message());
        if (resultat.niveau() != null) {
            accuse.setNiveau(versProto(resultat.niveau()));
        }
        if (alerte != null) {
            accuse.setAlerte(alerte);
        }
        return accuse.build();
    }

    // ===================== Enums =====================

    public Constante versConstante(TypeMesure type) {
        return switch (type) {
            case FREQUENCE_CARDIAQUE -> Constante.FREQUENCE_CARDIAQUE;
            case SPO2 -> Constante.SPO2;
            case TENSION_SYSTOLIQUE -> Constante.TENSION_SYSTOLIQUE;
            case TENSION_DIASTOLIQUE -> Constante.TENSION_DIASTOLIQUE;
            case TEMPERATURE -> Constante.TEMPERATURE;
            case CHUTE -> Constante.CHUTE;
            case TYPE_MESURE_NON_SPECIFIE, UNRECOGNIZED -> null;
        };
    }

    public TypeMesure versProto(Constante constante) {
        return TypeMesure.valueOf(constante.name());
    }

    public NiveauAlerte versProto(Niveau niveau) {
        return NiveauAlerte.valueOf(niveau.name());
    }

    /** NIVEAU_NON_SPECIFIE → null (l'appelant applique son défaut). */
    public Niveau versNiveau(NiveauAlerte niveau) {
        return switch (niveau) {
            case NORMAL -> Niveau.NORMAL;
            case ELEVEE -> Niveau.ELEVEE;
            case CRITIQUE -> Niveau.CRITIQUE;
            case NIVEAU_NON_SPECIFIE, UNRECOGNIZED -> null;
        };
    }

    // ===================== Dates =====================

    public static Timestamp versTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    public static Instant versInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static String nonNul(String texte) {
        return texte == null ? "" : texte;
    }
}
