package it.leonexiii.gestionale.staff;

/** IN_ATTESA_COLLEGA → IN_ATTESA_APPROVAZIONE → APPROVATA | RESPINTA; ANNULLATA dal richiedente finché è aperta. */
public enum StatoRichiesta {
    IN_ATTESA_COLLEGA,
    IN_ATTESA_APPROVAZIONE,
    APPROVATA,
    RESPINTA,
    ANNULLATA;

    boolean aperta() {
        return this == IN_ATTESA_COLLEGA || this == IN_ATTESA_APPROVAZIONE;
    }
}
