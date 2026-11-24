package tn.sahticonnect.orchestrateur.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

/**
 * Séquence de base de données qui numérote les workflows (créée par schema.sql). La requête « valeur suivante »
 * vient du dialecte Hibernate (H2 : next value for, PostgreSQL : nextval) ; jamais deux fois la même valeur.
 */
@Repository
public class NumerotationRepository {

    public static final String SEQUENCE_WORKFLOWS = "workflow_seq";

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
