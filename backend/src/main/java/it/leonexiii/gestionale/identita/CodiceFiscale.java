package it.leonexiii.gestionale.identita;

import java.util.Locale;

/** Controllo del codice fiscale delle persone fisiche: formato (anche con omocodie) e carattere di controllo. */
final class CodiceFiscale {
    private CodiceFiscale() {}

    private static final int[] DISPARI = {1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23};

    /** Ripulito (maiuscolo, senza spazi), oppure null se vuoto. Errore se non è un codice fiscale valido. */
    static String normalizza(String grezzo) {
        if (grezzo == null || grezzo.isBlank()) return null;
        String cf = grezzo.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!cf.matches("[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]"))
            throw new IllegalArgumentException("Il codice fiscale non ha il formato giusto (16 caratteri).");
        if (cf.charAt(15) != controllo(cf)) throw new IllegalArgumentException("Il codice fiscale non è valido: l'ultimo carattere non torna.");
        return cf;
    }

    static char controllo(String cf) {
        int somma = 0;
        for (int i = 0; i < 15; i++) {
            char c = cf.charAt(i);
            int v = Character.isDigit(c) ? c - '0' : c - 'A';
            somma += (i % 2 == 0) ? DISPARI[v] : v;
        }
        return (char) ('A' + somma % 26);
    }
}
