package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.listino.VenditeOfferte;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gli abbonamenti e i carnet venduti dal listino (anche annullati): un'offerta così venduta non si elimina, si spegne. */
@Component
class VenditeAbbonamenti implements VenditeOfferte {

    private final AbbonamentoRepository abbonamenti;

    VenditeAbbonamenti(AbbonamentoRepository abbonamenti) {
        this.abbonamenti = abbonamenti;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> vendite(Collection<UUID> offerte) {
        Map<UUID, Long> out = new HashMap<>();
        if (offerte.isEmpty()) return out;
        for (Object[] r : abbonamenti.contaPerOfferta(offerte)) out.put((UUID) r[0], (Long) r[1]);
        return out;
    }
}
