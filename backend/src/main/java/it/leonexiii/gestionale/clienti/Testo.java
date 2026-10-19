package it.leonexiii.gestionale.clienti;

final class Testo {
    private Testo() {}

    /** Testo facoltativo ripulito: null se vuoto, errore se troppo lungo. */
    static String breve(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        if (t.length() > max) throw new IllegalArgumentException("Testo troppo lungo (massimo " + max + " caratteri).");
        return t;
    }
}
