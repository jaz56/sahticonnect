package tn.sahticonnect.portail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.sahticonnect.portail.repository.NumerotationRepository;

import java.time.Clock;
import java.time.LocalDate;

import static tn.sahticonnect.portail.repository.NumerotationRepository.SEQUENCE_MISSIONS;
import static tn.sahticonnect.portail.repository.NumerotationRepository.SEQUENCE_ORDONNANCES;
import static tn.sahticonnect.portail.repository.NumerotationRepository.SEQUENCE_RENDEZ_VOUS;

/** Identifiants métier PREFIXE-AAAA-NNNNNN : année + valeur suivante d'une séquence de base. */
@Component
@RequiredArgsConstructor
public class GenerateurIdentifiants {

    private final NumerotationRepository numerotation;
    private final Clock horloge;

    public String rendezVous() {
        return format("RDV", LocalDate.now(horloge).getYear(), numerotation.prochaineValeur(SEQUENCE_RENDEZ_VOUS));
    }

    public String mission() {
        return format("AMB", LocalDate.now(horloge).getYear(), numerotation.prochaineValeur(SEQUENCE_MISSIONS));
    }

    public String ordonnance(LocalDate dateEmission) {
        return format("ORD", dateEmission.getYear(), numerotation.prochaineValeur(SEQUENCE_ORDONNANCES));
    }

    static String format(String prefixe, int annee, long valeur) {
        return "%s-%d-%06d".formatted(prefixe, annee, valeur);
    }
}
