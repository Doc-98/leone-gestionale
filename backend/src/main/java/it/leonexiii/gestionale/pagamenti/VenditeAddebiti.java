package it.leonexiii.gestionale.pagamenti;

import it.leonexiii.gestionale.listino.VenditeOfferte;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le vendite fatte con un addebito dal listino (anche annullato), per le offerte «altri importi» (es. una quota gara).
 * Si contano solo gli addebiti di origine ALTRO: quelli di un abbonamento o del suo rimborso citano la stessa offerta, ma
 * la vendita è già contata dall'abbonamento (altrimenti ogni vendita varrebbe due volte, o una per rata).
 */
@Component
class VenditeAddebiti implements VenditeOfferte {

    private final AddebitoRepository addebiti;

    VenditeAddebiti(AddebitoRepository addebiti) {
        this.addebiti = addebiti;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> vendite(Collection<UUID> offerte) {
        Map<UUID, Long> out = new HashMap<>();
        if (offerte.isEmpty()) return out;
        for (Object[] r : addebiti.contaPerOfferta(offerte, Pagamenti.Origine.ALTRO)) out.put((UUID) r[0], (Long) r[1]);
        return out;
    }
}
