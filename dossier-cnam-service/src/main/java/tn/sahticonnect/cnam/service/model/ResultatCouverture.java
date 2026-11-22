package tn.sahticonnect.cnam.service.model;

/** Couverture CNAM d'un assuré à une date donnée. */
public record ResultatCouverture(boolean couvert, int taux, String motif) {
}
