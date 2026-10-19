package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Ruolo;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Il lavoro del coordinatore sul piano: corsi, lezioni ricorrenti, salti e spostamenti, eventi. */
@Service
class GestionePianoService {

    private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    record LezioneDto(UUID id, int giornoSettimana, LocalTime inizio, LocalTime fine, LocalDate validaDal, LocalDate validaAl,
                      List<UUID> spazi, PersonaBreve istruttore, Integer posti) {}

    record CorsoDto(UUID id, UUID impiantoId, String nome, TipoCorso tipo, String livello, Integer etaDa, Integer etaA,
                    PersonaBreve istruttore, String note, boolean attivo, String colore, List<LezioneDto> lezioni) {}

    record CorsoInput(UUID impiantoId, String nome, TipoCorso tipo, String livello, Integer etaDa, Integer etaA, UUID istruttoreId,
                      String note, Boolean attivo, String colore) {}

    record LezioneInput(int giornoSettimana, LocalTime inizio, LocalTime fine, LocalDate validaDal, LocalDate validaAl,
                        List<UUID> spazi, UUID istruttoreId, Integer posti) {}

    record EccezioneInput(LocalDate data, TipoEccezione tipo, LocalDate nuovaData, LocalTime nuovoInizio, LocalTime nuovoFine, String motivo) {}

    record EventoInput(UUID impiantoId, TipoEvento tipo, String titolo, LocalDate data, LocalDate dataFine, LocalTime inizio, LocalTime fine,
                       List<UUID> spazi, UUID istruttoreId, String note) {}

    record EventoDto(UUID id, UUID impiantoId, TipoEvento tipo, String titolo, LocalDate data, LocalDate dataFine, LocalTime inizio,
                     LocalTime fine, List<UUID> spazi, PersonaBreve istruttore, String note, boolean annullato) {}

    /** Dopo una modifica: le sovrapposizioni che si sono create, da mostrare come avviso (non bloccano). */
    record Esito<T>(T valore, List<String> avvisi) {}

    private final CorsoRepository corsi;
    private final LezioneRepository lezioni;
    private final EccezioneRepository eccezioni;
    private final EventoRepository eventi;
    private final PianoService piano;
    private final Impianti impianti;
    private final Persone persone;
    private final AccessoPiano accesso;
    private final FasceService fasce;
    private final IscrizioneRepository iscrizioni;

    GestionePianoService(CorsoRepository corsi, LezioneRepository lezioni, EccezioneRepository eccezioni, EventoRepository eventi,
                         PianoService piano, Impianti impianti, Persone persone, AccessoPiano accesso, FasceService fasce,
                         IscrizioneRepository iscrizioni) {
        this.iscrizioni = iscrizioni;
        this.fasce = fasce;
        this.corsi = corsi;
        this.lezioni = lezioni;
        this.eccezioni = eccezioni;
        this.eventi = eventi;
        this.piano = piano;
        this.impianti = impianti;
        this.persone = persone;
        this.accesso = accesso;
    }

    // ── Corsi ───────────────────────────────────────────────────────────────

    /** Tutti i corsi degli impianti visibili (anche quelli non più attivi), con le lezioni. */
    @Transactional(readOnly = true)
    List<CorsoDto> corsi(Utente u) {
        Set<UUID> visibili = accesso.visibili(u);
        if (visibili.isEmpty()) return List.of();
        List<Corso> elenco = corsi.findByImpiantoIdInOrderByNome(visibili);
        if (elenco.isEmpty()) return List.of();
        Map<UUID, List<Lezione>> perCorso = new LinkedHashMap<>();
        lezioni.deiCorsi(elenco.stream().map(Corso::getId).toList()).forEach(l -> perCorso.computeIfAbsent(l.getCorso().getId(), k -> new ArrayList<>()).add(l));
        Map<UUID, String> nomi = nomi(elenco, perCorso.values().stream().flatMap(List::stream).toList());
        return elenco.stream().map(c -> dto(c, perCorso.getOrDefault(c.getId(), List.of()), nomi)).toList();
    }

    @Transactional
    CorsoDto creaCorso(Utente u, CorsoInput in) {
        if (in.impiantoId() == null) throw new IllegalArgumentException("Scegli l'impianto.");
        accesso.richiediGestione(u, in.impiantoId());
        controllaIstruttore(in.istruttoreId(), in.impiantoId());
        Corso c = new Corso(in.impiantoId());
        c.modifica(in.nome(), in.tipo(), in.livello(), in.etaDa(), in.etaA(), in.istruttoreId(), in.note(), in.attivo() == null || in.attivo(), in.colore());
        corsi.saveAndFlush(c);
        return dto(c, List.of(), nomi(List.of(c), List.of()));
    }

    @Transactional
    CorsoDto modificaCorso(Utente u, UUID id, CorsoInput in) {
        Corso c = corso(u, id);
        controllaIstruttore(in.istruttoreId(), c.getImpiantoId());
        c.modifica(in.nome(), in.tipo(), in.livello(), in.etaDa(), in.etaA(), in.istruttoreId(), in.note(), in.attivo() == null ? c.isAttivo() : in.attivo(), in.colore());
        corsi.flush();
        List<Lezione> sue = lezioni.deiCorsi(List.of(id));
        return dto(c, sue, nomi(List.of(c), sue));
    }

    // ── Lezioni ─────────────────────────────────────────────────────────────

    @Transactional
    Esito<LezioneDto> aggiungiLezione(Utente u, UUID corsoId, LezioneInput in) {
        Corso c = corso(u, corsoId);
        Lezione l = new Lezione(c);
        applica(l, c, in);
        lezioni.saveAndFlush(l);
        return new Esito<>(dto(l, nomi(List.of(c), List.of(l))), avvisiLezione(l));
    }

    @Transactional
    Esito<LezioneDto> modificaLezione(Utente u, UUID id, LezioneInput in) {
        Lezione l = lezione(u, id);
        applica(l, l.getCorso(), in);
        lezioni.flush();
        return new Esito<>(dto(l, nomi(List.of(l.getCorso()), List.of(l))), avvisiLezione(l));
    }

    /**
     * Toglie il turno dal piano. Se è già iniziato (o ha degli iscritti) resta nello storico e smette da oggi;
     * se non è ancora iniziato e nessuno è iscritto si elimina.
     */
    @Transactional
    void togliLezione(Utente u, UUID id) {
        Lezione l = lezione(u, id);
        LocalDate oggi = LocalDate.now(FUSO);
        boolean conIscritti = !iscrizioni.findByLezioneIdOrderByDal(id).isEmpty();
        if (l.getValidaDal().isAfter(oggi) && !conIscritti) lezioni.delete(l);
        else l.terminaIl(l.getValidaDal().isAfter(oggi) ? l.getValidaDal() : oggi);
    }

    @Transactional
    Esito<UUID> aggiungiEccezione(Utente u, UUID lezioneId, EccezioneInput in) {
        Lezione l = lezione(u, lezioneId);
        if (in.data() == null) throw new IllegalArgumentException("Indica il giorno della lezione.");
        Eccezione e = l.aggiungiEccezione(in.data(), in.tipo(), in.nuovaData(), in.nuovoInizio(), in.nuovoFine(), in.motivo());
        lezioni.flush();
        List<String> avvisi = e.getTipo() == TipoEccezione.SPOSTATA
            ? avvisi(l.getCorso().getImpiantoId(), e.getNuovaData(), e.getNuovaData(), o -> e.getId().equals(o.eccezioneId()) && o.attiva())
            : List.of();
        return new Esito<>(e.getId(), avvisi);
    }

    /** Annulla il salto o lo spostamento: la lezione torna regolare in quella data. */
    @Transactional
    void togliEccezione(Utente u, UUID id) {
        Eccezione e = eccezioni.findById(id).orElseThrow(() -> new NonTrovato("Modifica non trovata."));
        Lezione l = lezione(u, e.getLezione().getId());
        l.getEccezioni().remove(e);
    }

    // ── Eventi ──────────────────────────────────────────────────────────────

    @Transactional
    Esito<EventoDto> creaEvento(Utente u, EventoInput in) {
        if (in.impiantoId() == null) throw new IllegalArgumentException("Scegli l'impianto.");
        accesso.richiediGestione(u, in.impiantoId());
        Evento e = new Evento(in.impiantoId());
        applica(e, in);
        eventi.saveAndFlush(e);
        return new Esito<>(dto(e), avvisiEvento(e));
    }

    @Transactional
    Esito<EventoDto> modificaEvento(Utente u, UUID id, EventoInput in) {
        Evento e = evento(u, id);
        if (e.isAnnullato()) throw new it.leonexiii.gestionale.condiviso.Conflitto("L'evento è annullato: non si può modificare.");
        applica(e, in);
        eventi.flush();
        return new Esito<>(dto(e), avvisiEvento(e));
    }

    @Transactional
    EventoDto annullaEvento(Utente u, UUID id) {
        Evento e = evento(u, id);
        e.annulla();
        return dto(e);
    }

    @Transactional(readOnly = true)
    EventoDto evento(Utente u, UUID id, boolean soloLettura) {
        Evento e = eventi.findById(id).orElseThrow(() -> new NonTrovato("Evento non trovato."));
        if (!accesso.visibili(u).contains(e.getImpiantoId())) throw new NonTrovato("Evento non trovato.");
        return dto(e);
    }

    // ── Supporto ────────────────────────────────────────────────────────────

    private void applica(Lezione l, Corso c, LezioneInput in) {
        Set<UUID> spazi = in.spazi() == null ? Set.of() : new HashSet<>(in.spazi());
        controllaSpazi(spazi, c.getImpiantoId());
        controllaIstruttore(in.istruttoreId(), c.getImpiantoId());
        l.modifica(in.giornoSettimana(), in.inizio(), in.fine(), in.validaDal(), in.validaAl(), spazi, in.istruttoreId());
        l.posti(in.posti());
        controllaAssettoFascia(spazi, c.getImpiantoId(), in);
    }

    private void applica(Evento e, EventoInput in) {
        Set<UUID> spazi = in.spazi() == null ? Set.of() : new HashSet<>(in.spazi());
        controllaSpazi(spazi, e.getImpiantoId());
        controllaIstruttore(in.istruttoreId(), e.getImpiantoId());
        e.modifica(in.tipo(), in.titolo(), in.data(), in.dataFine(), in.inizio(), in.fine(), spazi, in.istruttoreId(), in.note());
    }

    /** Sovrapposizioni della lezione nelle prossime quattro settimane in cui vale. */
    private List<String> avvisiLezione(Lezione l) {
        LocalDate da = l.getValidaDal().isAfter(LocalDate.now(FUSO)) ? l.getValidaDal() : LocalDate.now(FUSO);
        LocalDate a = da.plusWeeks(4);
        if (l.getValidaAl() != null && l.getValidaAl().isBefore(a)) a = l.getValidaAl();
        if (a.isBefore(da)) return List.of();
        return avvisi(l.getCorso().getImpiantoId(), da, a, o -> l.getId().equals(o.lezioneId()) && o.attiva());
    }

    private List<String> avvisiEvento(Evento e) {
        if (e.getTipo() == TipoEvento.CHIUSURA) return List.of();
        return avvisi(e.getImpiantoId(), e.getData(), e.ultimoGiorno(), o -> e.getId().equals(o.eventoId()) && o.attiva());
    }

    private List<String> avvisi(UUID impiantoId, LocalDate da, LocalDate a, java.util.function.Predicate<PianoService.Occorrenza> mia) {
        List<PianoService.SpazioDto> spazi = impianti.spazi(Set.of(impiantoId)).stream()
            .map(PianoService::dto).toList();
        if (java.time.temporal.ChronoUnit.DAYS.between(da, a) > PianoService.GIORNI_MASSIMI) a = da.plusDays(PianoService.GIORNI_MASSIMI);
        return piano.calcola(Set.of(impiantoId), spazi, da, a).stream()
            .filter(mia).filter(o -> !o.conflitti().isEmpty())
            .map(o -> o.data() + ": si sovrappone a " + String.join(", ", o.conflitti()))
            .distinct().limit(10).toList();
    }

    /** Gli spazi sono dell'impianto e, se sono corsie della stessa vasca, dello stesso assetto (tutte a 6 o tutte a 5). */
    private void controllaSpazi(Set<UUID> spazi, UUID impiantoId) {
        if (spazi.isEmpty()) return;
        Map<UUID, Impianti.SpazioBreve> perId = new java.util.HashMap<>();
        impianti.spazi(Set.of(impiantoId)).forEach(s -> perId.put(s.id(), s));
        Map<UUID, Integer> assettoPerVasca = new java.util.HashMap<>();
        for (UUID id : spazi) {
            Impianti.SpazioBreve s = perId.get(id);
            if (s == null) throw new IllegalArgumentException("Uno spazio non appartiene all'impianto.");
            if (s.padreId() == null || s.assetto() == null) continue;
            Integer prima = assettoPerVasca.putIfAbsent(s.padreId(), s.assetto());
            if (prima != null && !prima.equals(s.assetto()))
                throw new IllegalArgumentException("Corsie di assetti diversi: scegli corsie della vasca divisa tutte nello stesso modo (a " + prima + " o a " + s.assetto() + ").");
        }
    }

    /** Se la fascia in cui cade il turno ha un assetto (es. 5 corsie), le corsie scelte devono essere di quell'assetto. */
    private void controllaAssettoFascia(Set<UUID> spazi, UUID impiantoId, LezioneInput in) {
        Map<UUID, Impianti.SpazioBreve> perId = new java.util.HashMap<>();
        impianti.spazi(Set.of(impiantoId)).forEach(s -> perId.put(s.id(), s));
        for (UUID id : spazi) {
            Impianti.SpazioBreve s = perId.get(id);
            if (s == null || s.padreId() == null || s.assetto() == null) continue;
            Integer richiesto = fasce.assettoPer(s.padreId(), in.giornoSettimana(), in.inizio(), in.fine(), in.validaDal(), in.validaAl());
            if (richiesto != null && !richiesto.equals(s.assetto()))
                throw new IllegalArgumentException("In quella fascia la vasca è divisa in " + richiesto + " corsie: scegli le corsie a " + richiesto
                    + " (o cambia prima l'assetto della fascia).");
        }
    }

    private void controllaIstruttore(UUID personaId, UUID impiantoId) {
        if (personaId == null) return;
        if (!persone.haRuolo(personaId, Ruolo.STAFF, impiantoId) && !persone.haRuolo(personaId, Ruolo.COORDINATORE, impiantoId))
            throw new IllegalArgumentException("L'istruttore deve far parte dello staff dell'impianto.");
    }

    private Corso corso(Utente u, UUID id) {
        Corso c = corsi.findById(id).orElseThrow(() -> new NonTrovato("Corso non trovato."));
        accesso.richiediGestione(u, c.getImpiantoId());
        return c;
    }

    private Lezione lezione(Utente u, UUID id) {
        Lezione l = lezioni.completa(id).orElseThrow(() -> new NonTrovato("Lezione non trovata."));
        accesso.richiediGestione(u, l.getCorso().getImpiantoId());
        return l;
    }

    private Evento evento(Utente u, UUID id) {
        Evento e = eventi.findById(id).orElseThrow(() -> new NonTrovato("Evento non trovato."));
        accesso.richiediGestione(u, e.getImpiantoId());
        return e;
    }

    private Map<UUID, String> nomi(List<Corso> c, List<Lezione> l) {
        return persone.nomiCompleti(Stream.concat(c.stream().map(Corso::getIstruttoreId), l.stream().map(Lezione::getIstruttoreId))
            .filter(Objects::nonNull).distinct().toList());
    }

    private static PersonaBreve breve(UUID id, Map<UUID, String> nomi) {
        return id == null ? null : new PersonaBreve(id, nomi.getOrDefault(id, "?"));
    }

    private CorsoDto dto(Corso c, List<Lezione> sue, Map<UUID, String> nomi) {
        return new CorsoDto(c.getId(), c.getImpiantoId(), c.getNome(), c.getTipo(), c.getLivello(), c.getEtaDa(), c.getEtaA(),
            breve(c.getIstruttoreId(), nomi), c.getNote(), c.isAttivo(), c.getColore(),
            sue.stream().sorted(Comparator.comparing(Lezione::getGiornoSettimana).thenComparing(Lezione::getInizio))
                .map(l -> dto(l, nomi)).toList());
    }

    private static LezioneDto dto(Lezione l, Map<UUID, String> nomi) {
        return new LezioneDto(l.getId(), l.getGiornoSettimana(), l.getInizio(), l.getFine(), l.getValidaDal(), l.getValidaAl(),
            l.getSpazi().stream().sorted().toList(), breve(l.getIstruttoreId(), nomi), l.getPosti());
    }

    private EventoDto dto(Evento e) {
        Map<UUID, String> nomi = e.getIstruttoreId() == null ? Map.of() : persone.nomiCompleti(List.of(e.getIstruttoreId()));
        return new EventoDto(e.getId(), e.getImpiantoId(), e.getTipo(), e.getTitolo(), e.getData(), e.getDataFine(), e.getInizio(), e.getFine(),
            e.getSpazi().stream().sorted().toList(), breve(e.getIstruttoreId(), nomi), e.getNote(), e.isAnnullato());
    }
}
