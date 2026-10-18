package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.Ruolo;
import it.leonexiii.gestionale.identita.RuoloAssegnato;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Il piano lo vede chiunque abbia un ruolo nell'impianto; lo modificano coordinatori e admin. */
@Component
class AccessoPiano {

    private final Impianti impianti;

    AccessoPiano(Impianti impianti) {
        this.impianti = impianti;
    }

    Set<UUID> visibili(Utente u) {
        Set<UUID> out = new HashSet<>();
        for (RuoloAssegnato r : u.ruoli()) {
            if (r.ruolo() == Ruolo.ADMIN || r.impiantoId() == null) return tutti();
            out.add(r.impiantoId());
        }
        return out;
    }

    Set<UUID> gestiti(Utente u) {
        Set<UUID> out = new HashSet<>();
        for (RuoloAssegnato r : u.ruoli()) {
            if (r.ruolo() != Ruolo.COORDINATORE && r.ruolo() != Ruolo.ADMIN) continue;
            if (r.ruolo() == Ruolo.ADMIN || r.impiantoId() == null) return tutti();
            out.add(r.impiantoId());
        }
        return out;
    }

    void richiediGestione(Utente u, UUID impiantoId) {
        if (!u.ha(Ruolo.COORDINATORE, impiantoId)) throw new AccessDeniedException("Non gestisci il piano di questo impianto.");
    }

    private Set<UUID> tutti() {
        Set<UUID> out = new HashSet<>();
        impianti.tutti().forEach(i -> out.add(i.id()));
        return out;
    }
}
