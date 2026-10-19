package it.leonexiii.gestionale.accessi;

import java.util.Locale;

/**
 * Il codice di un braccialetto come lo salviamo: esadecimale maiuscolo senza separatori, nell'ordine di lettura.
 * Il telefono legge «0c:7a:5c:ae» → «0C7A5CAE». Il gestionale attuale (e i lettori fissi) scrivono lo stesso seriale
 * in decimale a 10 cifre, «0209345710»: dieci cifre e basta vengono convertite (ADR 0004).
 */
final class Codici {
    private Codici() {}

    static String normalizza(String grezzo) {
        String c = grezzo == null ? "" : grezzo.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (c.matches("[0-9]{10}")) {
            long n = Long.parseLong(c);
            if (n <= 0xFFFFFFFFL) return String.format("%08X", n);
        }
        if (!c.matches("[0-9A-Z]{4,32}")) throw new IllegalArgumentException("Codice del braccialetto non valido.");
        return c;
    }

    /** Lo stesso codice come lo mostra il gestionale attuale (decimale a 10 cifre), se è un seriale di 4 byte. */
    static String decimale(String codice) {
        return codice != null && codice.matches("[0-9A-F]{8}") ? String.format("%010d", Long.parseLong(codice, 16)) : null;
    }
}
