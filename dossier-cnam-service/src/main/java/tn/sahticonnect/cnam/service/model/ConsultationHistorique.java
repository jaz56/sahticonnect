package tn.sahticonnect.cnam.service.model;

import tn.sahticonnect.cnam.entity.Assure;
import tn.sahticonnect.cnam.entity.EntreeHistorique;

import java.util.List;

/** Résultat de consulterHistorique : entrées triées de la plus récente à la plus ancienne. */
public record ConsultationHistorique(Assure assure, List<EntreeHistorique> entrees) {
}
