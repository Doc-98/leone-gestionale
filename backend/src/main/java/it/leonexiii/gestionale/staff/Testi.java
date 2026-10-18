package it.leonexiii.gestionale.staff;

/** Controlli sui testi inseriti dagli utenti. */
final class Testi {

    private Testi() {}

    /** Il testo senza spazi ai lati; errore se vuoto o troppo lungo. */
    static String obbligatorio(String testo, String cosa, int massimo) {
        String t = testo == null ? "" : testo.strip();
        if (t.isEmpty()) throw new IllegalArgumentException("Indica " + cosa + ".");
        if (t.length() > massimo) throw new IllegalArgumentException("Testo troppo lungo per " + cosa + " (al massimo " + massimo + " caratteri).");
        return t;
    }
}
