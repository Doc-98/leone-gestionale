package it.leonexiii.gestionale.identita;

public enum Ruolo {
    /** Tutto, compresa la gestione degli account */
    ADMIN,
    /** Orari e piano vasca del suo impianto, approva le richieste, vede ore e compensi */
    COORDINATORE,
    /** Clienti, iscrizioni, pagamenti, certificati */
    SEGRETERIA,
    /** I propri turni, le richieste, il piano vasca, l'esito al tornello */
    STAFF
}
