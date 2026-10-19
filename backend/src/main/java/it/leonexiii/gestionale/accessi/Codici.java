package it.leonexiii.gestionale.accessi;

import java.util.Locale;

/** Il codice di un braccialetto come lo salviamo: maiuscolo, senza separatori («04:a1:b2:c3» → «04A1B2C3»). */
final class Codici {
    private Codici() {}

    static String normalizza(String grezzo) {
        String c = grezzo == null ? "" : grezzo.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (!c.matches("[0-9A-Z]{4,32}")) throw new IllegalArgumentException("Codice del braccialetto non valido.");
        return c;
    }
}
