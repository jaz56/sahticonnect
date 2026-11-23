package tn.sahticonnect.portail.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

/**
 * Accès aux séquences de base de données (créées par schema.sql) qui numérotent
 * les rendez-vous, missions et ordonnances. La requête « valeur suivante » est fournie par le
 * dialecte Hibernate : {@code next value for …} sous H2, {@code nextval('…')} sous PostgreSQL.
 * Une séquence ne rend jamais deux fois la même valeur, même en cas d'appels simultanés.
 */
@Repository
public class NumerotationRepository {

    public static final String SEQUENCE_RENDEZ_VOUS = "rendez_vous_seq";
    public static final String SEQUENCE_MISSIONS = "mission_ambulance_seq";
    public static final String SEQUENCE_ORDONNANCES = "ordonnance_seq";

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
