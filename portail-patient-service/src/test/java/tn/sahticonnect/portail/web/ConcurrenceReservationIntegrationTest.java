package tn.sahticonnect.portail.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.sahticonnect.portail.entity.Creneau;
import tn.sahticonnect.portail.entity.TypeConsultation;
import tn.sahticonnect.portail.repository.CreneauRepository;
import tn.sahticonnect.portail.repository.RendezVousRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verrou optimiste sur Creneau (@Version). Pas de @Transactional ici : chaque requête valide sa
 * propre transaction, comme en production. Ces tests utilisent les créneaux de MED-003 en fin de période.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrenceReservationIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CreneauRepository creneauRepository;
    @Autowired
    private RendezVousRepository rendezVousRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock horloge;

    private String idCreneauMed003(String heure) {
        LocalDate jour = LocalDate.now(horloge).plusDays(13);
        if (jour.getDayOfWeek() == DayOfWeek.SUNDAY) {
            jour = jour.minusDays(1);
        }
        String id = "CRN-" + jour.toString().replace("-", "") + "-003-" + heure;
        assertThat(creneauRepository.findById(id)).isPresent();
        return id;
    }

    @Test
    void deuxReservationsSimultaneesDuMemeCreneau_une201EtUne409() throws Exception {
        String creneauId = idCreneauMed003("1600");
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService executeur = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> reservation = () -> {
                depart.await(); // les deux requêtes partent en même temps
                return mvc.perform(post("/rendez-vous").contentType(MediaType.APPLICATION_JSON).content("""
                                {"identifiantPatient":"PAT-0003","medecinId":"MED-003","creneauId":"%s",
                                 "type":"PRESENTIEL"}""".formatted(creneauId)))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> premiere = executeur.submit(reservation);
            Future<Integer> seconde = executeur.submit(reservation);
            depart.countDown();

            assertThat(List.of(premiere.get(), seconde.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            executeur.shutdown();
        }
        assertThat(creneauRepository.findById(creneauId).orElseThrow().isDisponible()).isFalse();
        assertThat(rendezVousRepository.findAll().stream()
                .filter(r -> r.getCreneau() != null && r.getCreneau().getId().equals(creneauId)))
                .hasSize(1);
    }

    /**
     * Le mécanisme pas à pas, sans dépendre de l'ordonnancement des threads : deux lectures du même
     * créneau (même version), la première écriture passe et incrémente la version, la seconde échoue.
     */
    @Test
    void ecritureAvecUneVersionPerimee_refusee() {
        String creneauId = idCreneauMed003("1630");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Creneau lectureA = transaction.execute(s -> creneauRepository.findById(creneauId).orElseThrow());
        Creneau lectureB = transaction.execute(s -> creneauRepository.findById(creneauId).orElseThrow());
        assertThat(lectureA.getVersion()).isEqualTo(lectureB.getVersion());
        long versionInitiale = lectureA.getVersion();

        transaction.executeWithoutResult(s -> {
            lectureA.setDisponible(false);
            creneauRepository.saveAndFlush(lectureA); // UPDATE … WHERE id = ? AND version = v → 1 ligne
        });
        assertThat(creneauRepository.findById(creneauId).orElseThrow().getVersion()).isEqualTo(versionInitiale + 1);

        assertThatThrownBy(() -> transaction.executeWithoutResult(s -> {
            lectureB.setDisponible(false);
            creneauRepository.saveAndFlush(lectureB); // version v périmée → 0 ligne → exception
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
