package it.leonexiii.gestionale.staff;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IntervalliTest {

    @Test
    void intervalliAccettatiERifiutati() {
        var d = LocalDate.of(2026, 10, 1);
        assertThatCode(() -> TurniService.controllaIntervallo(d, d)).doesNotThrowAnyException();
        assertThatCode(() -> TurniService.controllaIntervallo(d, d.plusDays(93))).doesNotThrowAnyException();
        assertThatThrownBy(() -> TurniService.controllaIntervallo(d, d.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurniService.controllaIntervallo(d, d.plusDays(94))).isInstanceOf(IllegalArgumentException.class);
    }
}
