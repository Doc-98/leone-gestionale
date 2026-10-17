package it.leonexiii.gestionale.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errori di validazione dei parametri come risposte 400 leggibili (formato «problem details», RFC 9457). */
@RestControllerAdvice
class GestioneErrori {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail richiestaNonValida(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
