package tn.sahticonnect.dashboard.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import tn.sahticonnect.dashboard.client.ServiceIndisponibleException;
import tn.sahticonnect.dashboard.service.DonneesInvalidesException;
import tn.sahticonnect.dashboard.service.RessourceIntrouvableException;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Transforme les exceptions des résolveurs en erreurs GraphQL, avec un code stable dans extensions :
 * <ul>
 *   <li>SERVICE_INDISPONIBLE (+ extensions.service) : appel gRPC/SOAP en échec ; le champ vaut null et
 *       le reste de la réponse est renvoyé (réponse partielle) ;</li>
 *   <li>NOT_FOUND : patient, alerte ou intervention inconnu ;</li>
 *   <li>BAD_REQUEST : données invalides.</li>
 * </ul>
 */
@Component
@Slf4j
public class GestionErreurs extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment env) {
        Throwable cause = deballer(exception);
        if (cause instanceof ServiceIndisponibleException e) {
            log.warn("Réponse partielle sur {} : {}", env.getExecutionStepInfo().getPath(), e.getMessage());
            return erreur(env, e.getMessage(), ErrorType.INTERNAL_ERROR,
                    Map.of("code", "SERVICE_INDISPONIBLE", "service", e.getService()));
        }
        if (cause instanceof RessourceIntrouvableException) {
            return erreur(env, cause.getMessage(), ErrorType.NOT_FOUND, Map.of("code", "NOT_FOUND"));
        }
        if (cause instanceof DonneesInvalidesException || cause instanceof ConstraintViolationException
                || cause instanceof IllegalArgumentException) {
            return erreur(env, cause.getMessage(), ErrorType.BAD_REQUEST, Map.of("code", "BAD_REQUEST"));
        }
        return null; // autres exceptions : traitement par défaut de Spring GraphQL (INTERNAL_ERROR)
    }

    private static GraphQLError erreur(DataFetchingEnvironment env, String message, ErrorType type,
                                       Map<String, Object> extensions) {
        return GraphqlErrorBuilder.newError(env).message(message).errorType(type).extensions(extensions).build();
    }

    /** Les champs asynchrones (CompletableFuture) enveloppent l'exception d'origine. */
    private static Throwable deballer(Throwable exception) {
        Throwable cause = exception;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
