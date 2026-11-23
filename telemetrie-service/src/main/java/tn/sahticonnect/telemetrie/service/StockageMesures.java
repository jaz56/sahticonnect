package tn.sahticonnect.telemetrie.service;

import org.springframework.stereotype.Component;
import tn.sahticonnect.telemetrie.config.TelemetrieProperties;
import tn.sahticonnect.telemetrie.domaine.Constante;
import tn.sahticonnect.telemetrie.domaine.MesureRecue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Historique en mémoire des dernières mesures : au plus N (100 par défaut) par patient et par type.
 * ConcurrentHashMap + LinkedBlockingDeque bornée : plusieurs capteurs écrivent en parallèle
 * pendant que DernieresMesures lit. La plus récente est en tête de chaque file.
 */
@Component
public class StockageMesures {

    private record Cle(String patient, Constante constante) {
    }

    private final int capacite;
    private final Map<Cle, LinkedBlockingDeque<MesureRecue>> mesures = new ConcurrentHashMap<>();

    public StockageMesures(TelemetrieProperties proprietes) {
        this.capacite = proprietes.historiqueParType();
    }

    public void ajouter(MesureRecue mesure) {
        LinkedBlockingDeque<MesureRecue> file = mesures.computeIfAbsent(
                new Cle(mesure.identifiantPatient(), mesure.constante()), cle -> new LinkedBlockingDeque<>(capacite));
        // file pleine : on retire la plus ancienne (en queue) jusqu'à pouvoir insérer en tête
        while (!file.offerFirst(mesure)) {
            file.pollLast();
        }
    }

    /**
     * Les {@code nombre} dernières mesures de chaque type demandé (tous les types si la liste est vide),
     * la plus récente en premier.
     */
    public List<MesureRecue> dernieres(String patient, Collection<Constante> constantes, int nombre) {
        Collection<Constante> types = constantes.isEmpty() ? List.of(Constante.values()) : constantes;
        List<MesureRecue> resultat = new ArrayList<>();
        for (Constante constante : types) {
            LinkedBlockingDeque<MesureRecue> file = mesures.get(new Cle(patient, constante));
            if (file != null) {
                file.stream().limit(nombre).forEach(resultat::add);
            }
        }
        resultat.sort(Comparator.comparing(MesureRecue::horodatage).reversed());
        return resultat;
    }

    public int capacite() {
        return capacite;
    }

    /** Vide l'historique (tests). */
    public void vider() {
        mesures.clear();
    }
}
