package it.leonexiii.gestionale.pianovasca;

/** Ogni quanto si ripete un turno nel suo periodo di validità. */
enum Cadenza {
    /** Ogni settimana, nel giorno indicato. */
    SETTIMANALE,
    /** Una volta al mese: lo stesso giorno della settimana nella stessa posizione del mese della prima data. */
    MENSILE
}
