package it.leonexiii.gestionale.condiviso;

/** La risorsa chiesta non esiste (o non è visibile a chi la chiede): diventa una risposta 404. */
public class NonTrovato extends RuntimeException {
    public NonTrovato(String messaggio) {
        super(messaggio);
    }
}
