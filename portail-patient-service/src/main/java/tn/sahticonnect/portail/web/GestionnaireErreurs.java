package tn.sahticonnect.portail.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tn.sahticonnect.portail.api.model.ErreurDetailsInnerDto;
import tn.sahticonnect.portail.api.model.ErreurDto;
import tn.sahticonnect.portail.exception.ConflitException;
import tn.sahticonnect.portail.exception.PortailException;
import tn.sahticonnect.portail.exception.RequeteInvalideException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Toutes les erreurs sont renvoyées dans le schéma Erreur du contrat :
 * horodatage, statut, code (stable), message, chemin et, pour les 400, details champ par champ.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GestionnaireErreurs {

    private final Clock horloge;

    /** Erreurs métier : 404, 409, 400 avec leur code. */
    @ExceptionHandler(PortailException.class)
    public ResponseEntity<ErreurDto> metier(PortailException e, HttpServletRequest requete) {
        return reponse(e.getStatut(), e.getCode(), e.getMessage(), requete, null);
    }

    /** Corps JSON invalide au regard du contrat (@NotNull, @Pattern, @Size…). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurDto> corpsInvalide(MethodArgumentNotValidException e, HttpServletRequest requete) {
        List<ErreurDetailsInnerDto> details = new ArrayList<>();
        for (FieldError erreur : e.getBindingResult().getFieldErrors()) {
            details.add(detail(erreur.getField(), erreur.getDefaultMessage()));
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(erreur -> details.add(detail(erreur.getObjectName(), erreur.getDefaultMessage())));
        return invalide(requete, details);
    }

    /** Paramètre de chemin ou de requête invalide (validation de méthode de Spring MVC). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErreurDto> parametresInvalides(HandlerMethodValidationException e, HttpServletRequest requete) {
        List<ErreurDetailsInnerDto> details = new ArrayList<>();
        e.getParameterValidationResults().forEach(resultat -> {
            String champ = resultat.getMethodParameter().getParameterName();
            for (MessageSourceResolvable erreur : resultat.getResolvableErrors()) {
                details.add(detail(champ, erreur.getDefaultMessage()));
            }
        });
        return invalide(requete, details);
    }

    /** Même cas, quand la validation passe par @Validated (proxy AOP). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErreurDto> contraintes(ConstraintViolationException e, HttpServletRequest requete) {
        List<ErreurDetailsInnerDto> details = e.getConstraintViolations().stream()
                .map(v -> {
                    String chemin = v.getPropertyPath().toString();
                    return detail(chemin.substring(chemin.lastIndexOf('.') + 1), v.getMessage());
                })
                .toList();
        return invalide(requete, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErreurDto> parametreManquant(MissingServletRequestParameterException e,
                                                       HttpServletRequest requete) {
        return invalide(requete, List.of(detail(e.getParameterName(), "paramètre obligatoire")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurDto> typeIncorrect(MethodArgumentTypeMismatchException e, HttpServletRequest requete) {
        return invalide(requete, List.of(detail(e.getName(), "valeur invalide : " + e.getValue())));
    }

    /** JSON mal formé, enum inconnu, date illisible… */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurDto> corpsIllisible(HttpMessageNotReadableException e, HttpServletRequest requete) {
        return reponse(HttpStatus.BAD_REQUEST, RequeteInvalideException.CODE,
                "Corps de requête illisible ou non conforme au contrat", requete, null);
    }

    /** Filet de sécurité : conflit de version non intercepté par le service. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErreurDto> conflitConcurrent(ObjectOptimisticLockingFailureException e,
                                                       HttpServletRequest requete) {
        ConflitException conflit = new ConflitException("CONFLIT_CONCURRENT",
                "La ressource a été modifiée par une autre requête, veuillez réessayer");
        return metier(conflit, requete);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurDto> routeInconnue(NoResourceFoundException e, HttpServletRequest requete) {
        return reponse(HttpStatus.NOT_FOUND, "RESSOURCE_INTROUVABLE", "Aucune ressource à cette adresse", requete, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErreurDto> methodeNonSupportee(HttpRequestMethodNotSupportedException e,
                                                         HttpServletRequest requete) {
        return reponse(HttpStatus.METHOD_NOT_ALLOWED, "METHODE_NON_SUPPORTEE",
                "Méthode " + e.getMethod() + " non supportée sur cette adresse", requete, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurDto> inattendue(Exception e, HttpServletRequest requete) {
        log.error("Erreur inattendue sur {}", requete.getRequestURI(), e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", "Erreur interne du service", requete, null);
    }

    // ===================== Construction de la réponse =====================

    private ResponseEntity<ErreurDto> invalide(HttpServletRequest requete, List<ErreurDetailsInnerDto> details) {
        return reponse(HttpStatus.BAD_REQUEST, RequeteInvalideException.CODE,
                "La requête contient des champs invalides", requete, details);
    }

    private ResponseEntity<ErreurDto> reponse(HttpStatus statut, String code, String message,
                                              HttpServletRequest requete, List<ErreurDetailsInnerDto> details) {
        ErreurDto erreur = new ErreurDto()
                .horodatage(OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS))
                .statut(statut.value())
                .code(code)
                .message(message)
                .chemin(requete.getRequestURI());
        // le DTO généré initialise details à [] : on l'omet quand il n'y a pas de détail
        erreur.setDetails(details == null || details.isEmpty() ? null : details);
        return ResponseEntity.status(statut).body(erreur);
    }

    private static ErreurDetailsInnerDto detail(String champ, String message) {
        return new ErreurDetailsInnerDto().champ(champ).message(message);
    }
}
