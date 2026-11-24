package tn.sahticonnect.orchestrateur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tn.sahticonnect.orchestrateur.api.model.ErreurDetailsInnerDto;
import tn.sahticonnect.orchestrateur.api.model.ErreurDto;
import tn.sahticonnect.orchestrateur.service.WorkflowIntrouvableException;
import tn.sahticonnect.orchestrateur.service.WorkflowNonRelancableException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Toutes les erreurs dans le schéma Erreur du contrat (identique à celui du portail patient). */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GestionnaireErreurs {

    private final Clock horloge;

    @ExceptionHandler(WorkflowIntrouvableException.class)
    public ResponseEntity<ErreurDto> introuvable(WorkflowIntrouvableException e, HttpServletRequest requete) {
        return reponse(HttpStatus.NOT_FOUND, "WORKFLOW_INTROUVABLE", e.getMessage(), requete, null);
    }

    @ExceptionHandler(WorkflowNonRelancableException.class)
    public ResponseEntity<ErreurDto> nonRelancable(WorkflowNonRelancableException e, HttpServletRequest requete) {
        return reponse(HttpStatus.CONFLICT, "WORKFLOW_NON_RELANCABLE", e.getMessage(), requete, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurDto> corpsInvalide(MethodArgumentNotValidException e, HttpServletRequest requete) {
        List<ErreurDetailsInnerDto> details = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(f -> details.add(detail(f.getField(), f.getDefaultMessage())));
        return invalide(requete, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErreurDto> parametresInvalides(HandlerMethodValidationException e, HttpServletRequest requete) {
        List<ErreurDetailsInnerDto> details = new ArrayList<>();
        e.getParameterValidationResults().forEach(r -> {
            for (MessageSourceResolvable erreur : r.getResolvableErrors()) {
                details.add(detail(r.getMethodParameter().getParameterName(), erreur.getDefaultMessage()));
            }
        });
        return invalide(requete, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErreurDto> contraintes(ConstraintViolationException e, HttpServletRequest requete) {
        return invalide(requete, e.getConstraintViolations().stream().map(v -> {
            String chemin = v.getPropertyPath().toString();
            return detail(chemin.substring(chemin.lastIndexOf('.') + 1), v.getMessage());
        }).toList());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurDto> typeIncorrect(MethodArgumentTypeMismatchException e, HttpServletRequest requete) {
        return invalide(requete, List.of(detail(e.getName(), "valeur invalide : " + e.getValue())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurDto> illisible(HttpMessageNotReadableException e, HttpServletRequest requete) {
        return reponse(HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", "Corps de requête illisible ou non conforme au contrat",
                requete, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErreurDto> routeInconnue(NoResourceFoundException e, HttpServletRequest requete) {
        return reponse(HttpStatus.NOT_FOUND, "RESSOURCE_INTROUVABLE", "Aucune ressource à cette adresse", requete, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurDto> inattendue(Exception e, HttpServletRequest requete) {
        log.error("Erreur inattendue sur {}", requete.getRequestURI(), e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", "Erreur interne du service", requete, null);
    }

    private ResponseEntity<ErreurDto> invalide(HttpServletRequest requete, List<ErreurDetailsInnerDto> details) {
        return reponse(HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", "La requête contient des champs invalides", requete, details);
    }

    private ResponseEntity<ErreurDto> reponse(HttpStatus statut, String code, String message, HttpServletRequest requete,
                                              List<ErreurDetailsInnerDto> details) {
        ErreurDto erreur = new ErreurDto()
                .horodatage(OffsetDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS))
                .statut(statut.value())
                .code(code)
                .message(message)
                .chemin(requete.getRequestURI());
        erreur.setDetails(details == null || details.isEmpty() ? null : details);
        return ResponseEntity.status(statut).body(erreur);
    }

    private static ErreurDetailsInnerDto detail(String champ, String message) {
        return new ErreurDetailsInnerDto().champ(champ).message(message);
    }
}
