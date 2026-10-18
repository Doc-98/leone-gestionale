package it.leonexiii.gestionale.condiviso;

/** L'operazione contrasta con lo stato attuale dei dati (es. turni sovrapposti): diventa una risposta 409. */
public class Conflitto extends RuntimeException {
    public Conflitto(String messaggio) {
        super(messaggio);
    }
}
