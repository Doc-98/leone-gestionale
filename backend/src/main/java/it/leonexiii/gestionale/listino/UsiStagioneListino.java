package it.leonexiii.gestionale.listino;

import it.leonexiii.gestionale.societa.Societa;
import it.leonexiii.gestionale.societa.UsiStagione;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cosa del listino impedisce di eliminare una stagione: le offerte (si possono svuotare) e le vendite (storico). */
@Component
@Order(1)
class UsiStagioneListino implements UsiStagione {

    private final OffertaRepository offerte;
    private final List<VenditeOfferte> vendite;

    UsiStagioneListino(OffertaRepository offerte, List<VenditeOfferte> vendite) {
        this.offerte = offerte;
        this.vendite = vendite;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Uso> usi(Societa.Stagione stagione) {
        List<UUID> ids = offerte.findByStagioneIdOrderByOrdineAscNomeAsc(stagione.id()).stream().map(Offerta::getId).toList();
        List<Uso> out = new ArrayList<>();
        if (ids.isEmpty()) return out;
        out.add(new Uso(ids.size() == 1 ? "1 offerta nel listino" : ids.size() + " offerte nel listino", false));
        long vendute = vendite.stream().flatMap(v -> v.vendite(ids).values().stream()).mapToLong(Long::longValue).sum();
        if (vendute > 0) out.add(new Uso(vendute == 1 ? "1 vendita dal listino" : vendute + " vendite dal listino", true));
        return out;
    }
}
