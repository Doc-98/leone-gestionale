package it.leonexiii.gestionale.identita;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chi può fare cosa, impianto per impianto (ADR 0010). I ruoli di un account e i permessi di ogni ruolo si leggono
 * dal database (con una piccola cache che si svuota a ogni modifica), quindi valgono subito. Un ruolo assegnato su un
 * impianto vale solo lì; senza impianto vale per tutta la società. ADMIN ha tutti i poteri ovunque.
 *
 * <p>Nei controller: {@code @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")} (uno qualsiasi dei
 * permessi indicati, in almeno un impianto); nei servizi, per impianto: {@link #richiedi}.
 */
@Service("autorizzazioni")
public class Autorizzazioni {

    /** Dove vale un permesso: dappertutto, oppure in questi impianti (vuoto = da nessuna parte). */
    public record Ambito(boolean tutti, Set<UUID> impianti) {
        public boolean comprende(UUID impiantoId) {
            return tutti || impianti.contains(impiantoId);
        }

        public boolean vuoto() {
            return !tutti && impianti.isEmpty();
        }
    }

    private record Assegnazioni(LocalDate giorno, List<RuoloAssegnato> ruoli) {}

    private final AssegnazioneRuoloRepository assegnazioni;
    private final RuoloPermessoRepository permessiRuoli;
    private final Map<UUID, Assegnazioni> cacheAccount = new ConcurrentHashMap<>();
    private volatile Map<String, Set<Permesso>> cachePermessi;

    Autorizzazioni(AssegnazioneRuoloRepository assegnazioni, RuoloPermessoRepository permessiRuoli) {
        this.assegnazioni = assegnazioni;
        this.permessiRuoli = permessiRuoli;
    }

    /** Dopo ogni modifica a ruoli, permessi o assegnazioni. */
    void invalida() {
        cacheAccount.clear();
        cachePermessi = null;
    }

    /** I ruoli validi oggi dell'utente. */
    @Transactional(readOnly = true)
    public List<RuoloAssegnato> ruoli(Utente u) {
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        Assegnazioni a = cacheAccount.get(u.accountId());
        if (a == null || !a.giorno().equals(oggi)) {
            a = new Assegnazioni(oggi, assegnazioni.validi(u.accountId(), oggi).stream()
                .map(r -> new RuoloAssegnato(r.getRuolo(), r.getImpiantoId())).toList());
            cacheAccount.put(u.accountId(), a);
        }
        return a.ruoli();
    }

    Set<Permesso> permessiDelRuolo(String ruolo) {
        if (RuoloDefinito.ADMIN.equals(ruolo)) {
            Set<Permesso> tutti = EnumSet.noneOf(Permesso.class);
            for (Permesso p : Permesso.values()) if (p.potere()) tutti.add(p);
            return tutti;
        }
        Map<String, Set<Permesso>> m = cachePermessi;
        if (m == null) {
            m = new HashMap<>();
            for (RuoloPermesso rp : permessiRuoli.findAll()) {
                try {
                    m.computeIfAbsent(rp.getChiave().ruolo(), k -> EnumSet.noneOf(Permesso.class)).add(Permesso.valueOf(rp.getChiave().permesso()));
                } catch (IllegalArgumentException e) {
                    // un permesso tolto dal codice resta nel database senza effetto
                }
            }
            cachePermessi = m;
        }
        return m.getOrDefault(ruolo, Set.of());
    }

    public boolean admin(Utente u) {
        return ruoli(u).stream().anyMatch(r -> RuoloDefinito.ADMIN.equals(r.ruolo()));
    }

    /** Dove l'utente ha il permesso. */
    public Ambito ambito(Utente u, Permesso p) {
        Set<UUID> impianti = new HashSet<>();
        for (RuoloAssegnato r : ruoli(u)) {
            if (!permessiDelRuolo(r.ruolo()).contains(p)) continue;
            if (r.impiantoId() == null || RuoloDefinito.ADMIN.equals(r.ruolo())) return new Ambito(true, Set.of());
            impianti.add(r.impiantoId());
        }
        return new Ambito(false, Set.copyOf(impianti));
    }

    /** Dove l'utente ha un ruolo qualsiasi (es. dove vede il piano vasca). */
    public Ambito ambitoDeiRuoli(Utente u) {
        Set<UUID> impianti = new HashSet<>();
        for (RuoloAssegnato r : ruoli(u)) {
            if (r.impiantoId() == null || RuoloDefinito.ADMIN.equals(r.ruolo())) return new Ambito(true, Set.of());
            impianti.add(r.impiantoId());
        }
        return new Ambito(false, Set.copyOf(impianti));
    }

    public boolean puo(Utente u, Permesso p, UUID impiantoId) {
        return ambito(u, p).comprende(impiantoId);
    }

    /** Ha il permesso in almeno un impianto. */
    public boolean puo(Utente u, Permesso p) {
        return !ambito(u, p).vuoto();
    }

    public void richiedi(Utente u, Permesso p, UUID impiantoId) {
        if (!puo(u, p, impiantoId)) throw new AccessDeniedException("Permesso mancante: " + p.nome() + ".");
    }

    /** I permessi che l'utente ha da qualche parte (per l'interfaccia). */
    public Set<Permesso> permessi(Utente u) {
        Set<Permesso> out = EnumSet.noneOf(Permesso.class);
        for (RuoloAssegnato r : ruoli(u)) out.addAll(permessiDelRuolo(r.ruolo()));
        return out;
    }

    /** Per {@code @PreAuthorize}: almeno uno dei permessi, in almeno un impianto. */
    public boolean ha(Authentication auth, String... permessi) {
        if (auth == null || !(auth.getPrincipal() instanceof Utente u)) return false;
        Set<Permesso> suoi = permessi(u);
        return Arrays.stream(permessi).map(Permesso::valueOf).anyMatch(suoi::contains);
    }
}
