package tn.sahticonnect.cnam.service.model;

import tn.sahticonnect.cnam.entity.ActeCote;
import tn.sahticonnect.cnam.entity.Filiere;

import java.util.List;

/** Une prise en charge à comparer (ex. « Hôpital public », filière PUBLIQUE, actes C 1 + B 20). */
public record OffreSoins(String libelle, Filiere filiere, List<ActeCote> actes) {
}
