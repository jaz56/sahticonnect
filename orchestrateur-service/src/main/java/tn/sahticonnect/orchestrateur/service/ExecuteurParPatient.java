package tn.sahticonnect.orchestrateur.service;

import org.springframework.stereotype.Component;
import tn.sahticonnect.orchestrateur.correlation.Correlation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Une file par patient : les tâches d'un même patient s'exécutent dans l'ordre, une à la fois (deux alertes
 * rapprochées ne déclenchent pas deux workflows qui se marchent dessus) ; des patients différents sont
 * traités en parallèle. Chaque tâche est chaînée derrière la précédente du même patient.
 */
@Component
public class ExecuteurParPatient {

    private final ExecutorService executeur;
    private final Map<String, CompletableFuture<Void>> files = new ConcurrentHashMap<>();

    public ExecuteurParPatient(ExecutorService executeurWorkflows) {
        this.executeur = executeurWorkflows;
    }

    public CompletableFuture<Void> soumettre(String patientId, Runnable tache) {
        // MDC de l'appelant (identifiant de corrélation d'une requête HTTP) recopié sur le thread du workflow
        Runnable tacheAvecContexte = Correlation.avecContexte(tache);
        CompletableFuture<Void> suivante = files.compute(patientId, (cle, precedente) ->
                (precedente == null ? CompletableFuture.<Void>completedFuture(null) : precedente)
                        .handle((resultat, erreur) -> null)   // un échec ne bloque pas la file
                        .thenRunAsync(tacheAvecContexte, executeur));
        // file vide après la dernière tâche : on l'oublie
        suivante.whenComplete((resultat, erreur) -> files.remove(patientId, suivante));
        return suivante;
    }
}
