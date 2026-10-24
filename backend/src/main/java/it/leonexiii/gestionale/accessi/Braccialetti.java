package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.identita.Persone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per gli altri moduli (es. le macchine di phon e docce): di chi è un braccialetto letto. */
@Service
public class Braccialetti {

    /** Il braccialetto con quel codice e la persona; «attivo» false se smarrito, disattivato o riconsegnato. */
    public record Titolare(UUID braccialettoId, String codice, UUID personaId, String nome, String cognome, boolean attivo) {}

    private final BraccialettoRepository braccialetti;
    private final Persone persone;

    Braccialetti(BraccialettoRepository braccialetti, Persone persone) {
        this.braccialetti = braccialetti;
        this.persone = persone;
    }

    /** Il codice si normalizza come al tornello (esadecimale con o senza separatori, o il decimale del vecchio gestionale). */
    @Transactional(readOnly = true)
    public Optional<Titolare> titolare(String codice) {
        String c;
        try {
            c = Codici.normalizza(codice);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        var lista = braccialetti.findByCodiceOrderByAssegnatoIlDesc(c);
        var b = lista.stream().filter(Braccialetto::attivo).findFirst().or(() -> lista.stream().findFirst());
        return b.map(x -> {
            var s = persone.schede(List.of(x.getPersonaId())).get(x.getPersonaId());
            return new Titolare(x.getId(), x.getCodice(), x.getPersonaId(), s == null ? null : s.nome(), s == null ? null : s.cognome(), x.attivo());
        });
    }

    /** Il codice normalizzato (per registrarlo), o quello scritto se non è un codice valido. */
    public String normalizza(String codice) {
        try {
            return Codici.normalizza(codice);
        } catch (IllegalArgumentException e) {
            return codice == null ? "" : codice.strip();
        }
    }
}
