package it.leonexiii.gestionale.struttura;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pubblica del modulo per gli altri moduli: impianti e spazi. */
@Service
public class Impianti {

    public record ImpiantoBreve(UUID id, String nome) {}

    /** padreId: la vasca che contiene la corsia, null per gli spazi di primo livello. */
    public record SpazioBreve(UUID id, UUID impiantoId, String nome, String tipo, UUID padreId) {}

    private final ImpiantoRepository impianti;
    private final SpazioRepository spazi;

    Impianti(ImpiantoRepository impianti, SpazioRepository spazi) {
        this.impianti = impianti;
        this.spazi = spazi;
    }

    @Transactional(readOnly = true)
    public List<ImpiantoBreve> tutti() {
        return impianti.ordinati().stream().map(i -> new ImpiantoBreve(i.getId(), i.getNome())).toList();
    }

    @Transactional(readOnly = true)
    public List<SpazioBreve> spazi(Collection<UUID> impiantiIds) {
        if (impiantiIds.isEmpty()) return List.of();
        return spazi.diImpianti(impiantiIds).stream().map(s -> new SpazioBreve(s.getId(), s.getImpiantoId(), s.getNome(), s.getTipo(), s.getPadreId())).toList();
    }

    /** Lo spazio, se esiste e appartiene all'impianto indicato. */
    @Transactional(readOnly = true)
    public boolean spazioDi(UUID spazioId, UUID impiantoId) {
        return spazi.findById(spazioId).map(s -> s.getImpiantoId().equals(impiantoId)).orElse(false);
    }
}
