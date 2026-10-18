package it.leonexiii.gestionale.pianovasca;

/** Controlli sui testi inseriti dagli utenti. */
final class Testi {

    private Testi() {}

    static String obbligatorio(String testo, String cosa, int massimo) {
        String t = facoltativo(testo, cosa, massimo);
        if (t == null) throw new IllegalArgumentException("Indica " + cosa + ".");
        return t;
    }

    static String facoltativo(String testo, String cosa, int massimo) {
        if (testo == null || testo.isBlank()) return null;
        String t = testo.strip();
        if (t.length() > massimo) throw new IllegalArgumentException("Testo troppo lungo per " + cosa + " (al massimo " + massimo + " caratteri).");
        return t;
    }
}
