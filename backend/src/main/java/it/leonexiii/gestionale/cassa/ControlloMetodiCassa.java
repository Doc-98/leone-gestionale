package it.leonexiii.gestionale.cassa;

import it.leonexiii.gestionale.pagamenti.ControlloMetodi;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Dice ai pagamenti quali metodi hanno soldi in una cassa non ancora chiusa: finché ci sono, il metodo non si elimina. */
@Component
class ControlloMetodiCassa implements ControlloMetodi {

    private final CassaService cassa;

    ControlloMetodiCassa(CassaService cassa) {
        this.cassa = cassa;
    }

    @Override
    public Map<UUID, String> metodiInCassaNonChiusa() {
        return cassa.metodiInCassaNonChiusa();
    }

    @Override
    public boolean cassaAperta() {
        return cassa.cassaAperta();
    }
}
