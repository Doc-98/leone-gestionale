package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Il piano lo vede chiunque abbia un ruolo nell'impianto; lo modifica chi ha «Modifica del piano»; iscrive chi ha
 * «Iscrizioni»; segna le presenze di tutti chi ha «Presenze di tutti» (ADR 0010).
 */
@Component
class AccessoPiano {

    private final Impianti impianti;
    private final Autorizzazioni autorizzazioni;

    AccessoPiano(Impianti impianti, Autorizzazioni autorizzazioni) {
        this.impianti = impianti;
        this.autorizzazioni = autorizzazioni;
    }

    Set<UUID> visibili(Utente u) {
        return espandi(autorizzazioni.ambitoDeiRuoli(u));
    }

    Set<UUID> gestiti(Utente u) {
        return espandi(autorizzazioni.ambito(u, Permesso.PIANO_MODIFICA));
    }

    boolean gestisceIscrizioni(Utente u, UUID impiantoId) {
        return autorizzazioni.puo(u, Permesso.ISCRIZIONI, impiantoId);
    }

    boolean segnaTutteLePresenze(Utente u, UUID impiantoId) {
        return autorizzazioni.puo(u, Permesso.PRESENZE, impiantoId);
    }

    /** Dove l'utente vede le liste di tutti i turni (per iscrivere o per le presenze). */
    Set<UUID> impiantiIscrizioni(Utente u) {
        Set<UUID> out = new HashSet<>(espandi(autorizzazioni.ambito(u, Permesso.ISCRIZIONI)));
        out.addAll(espandi(autorizzazioni.ambito(u, Permesso.PRESENZE)));
        return out;
    }

    void richiediGestione(Utente u, UUID impiantoId) {
        autorizzazioni.richiedi(u, Permesso.PIANO_MODIFICA, impiantoId);
    }

    private Set<UUID> espandi(Autorizzazioni.Ambito a) {
        if (!a.tutti()) return a.impianti();
        Set<UUID> out = new HashSet<>();
        impianti.tutti().forEach(i -> out.add(i.id()));
        return out;
    }
}
