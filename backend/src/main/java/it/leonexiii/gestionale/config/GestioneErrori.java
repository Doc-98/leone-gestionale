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
        // i messaggi scritti nel codice (es. quale permesso serve) si mostrano; quello generico di Spring no
        String m = e.getMessage();
        boolean nostro = m != null && !m.isBlank() && !m.startsWith("Access Denied") && !m.startsWith("Access is denied");
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, nostro ? m : "Non hai i permessi per questa operazione.");
    }

    /** Ultima difesa: i vincoli del database (es. due turni sovrapposti salvati nello stesso istante). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail vincolo(DataIntegrityViolationException e) {
        String m = String.valueOf(e.getMostSpecificCause().getMessage());
        String testo = m.contains("turno_senza_sovrapposizioni") ? "La persona ha già un turno in quell'orario."
            : m.contains("richiesta_turno_una_aperta") ? "Per questo turno c'è già una richiesta in corso."
            : m.contains("fascia_senza_sovrapposizioni") ? "In quella vasca e in quel giorno c'è già una fascia che si accavalla con questo orario."
            : m.contains("iscrizione_senza_doppioni") ? "Questa persona è già iscritta a questo turno nello stesso periodo."
            : m.contains("account_email_unica") ? "C'è già un account con questa email."
            : m.contains("braccialetto_codice_in_uso") ? "Questo braccialetto è già assegnato a una persona: va prima disattivato."
            : m.contains("tesseramento_uno_per_stagione") ? "Per questa stagione il tesseramento è già registrato."
            : m.contains("stagione_senza_sovrapposizioni") ? "Le date si accavallano con un'altra stagione."
            : m.contains("stagione_nome_key") ? "C'è già una stagione con questo nome."
            : m.contains("servizio_nome") ? "C'è già un servizio con questo nome."
            : m.contains("metodo_pagamento_nome") ? "C'è già un metodo di pagamento con questo nome."
            : m.contains("gruppo_clienti_nome") ? "C'è già un gruppo con questo nome."
            : m.contains("appartenenza_gruppo_attiva") ? "La persona è già in questo gruppo."
            : m.contains("offerta_prezzo_unico") ? "C'è già un prezzo per questa frequenza (e gruppo)."
            : m.contains("abbonamento_offerta_id_fkey") ? "Con questa offerta sono già stati venduti abbonamenti: invece di eliminarla, disattivala."
            : "I dati non rispettano un vincolo del database.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, testo);
    }
}
