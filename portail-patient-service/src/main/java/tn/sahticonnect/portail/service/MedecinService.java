package tn.sahticonnect.portail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.exception.RessourceIntrouvableException;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.MedecinRepository;

import java.time.LocalDate;
import java.util.List;

/** Annuaire des médecins et recherche de créneaux. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedecinService {

    private final MedecinRepository medecinRepository;
    private final CreneauRepository creneauRepository;

    /** Filtres facultatifs, insensibles à la casse. */
    public List<Medecin> lister(String specialite, String ville) {
        return medecinRepository.findAll(Sort.by("id")).stream()
                .filter(m -> specialite == null || m.getSpecialite().equalsIgnoreCase(specialite))
                .filter(m -> ville == null || (m.getVille() != null && m.getVille().equalsIgnoreCase(ville)))
                .toList();
    }

    public Medecin consulter(String id) {
        return medecinRepository.findById(id).orElseThrow(() -> RessourceIntrouvableException.medecin(id));
    }

    /** Créneaux du médecin pour une date, filtrés par type (facultatif) et disponibilité. */
    public List<Creneau> creneaux(String medecinId, LocalDate date, TypeConsultation type, boolean disponiblesSeulement) {
        consulter(medecinId);
        return creneauRepository.findByMedecinIdAndDebutGreaterThanEqualAndDebutLessThanOrderByDebut(
                        medecinId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .filter(c -> type == null || c.getType() == type)
                .filter(c -> !disponiblesSeulement || c.isDisponible())
                .toList();
    }
}
