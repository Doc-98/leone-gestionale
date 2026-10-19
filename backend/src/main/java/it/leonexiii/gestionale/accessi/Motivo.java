package it.leonexiii.gestionale.accessi;

/**
 * Perché il tornello dà rosso (o un avviso, se {@code blocca} è falso): codice stabile, messaggio per il
 * tornellista e cosa fare per risolvere.
 */
record Motivo(String codice, String regola, String messaggio, String rimedio, boolean blocca) {

    static Motivo blocca(String codice, String regola, String messaggio, String rimedio) {
        return new Motivo(codice, regola, messaggio, rimedio, true);
    }
}
