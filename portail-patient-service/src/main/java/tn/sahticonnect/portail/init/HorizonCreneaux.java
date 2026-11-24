package tn.sahticonnect.portail.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.Medecin;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.MedecinRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Garde toujours {@value DonneesInitiales#NOMBRE_JOURS} jours de créneaux à venir.
 * <p>
 * Avec PostgreSQL (profil docker), le volume persiste et {@link DonneesInitiales} ne s'exécute qu'une fois :
 * sans ce complément, il n'y aurait plus aucun créneau (ni garde URGENCE) deux semaines après le premier
 * démarrage. Au démarrage (après le chargement initial) puis chaque nuit, les créneaux manquants de l'horizon
 * sont ajoutés. Les identifiants CRN-aaaaMMjj-NNN-HHmm sont déterministes : un créneau existant n'est jamais
 * recréé ni modifié (réservations conservées).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HorizonCreneaux {

    private final MedecinRepository medecinRepository;
    private final CreneauRepository creneauRepository;
    private final Clock horloge;

    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        completer();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Tunis")
    public void chaqueNuit() {
        completer();
    }

    /** Ajoute les créneaux manquants d'aujourd'hui à J+13 ; renvoie le nombre de créneaux créés. */
    @Transactional
    public int completer() {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        LocalDate aujourdhui = maintenant.toLocalDate();
        List<Creneau> manquants = new ArrayList<>();
        for (Medecin medecin : medecinRepository.findAll()) {
            for (int j = 0; j < DonneesInitiales.NOMBRE_JOURS; j++) {
                List<Creneau> prevus = DonneesInitiales.genererCreneaux(medecin, aujourdhui.plusDays(j), maintenant);
                Set<String> existants = new HashSet<>();
                creneauRepository.findAllById(prevus.stream().map(Creneau::getId).toList())
                        .forEach(c -> existants.add(c.getId()));
                prevus.stream().filter(c -> !existants.contains(c.getId())).forEach(manquants::add);
            }
        }
        if (!manquants.isEmpty()) {
            creneauRepository.saveAll(manquants);
            log.info("Horizon des créneaux complété : {} créneau(x) ajouté(s) jusqu'au {}", manquants.size(),
                    aujourdhui.plusDays(DonneesInitiales.NOMBRE_JOURS - 1));
        }
        return manquants.size();
    }
}
