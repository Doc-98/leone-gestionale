package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Ruolo;
import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Il lavoro del coordinatore: turni dell'impianto, creazione e modifica, generazione del mese dal modello. */
@Service
class PianificazioneService {

    /** Oltre questo intervallo la richiesta è quasi certamente un errore (e pesante). */
    static final long GIORNI_MASSIMI = 93;

    record TurnoInput(UUID postazioneId, LocalDate data, LocalTime inizio, LocalTime fine, UUID personaId, String note) {}

    record PostazioneDto(UUID id, String nome, String mansione, UUID impiantoId) {}

    record RigaModelloDto(UUID id, UUID postazioneId, String postazione, int giornoSettimana, LocalTime inizio, LocalTime fine, PersonaBreve persona) {}

    record ModelloDto(UUID id, UUID impiantoId, String nome, LocalDate validoDal, LocalDate validoAl, List<RigaModelloDto> righe) {}

    record RigaInput(UUID postazioneId, int giornoSettimana, LocalTime inizio, LocalTime fine, UUID personaId) {}

    record EsitoGenerazione(int creati, int scoperti, int giaPresenti, List<String> avvisi) {}

    private final TurnoRepository turni;
    private final PostazioneRepository postazioni;
    private final ModelloRepository modelli;
    private final Persone persone;
    private final Permessi permessi;

    PianificazioneService(TurnoRepository turni, PostazioneRepository postazioni, ModelloRepository modelli, Persone persone, Permessi permessi) {
        this.turni = turni;
        this.postazioni = postazioni;
        this.modelli = modelli;
        this.persone = persone;
        this.permessi = permessi;
    }

    @Transactional(readOnly = true)
    List<TurnoDto> diPersona(UUID personaId, LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);
        return turni.diPersona(personaId, da, a).stream().map(t -> TurnoDto.da(t, null)).toList();
    }

    /** Tutti i turni (anche scoperti) degli impianti che l'utente gestisce. */
    @Transactional(readOnly = true)
    List<TurnoDto> tutti(Utente u, LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);
        Set<UUID> impianti = permessi.impiantiGestiti(u);
        if (impianti.isEmpty()) return List.of();
        List<Turno> elenco = turni.nelPeriodo(da, a, impianti);
        var nomi = persone.nomiCompleti(elenco.stream().map(Turno::getPersonaId).filter(java.util.Objects::nonNull).distinct().toList());
        return elenco.stream().map(t -> TurnoDto.conPersona(t, nomi)).toList();
    }

    @Transactional
    TurnoDto crea(Utente u, TurnoInput in) {
        Postazione p = postazione(in.postazioneId());
        permessi.richiediGestione(u, p.getMansione().getImpiantoId());
        controllaPersona(in.personaId(), p.getMansione().getImpiantoId());
        controllaSovrapposizione(in.personaId(), in.data(), in.inizio(), in.fine(), TurnoRepository.NESSUNO);
        Turno t = turni.saveAndFlush(new Turno(p, in.data(), in.inizio(), in.fine(), in.personaId(), in.note(), null));
        return conNome(t);
    }

    @Transactional
    TurnoDto modifica(Utente u, UUID id, TurnoInput in) {
        Turno t = turno(id);
        permessi.richiediGestione(u, t.impiantoId());
        if (!t.pianificato()) throw new Conflitto("Il turno è annullato: non si può modificare.");
        Postazione p = in.postazioneId() == null ? t.getPostazione() : postazione(in.postazioneId());
        permessi.richiediGestione(u, p.getMansione().getImpiantoId());
        controllaPersona(in.personaId(), p.getMansione().getImpiantoId());
        controllaSovrapposizione(in.personaId(), in.data(), in.inizio(), in.fine(), t.getId());
        t.modifica(p, in.data(), in.inizio(), in.fine(), in.personaId(), in.note());
        turni.flush();
        return conNome(t);
    }

    @Transactional
    TurnoDto annulla(Utente u, UUID id) {
        Turno t = turno(id);
        permessi.richiediGestione(u, t.impiantoId());
        t.annulla();
        return conNome(t);
    }

    @Transactional(readOnly = true)
    List<PostazioneDto> postazioni(Utente u) {
        Set<UUID> impianti = permessi.impiantiGestiti(u);
        if (impianti.isEmpty()) return List.of();
        return postazioni.attive(impianti).stream()
            .map(p -> new PostazioneDto(p.getId(), p.getNome(), p.getMansione().getNome(), p.getMansione().getImpiantoId()))
            .toList();
    }

    /** Lo staff degli impianti gestiti: le persone a cui si possono assegnare i turni. */
    @Transactional(readOnly = true)
    List<PersonaBreve> personale(Utente u) {
        var unici = new LinkedHashMap<UUID, PersonaBreve>();
        for (UUID imp : permessi.impiantiGestiti(u)) for (PersonaBreve p : persone.conRuolo(Ruolo.STAFF, imp)) unici.putIfAbsent(p.id(), p);
        return unici.values().stream().sorted(Comparator.comparing(PersonaBreve::nome)).toList();
    }

    // ── Modello settimanale ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<ModelloDto> modelli(Utente u) {
        Set<UUID> impianti = permessi.impiantiGestiti(u);
        if (impianti.isEmpty()) return List.of();
        List<ModelloSettimanale> elenco = modelli.diImpianti(impianti);
        var nomi = persone.nomiCompleti(elenco.stream().flatMap(m -> m.getRighe().stream()).map(ModelloRiga::getPersonaId)
            .filter(java.util.Objects::nonNull).distinct().toList());
        return elenco.stream().map(m -> modelloDto(m, nomi)).toList();
    }

    @Transactional
    ModelloDto aggiungiRiga(Utente u, UUID modelloId, RigaInput in) {
        ModelloSettimanale m = modello(u, modelloId);
        Postazione p = postazione(in.postazioneId());
        if (!p.getMansione().getImpiantoId().equals(m.getImpiantoId())) throw new IllegalArgumentException("La postazione è di un altro impianto.");
        controllaPersona(in.personaId(), m.getImpiantoId());
        m.aggiungiRiga(p, in.giornoSettimana(), in.inizio(), in.fine(), in.personaId());
        modelli.flush();
        return modelloDto(m, nomiRighe(m));
    }

    @Transactional
    ModelloDto togliRiga(Utente u, UUID modelloId, UUID rigaId) {
        ModelloSettimanale m = modello(u, modelloId);
        if (!m.getRighe().removeIf(r -> r.getId().equals(rigaId))) throw new NonTrovato("Riga del modello non trovata.");
        return modelloDto(m, nomiRighe(m));
    }

    /**
     * Crea i turni del mese dalla settimana tipo. Si può rilanciare: i turni già presenti (stessa postazione,
     * giorno e ora di inizio) non vengono duplicati. Se la persona del modello ha già un turno che si accavalla,
     * il turno nasce scoperto e compare un avviso.
     */
    @Transactional
    EsitoGenerazione genera(Utente u, UUID modelloId, YearMonth mese) {
        ModelloSettimanale m = modello(u, modelloId);
        int creati = 0, scoperti = 0, presenti = 0;
        List<String> avvisi = new ArrayList<>();
        var nomi = nomiRighe(m);
        for (LocalDate g = mese.atDay(1); !g.isAfter(mese.atEndOfMonth()); g = g.plusDays(1)) {
            if (!m.validoIl(g)) continue;
            int dow = g.getDayOfWeek().getValue();
            for (ModelloRiga r : m.getRighe()) {
                if (r.getGiornoSettimana() != dow) continue;
                if (turni.esisteGia(r.getPostazione().getId(), g, r.getInizio())) { presenti++; continue; }
                UUID persona = r.getPersonaId();
                if (persona != null && turni.sovrapposto(persona, g, r.getInizio(), r.getFine(), TurnoRepository.NESSUNO)) {
                    avvisi.add(g + " · " + r.getPostazione().getNome() + ": " + nomi.getOrDefault(persona, "?")
                        + " ha già un turno in quell'orario, il turno resta da assegnare.");
                    persona = null;
                }
                turni.saveAndFlush(new Turno(r.getPostazione(), g, r.getInizio(), r.getFine(), persona, null, r.getId()));
                if (persona == null) scoperti++; else creati++;
            }
        }
        return new EsitoGenerazione(creati, scoperti, presenti, avvisi);
    }

    // ── Controlli ───────────────────────────────────────────────────────────

    static void controllaIntervallo(LocalDate da, LocalDate a) {
        if (a.isBefore(da)) throw new IllegalArgumentException("La data finale è prima di quella iniziale.");
        if (ChronoUnit.DAYS.between(da, a) > GIORNI_MASSIMI)
            throw new IllegalArgumentException("Intervallo troppo lungo: al massimo " + GIORNI_MASSIMI + " giorni.");
    }

    private void controllaPersona(UUID personaId, UUID impiantoId) {
        if (personaId != null && !persone.haRuolo(personaId, Ruolo.STAFF, impiantoId))
            throw new IllegalArgumentException("Questa persona non fa parte dello staff dell'impianto.");
    }

    private void controllaSovrapposizione(UUID personaId, LocalDate data, LocalTime inizio, LocalTime fine, UUID escluso) {
        if (inizio == null || fine == null || !fine.isAfter(inizio)) throw new IllegalArgumentException("La fine del turno deve essere dopo l'inizio.");
        if (personaId != null && turni.sovrapposto(personaId, data, inizio, fine, escluso))
            throw new Conflitto("La persona ha già un turno in quell'orario.");
    }

    private Turno turno(UUID id) {
        return turni.findById(id).orElseThrow(() -> new NonTrovato("Turno non trovato."));
    }

    private Postazione postazione(UUID id) {
        if (id == null) throw new IllegalArgumentException("Scegli la postazione.");
        return postazioni.findById(id).orElseThrow(() -> new NonTrovato("Postazione non trovata."));
    }

    private ModelloSettimanale modello(Utente u, UUID id) {
        ModelloSettimanale m = modelli.findById(id).orElseThrow(() -> new NonTrovato("Modello non trovato."));
        permessi.richiediGestione(u, m.getImpiantoId());
        return m;
    }

    private java.util.Map<UUID, String> nomiRighe(ModelloSettimanale m) {
        return persone.nomiCompleti(m.getRighe().stream().map(ModelloRiga::getPersonaId).filter(java.util.Objects::nonNull).distinct().toList());
    }

    private ModelloDto modelloDto(ModelloSettimanale m, java.util.Map<UUID, String> nomi) {
        return new ModelloDto(m.getId(), m.getImpiantoId(), m.getNome(), m.getValidoDal(), m.getValidoAl(),
            m.getRighe().stream().map(r -> new RigaModelloDto(r.getId(), r.getPostazione().getId(), r.getPostazione().getNome(),
                r.getGiornoSettimana(), r.getInizio(), r.getFine(),
                r.getPersonaId() == null ? null : new PersonaBreve(r.getPersonaId(), nomi.getOrDefault(r.getPersonaId(), "?")))).toList());
    }

    private TurnoDto conNome(Turno t) {
        return TurnoDto.conPersona(t, t.getPersonaId() == null ? java.util.Map.of() : persone.nomiCompleti(List.of(t.getPersonaId())));
    }
}
