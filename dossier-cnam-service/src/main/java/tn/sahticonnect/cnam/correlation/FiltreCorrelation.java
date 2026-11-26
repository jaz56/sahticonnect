package tn.sahticonnect.cnam.correlation;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Identifiant de corrélation des requêtes HTTP reçues :
 * <ul>
 *   <li>repris de l'en-tête {@value Correlation#ENTETE} (posé par la gateway ou l'appelant), sinon généré ;</li>
 *   <li>mis dans le MDC pendant le traitement : tous les journaux de la requête commencent par {@code [id]} ;</li>
 *   <li>renvoyé dans la réponse ;</li>
 *   <li>une ligne de journal par requête : méthode, chemin, statut, durée (sauf /actuator : healthchecks Docker).</li>
 * </ul>
 * Réponse asynchrone (SSE, GraphQL) : la ligne est écrite à la fin de l'échange, avec le statut final.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltreCorrelation extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltreCorrelation.class);

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String id = Correlation.accepterOuGenerer(requete.getHeader(Correlation.ENTETE));
        reponse.setHeader(Correlation.ENTETE, id);
        long debut = System.nanoTime();
        MDC.put(Correlation.CLE_MDC, id);
        try {
            chaine.doFilter(requete, reponse);
        } finally {
            if (requete.isAsyncStarted()) {
                requete.getAsyncContext().addListener(new FinAsynchrone(id, requete, reponse, debut));
            } else {
                journaliser(requete, reponse, debut);
            }
            MDC.remove(Correlation.CLE_MDC);
        }
    }

    private static void journaliser(HttpServletRequest requete, HttpServletResponse reponse, long debut) {
        if (!requete.getRequestURI().startsWith("/actuator")) {
            log.info("{} {} → {} ({} ms)", requete.getMethod(), requete.getRequestURI(), reponse.getStatus(),
                    (System.nanoTime() - debut) / 1_000_000);
        }
    }

    /** Fin d'une réponse asynchrone : journalisée avec l'identifiant de la requête. */
    private record FinAsynchrone(String id, HttpServletRequest requete, HttpServletResponse reponse, long debut)
            implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent evenement) {
            try (MDC.MDCCloseable c = MDC.putCloseable(Correlation.CLE_MDC, id)) {
                journaliser(requete, reponse, debut);
            }
        }

        @Override
        public void onTimeout(AsyncEvent evenement) {
            // la fin (onComplete) suit toujours
        }

        @Override
        public void onError(AsyncEvent evenement) {
            // la fin (onComplete) suit toujours
        }

        @Override
        public void onStartAsync(AsyncEvent evenement) {
            // rien
        }
    }
}
