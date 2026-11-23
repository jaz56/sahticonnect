package tn.sahticonnect.dashboard.client;

import tn.sahticonnect.dashboard.entity.NiveauAlerte;
import tn.sahticonnect.dashboard.entity.TypeConstante;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Objets renvoyés par les clients distants, exposés tels quels au schéma GraphQL
 * (les noms des composants correspondent aux champs des types GraphQL).
 */
public final class ModelesDistants {

    private ModelesDistants() {
    }

    /** Type GraphQL ConstanteVitale (source : gRPC DernieresMesures). */
    public record ConstanteVitale(String id, String patientId, TypeConstante type, double valeur, String unite,
                                  OffsetDateTime horodatage, NiveauAlerte niveau) {
    }

    /** Type GraphQL ActeCote (source : SOAP). */
    public record ActeCote(String lettreCle, double coefficient, String libelle) {
    }

    /** Type GraphQL EntreeHistoriqueSoins (source : SOAP consulterHistorique). */
    public record EntreeHistoriqueSoins(LocalDate date, String etablissement, String diagnostic,
                                        List<ActeCote> actes, String numeroFeuilleSoins) {
    }

    /** Résultat de consulterHistorique : vignette de l'assuré et entrées (de la plus récente à la plus ancienne). */
    public record HistoriqueCnam(String codeVignette, List<EntreeHistoriqueSoins> entrees) {
    }

    /** Couverture CNAM du jour (verifierDroitsCNAM). */
    public record Couverture(boolean couvert, int tauxRemboursement) {
    }

    /** Type GraphQL PageHistoriqueSoins. */
    public record PageHistoriqueSoins(List<EntreeHistoriqueSoins> elements, int page, int taille,
                                      int totalElements, int totalPages) {

        public static PageHistoriqueSoins de(List<EntreeHistoriqueSoins> toutes, int page, int taille) {
            int debut = Math.min(page * taille, toutes.size());
            int fin = Math.min(debut + taille, toutes.size());
            int totalPages = (toutes.size() + taille - 1) / taille;
            return new PageHistoriqueSoins(List.copyOf(toutes.subList(debut, fin)), page, taille, toutes.size(), totalPages);
        }
    }
}
