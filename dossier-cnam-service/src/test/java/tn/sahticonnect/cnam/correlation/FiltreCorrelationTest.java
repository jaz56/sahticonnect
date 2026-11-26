package tn.sahticonnect.cnam.correlation;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Filtre de corrélation : en-tête repris ou généré, MDC pendant le traitement, renvoyé dans la réponse. */
class FiltreCorrelationTest {

    private final FiltreCorrelation filtre = new FiltreCorrelation();
    /** Valeur du MDC vue par le traitement de la requête. */
    private final AtomicReference<String> vuParLeTraitement = new AtomicReference<>();

    private MockHttpServletResponse executer(String entete) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/patients/PAT-0001");
        if (entete != null) {
            requete.addHeader(Correlation.ENTETE, entete);
        }
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, new MockFilterChain(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                vuParLeTraitement.set(MDC.get(Correlation.CLE_MDC));
            }
        }));
        return reponse;
    }

    @Test
    void enteteRecu_misDansLeMdcEtRenvoye() throws Exception {
        MockHttpServletResponse reponse = executer("demo-42");

        assertThat(vuParLeTraitement.get()).isEqualTo("demo-42");
        assertThat(reponse.getHeader(Correlation.ENTETE)).isEqualTo("demo-42");
        assertThat(MDC.get(Correlation.CLE_MDC)).as("MDC nettoyé après la requête").isNull();
    }

    @Test
    void sansEntete_identifiantGenere() throws Exception {
        MockHttpServletResponse reponse = executer(null);

        assertThat(reponse.getHeader(Correlation.ENTETE)).matches("[0-9a-f-]{36}").isEqualTo(vuParLeTraitement.get());
    }

    @Test
    void enteteInvalide_remplace() throws Exception {
        MockHttpServletResponse reponse = executer("faux\nINFO injecté");

        assertThat(reponse.getHeader(Correlation.ENTETE)).matches("[0-9a-f-]{36}");
    }

    @Test
    void avecContexte_leMdcSuitLaTacheSurUnAutreThread() throws Exception {
        AtomicReference<String> vuAilleurs = new AtomicReference<>();
        Runnable tache;
        try (MDC.MDCCloseable c = MDC.putCloseable(Correlation.CLE_MDC, "WF-2026-000001")) {
            tache = Correlation.avecContexte(() -> vuAilleurs.set(Correlation.courant()));
        }
        Thread thread = Thread.ofVirtual().start(tache);
        thread.join();

        assertThat(vuAilleurs.get()).isEqualTo("WF-2026-000001");
    }
}
