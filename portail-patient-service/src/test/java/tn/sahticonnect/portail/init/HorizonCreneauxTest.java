package tn.sahticonnect.portail.init;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.repository.CreneauRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base persistante (profil docker) : l'horizon de 14 jours de créneaux est complété sans doublon,
 * et les créneaux existants (réservés ou non) ne sont pas touchés.
 */
@SpringBootTest
@Transactional
class HorizonCreneauxTest {

    @Autowired
    private HorizonCreneaux horizon;
    @Autowired
    private CreneauRepository creneauRepository;
    @Autowired
    private Clock horloge;

    @Test
    void horizonDejaComplet_rienNEstAjoute() {
        long avant = creneauRepository.count();

        assertThat(horizon.completer()).isZero();
        assertThat(creneauRepository.count()).isEqualTo(avant);
    }

    @Test
    void creneauxManquants_dernierJour_sontRecreesUneSeuleFois() {
        // comme si la base avait été initialisée la veille : le dernier jour de l'horizon manque
        String dernierJour = LocalDate.now(horloge).plusDays(DonneesInitiales.NOMBRE_JOURS - 1)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Creneau> aSupprimer = creneauRepository.findAll().stream()
                .filter(c -> c.getId().startsWith("CRN-" + dernierJour + "-") && c.isDisponible())
                .toList();
        assertThat(aSupprimer).isNotEmpty();
        creneauRepository.deleteAll(aSupprimer);
        creneauRepository.flush();
        long avant = creneauRepository.count();

        assertThat(horizon.completer()).isEqualTo(aSupprimer.size());
        assertThat(creneauRepository.count()).isEqualTo(avant + aSupprimer.size());
        assertThat(horizon.completer()).as("deuxième passage : aucun doublon").isZero();
    }
}
