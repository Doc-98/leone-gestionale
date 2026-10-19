package it.leonexiii.gestionale.config;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errori di dominio come risposte leggibili, nel formato «problem details» (RFC 9457). */
@RestControllerAdvice
class GestioneErrori {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail richiestaNonValida(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NonTrovato.class)
    ProblemDetail nonTrovato(NonTrovato e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(Conflitto.class)
    ProblemDetail conflitto(Conflitto e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail vietato(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Non hai i permessi per questa operazione.");
    }

    /** Ultima difesa: i vincoli del database (es. due turni sovrapposti salvati nello stesso istante). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail vincolo(DataIntegrityViolationException e) {
        String m = String.valueOf(e.getMostSpecificCause().getMessage());
        String testo = m.contains("turno_senza_sovrapposizioni") ? "La persona ha già un turno in quell'orario."
            : m.contains("richiesta_turno_una_aperta") ? "Per questo turno c'è già una richiesta in corso."
            : m.contains("fascia_senza_sovrapposizioni") ? "In quella vasca e in quel giorno c'è già una fascia che si accavalla con questo orario."
            : m.contains("iscrizione_senza_doppioni") ? "Questa persona è già iscritta a questo turno nello stesso periodo."
            : m.contains("braccialetto_codice_in_uso") ? "Questo braccialetto è già assegnato a una persona: va prima disattivato."
            : m.contains("tesseramento_uno_per_stagione") ? "Per questa stagione il tesseramento è già registrato."
            : "I dati non rispettano un vincolo del database.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, testo);
    }
}
