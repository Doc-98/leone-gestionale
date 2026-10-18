package it.leonexiii.gestionale;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** I moduli rispettano i confini: nessun accesso alle classi interne di un altro modulo, nessun ciclo. */
class ModuliTest {

    @Test
    void confiniDeiModuli() {
        ApplicationModules.of(GestionaleApplication.class).verify();
    }
}
