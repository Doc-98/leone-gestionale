package it.leonexiii.gestionale.staff;

public enum TipoRichiesta {
    /** Nuovo orario per il proprio turno */
    CAMBIO_ORARIO,
    /** Un collega prende il turno: indicato, oppure «aperta» a chiunque dello staff */
    SOSTITUZIONE,
    /** Due turni si scambiano le persone */
    SCAMBIO
}
