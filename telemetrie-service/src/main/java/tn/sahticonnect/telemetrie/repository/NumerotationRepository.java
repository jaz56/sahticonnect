package tn.sahticonnect.telemetrie.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

/**
 * Séquence de base de données qui numérote les alertes (créée par schema.sql).
 * La requête « valeur suivante » est fournie par le dialecte Hibernate : {@code next value for …}
 * sous H2, {@code nextval('…')} sous PostgreSQL. Jamais deux fois la même valeur.
 */
@Repository
public class NumerotationRepository {

    public static final String SEQUENCE_ALERTES = "alerte_seq";

    @PersistenceContext
    private EntityManager entityManager;

    public long prochaineValeur(String sequence) {
        String requete = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect()
                .getSequenceSupport()
                .getSequenceNextValString(sequence);
        return ((Number) entityManager.createNativeQuery(requete).getSingleResult()).longValue();
    }
}
