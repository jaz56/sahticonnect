package tn.sahticonnect.dashboard.correlation;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Exécuteur qui recopie le MDC du thread appelant sur le thread de la tâche : les appels SOAP et gRPC lancés en
 * parallèle pendant une requête GraphQL gardent l'identifiant de corrélation de la requête (journaux et en-têtes
 * des appels sortants).
 */
public class ExecuteurAvecCorrelation extends AbstractExecutorService {

    private final ExecutorService delegue;

    public ExecuteurAvecCorrelation(ExecutorService delegue) {
        this.delegue = delegue;
    }

    @Override
    public void execute(Runnable tache) {
        delegue.execute(Correlation.avecContexte(tache));
    }

    @Override
    public void shutdown() {
        delegue.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegue.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegue.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegue.isTerminated();
    }

    @Override
    public boolean awaitTermination(long delai, TimeUnit unite) throws InterruptedException {
        return delegue.awaitTermination(delai, unite);
    }
}
