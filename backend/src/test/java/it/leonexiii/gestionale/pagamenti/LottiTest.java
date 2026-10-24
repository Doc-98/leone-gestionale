package it.leonexiii.gestionale.pagamenti;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Il credito in euro a lotti: le uscite consumano prima quello che scade prima; senza scadenza va in fondo. */
class LottiTest {

    static final UUID P = UUID.randomUUID();

    private static MovimentoBorsellino m(String quantita, LocalDate scadenza, int minuto) {
        var causale = new BigDecimal(quantita).signum() > 0 ? MovimentoBorsellino.Causale.ACCREDITO : MovimentoBorsellino.Causale.PAGAMENTO;
        return new MovimentoBorsellino(P, MovimentoBorsellino.Saldo.EURO, new BigDecimal(quantita), causale, "x", scadenza, null,
            Instant.parse("2026-10-01T10:00:00Z").plusSeconds(minuto * 60L));
    }

    @Test
    void leUsciteConsumanoPrimaQuelloCheScadePrima() {
        var lotti = Movimenti.lotti(List.of(
            m("30", null, 1),
            m("20", LocalDate.of(2027, 6, 1), 2),
            m("10", LocalDate.of(2027, 1, 1), 3),
            m("-15", null, 4)));
        assertThat(lotti).containsExactly(
            new Movimenti.Lotto(LocalDate.of(2027, 6, 1), new BigDecimal("15.00")),
            new Movimenti.Lotto(null, new BigDecimal("30.00")));
    }

    @Test
    void tuttoSpeso() {
        assertThat(Movimenti.lotti(List.of(m("10", LocalDate.of(2027, 1, 1), 1), m("-10", null, 2)))).isEmpty();
    }
}
