package tn.sahticonnect.cnam.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

/**
 * Accès à la séquence de base de données qui numérote les feuilles de soins.
 * La séquence est créée par schema.sql. La requête « valeur suivante » est fournie par le
 * dialecte Hibernate : {@code next value for …} sous H2, {@code nextval('…')} sous PostgreSQL.
 * Une séquence ne rend jamais deux fois la même valeur, même en cas d'appels simultanés.
 */
@Repository
public class NumerotationRepository {

    public static final String SEQUENCE_FEUILLES = "feuille_soins_numero_seq";

    @PersistenceContext
    private EntityManager entityManager;

    public long prochaineValeurFeuille() {
        String requete = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect()
                .getSequenceSupport()
                .getSequenceNextValString(SEQUENCE_FEUILLES);
        return ((Number) entityManager.createNativeQuery(requete).getSingleResult()).longValue();
    }
}
