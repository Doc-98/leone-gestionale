package it.leonexiii.gestionale.condiviso;

/**
 * Qualcosa di insolito ma permesso (es. due turni accavallati della stessa persona): diventa una risposta 409 con
 * {@code avviso: true}. L'interfaccia mostra il messaggio e, se l'utente conferma, ripete la richiesta con
 * {@code forza: true}.
 */
public class Avviso extends RuntimeException {
    public Avviso(String messaggio) {
        super(messaggio);
    }
}
