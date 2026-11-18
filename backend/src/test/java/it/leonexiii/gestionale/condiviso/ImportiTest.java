package it.leonexiii.gestionale.condiviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ImportiTest {

    private static final String E = " €";

    @Test
    void nelTestoTondoSenzaDecimaliAltrimentiDue() {
        assertThat(Importi.testo(new BigDecimal("1175.6"))).isEqualTo("1.175,60" + E);
        assertThat(Importi.testo(new BigDecimal("35"))).isEqualTo("35" + E);
        assertThat(Importi.testo(new BigDecimal("35.00"))).isEqualTo("35" + E);
        assertThat(Importi.testo(new BigDecimal("35.5"))).isEqualTo("35,50" + E);
        assertThat(Importi.testo(new BigDecimal("1000"))).isEqualTo("1.000" + E);
        assertThat(Importi.testo(BigDecimal.ZERO)).isEqualTo("0" + E);
        assertThat(Importi.testo(new BigDecimal("-12.5"))).isEqualTo("-12,50" + E);
        assertThat(Importi.testo(new BigDecimal("1234567.891"))).isEqualTo("1.234.567,89" + E);
        assertThat(Importi.testo(new BigDecimal("12.005"))).isEqualTo("12,01" + E);
        assertThat(Importi.testo(new BigDecimal("9.999"))).isEqualTo("10" + E);
        assertThat(Importi.testo(new BigDecimal("-0.001"))).isEqualTo("0" + E);
    }

    @Test
    void nelleColonneSempreDueDecimali() {
        assertThat(Importi.colonna(new BigDecimal("35"))).isEqualTo("35,00" + E);
        assertThat(Importi.colonna(new BigDecimal("1175.6"))).isEqualTo("1.175,60" + E);
        assertThat(Importi.colonna(new BigDecimal("1000"))).isEqualTo("1.000,00" + E);
        assertThat(Importi.colonna(BigDecimal.ZERO)).isEqualTo("0,00" + E);
        assertThat(Importi.colonna(new BigDecimal("-12.5"))).isEqualTo("-12,50" + E);
        assertThat(Importi.colonna(new BigDecimal("1234567.891"))).isEqualTo("1.234.567,89" + E);
    }
}
