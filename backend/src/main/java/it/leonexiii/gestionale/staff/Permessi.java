package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Chi può fare cosa, impianto per impianto, per il modulo staff (ADR 0010): un permesso vale negli impianti dei
 * ruoli che lo danno; un ruolo senza impianto vale per tutta la società; ADMIN vale ovunque.
 */
@Component
class Permessi {

    private final Impianti impianti;
    private final Autorizzazioni autorizzazioni;

    Permessi(Impianti impianti, Autorizzazioni autorizzazioni) {
        this.impianti = impianti;
        this.autorizzazioni = autorizzazioni;
    }

    /** Impianti in cui l'utente ha il permesso. */
    Set<UUID> impianti(Utente u, Permesso p) {
        Autorizzazioni.Ambito a = autorizzazioni.ambito(u, p);
        if (!a.tutti()) return a.impianti();
        Set<UUID> out = new HashSet<>();
        for (Impianti.ImpiantoBreve i : impianti.tutti()) out.add(i.id());
        return out;
    }

    /** Impianti in cui l'utente ha almeno uno dei permessi. */
    Set<UUID> impianti(Utente u, Permesso p, Permesso altro) {
        Set<UUID> out = new HashSet<>(impianti(u, p));
        out.addAll(impianti(u, altro));
        return out;
    }

    /** Impianti in cui l'utente fa i turni (vede le sostituzioni aperte, può accettarle). */
    Set<UUID> impiantiStaff(Utente u) {
        return impianti(u, Permesso.TURNI);
    }

    void richiedi(Utente u, Permesso p, UUID impiantoId) {
        autorizzazioni.richiedi(u, p, impiantoId);
    }
}
