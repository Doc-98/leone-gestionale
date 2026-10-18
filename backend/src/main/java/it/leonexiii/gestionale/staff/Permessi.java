package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Ruolo;
import it.leonexiii.gestionale.identita.RuoloAssegnato;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Chi può fare cosa, impianto per impianto. I ruoli hanno un ambito: un coordinatore della piscina gestisce
 * solo la piscina; un ruolo senza impianto vale per tutta la società; ADMIN vale ovunque.
 */
@Component
class Permessi {

    private final Impianti impianti;

    Permessi(Impianti impianti) {
        this.impianti = impianti;
    }

    /** Impianti in cui l'utente pianifica turni e approva richieste. */
    Set<UUID> impiantiGestiti(Utente u) {
        return impiantiCon(u, Ruolo.COORDINATORE);
    }

    /** Impianti in cui l'utente lavora come staff (vede le sostituzioni aperte, può accettarle). */
    Set<UUID> impiantiStaff(Utente u) {
        return impiantiCon(u, Ruolo.STAFF);
    }

    void richiediGestione(Utente u, UUID impiantoId) {
        if (!u.ha(Ruolo.COORDINATORE, impiantoId)) throw new AccessDeniedException("Non gestisci questo impianto.");
    }

    private Set<UUID> tuttiGliImpianti() {
        Set<UUID> out = new HashSet<>();
        for (Impianti.ImpiantoBreve i : impianti.tutti()) out.add(i.id());
        return out;
    }

    private Set<UUID> impiantiCon(Utente u, Ruolo ruolo) {
        Set<UUID> out = new HashSet<>();
        for (RuoloAssegnato r : u.ruoli()) {
            if (r.ruolo() != ruolo && r.ruolo() != Ruolo.ADMIN) continue;
            if (r.ruolo() == Ruolo.ADMIN || r.impiantoId() == null) return tuttiGliImpianti();
            out.add(r.impiantoId());
        }
        return out;
    }
}
