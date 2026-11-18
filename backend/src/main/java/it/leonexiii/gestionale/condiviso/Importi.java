package it.leonexiii.gestionale.condiviso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Gli importi in euro scritti in un testo che legge una persona (descrizioni, note, messaggi, PDF).
 * Virgola decimale, migliaia col punto (anche da 1.000), spazio non divisibile prima di «€».
 * Non serve per i numeri delle API (restano numeri) né per i CSV (lì il formato lo vuole il foglio di calcolo).
 */
public final class Importi {

    private static final String EURO = " €";

    private Importi() {}

    /** Nel testo corrente (importo non null): tondo senza decimali («35 €»), altrimenti due («12,50 €», «1.175,60 €»). */
    public static String testo(BigDecimal importo) {
        BigDecimal r = Objects.requireNonNull(importo, "importo").setScale(2, RoundingMode.HALF_UP);
        boolean tondo = r.remainder(BigDecimal.ONE).signum() == 0;
        return formatta(r, !tondo);
    }

    /** Per i PDF e le colonne: sempre due decimali («35,00 €»). */
    public static String colonna(BigDecimal importo) {
        return formatta(Objects.requireNonNull(importo, "importo").setScale(2, RoundingMode.HALF_UP), true);
    }

    private static String formatta(BigDecimal arrotondato, boolean conDecimali) {
        String s = arrotondato.abs().toPlainString();
        String intero = s.substring(0, s.indexOf('.')), dec = s.substring(s.indexOf('.') + 1);
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < intero.length(); i++) {
            if (i > 0 && (intero.length() - i) % 3 == 0) g.append('.');
            g.append(intero.charAt(i));
        }
        boolean meno = arrotondato.signum() < 0;
        return (meno ? "-" : "") + g + (conDecimali ? "," + dec : "") + EURO;
    }
}
