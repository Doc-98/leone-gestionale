package it.leonexiii.gestionale.clienti;

import java.math.BigDecimal;
import java.time.LocalDate;

/** La stagione sportiva: di solito da settembre a luglio. Ad agosto si propone già la stagione che sta per iniziare. */
record Stagione(String nome, LocalDate dal, LocalDate al, BigDecimal importo) {

    static final BigDecimal QUOTA = new BigDecimal("35.00");

    static Stagione di(LocalDate g) {
        int inizio = g.getMonthValue() >= 8 ? g.getYear() : g.getYear() - 1;
        return new Stagione(inizio + "/" + String.format("%02d", (inizio + 1) % 100), LocalDate.of(inizio, 9, 1), LocalDate.of(inizio + 1, 7, 31), QUOTA);
    }
}
