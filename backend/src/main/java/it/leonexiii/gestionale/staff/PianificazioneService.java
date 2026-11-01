package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.condiviso.Avviso;
import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.pianovasca.Chiusure;
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

    /** forza: il coordinatore ha visto l'avviso (es. la persona ha già un turno in quell'orario) e conferma. */
    record TurnoInput(UUID postazioneId, LocalDate data, LocalTime inizio, LocalTime fine, UUID personaId, String note, Boolean forza) {}

    /** soloSesso: «M», «F» o null (chiunque), es. gli spogliatoi. */
    record PostazioneDto(UUID id, String nome, String mansione, UUID impiantoId, UUID mansioneId, String soloSesso) {}

    /** Una persona dello staff, con il sesso dell'anagrafica (per le postazioni riservate). */
    record PersonaStaff(UUID id, String nome, String sesso) {}

    record RigaModelloDto(UUID id, UUID postazioneId, String postazione, int giornoSettimana, LocalTime inizio, LocalTime fine, PersonaBreve persona) {}

    record ModelloDto(UUID id, UUID impiantoId, String nome, LocalDate validoDal, LocalDate validoAl, List<RigaModelloDto> righe) {}

    record RigaInput(UUID postazioneId, int giornoSettimana, LocalTime inizio, LocalTime fine, UUID personaId) {}

    /** Nuovo modello; con copiaDa riprende le righe di un modello esistente (es. la stagione precedente). */
    record ModelloInput(UUID impiantoId, String nome, LocalDate validoDal, LocalDate validoAl, UUID copiaDa) {}

    record EsitoGenerazione(int creati, int scoperti, int giaPresenti, int chiusi, List<String> avvisi) {}

    private final TurnoRepository turni;
    private final PostazioneRepository postazioni;
    private final ModelloRepository modelli;
    private final Persone persone;
    private final Permessi permessi;
    private final Chiusure chiusure;

    PianificazioneService(TurnoRepository turni, PostazioneRepository postazioni, ModelloRepository modelli, Persone persone, Permessi permessi,
                          Chiusure chiusure) {
        this.turni = turni;
        this.postazioni = postazioni;
        this.modelli = modelli;
        this.persone = persone;
        this.permessi = permessi;
        this.chiusure = chiusure;
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
        Set<UUID> impianti = permessi.impianti(u, Permesso.PIANIFICAZIONE);
        if (impianti.isEmpty()) return List.of();
        List<Turno> elenco = turni.nelPeriodo(da, a, impianti);
        var nomi = persone.nomiCompleti(elenco.stream().map(Turno::getPersonaId).filter(java.util.Objects::nonNull).distinct().toList());
        return elenco.stream().map(t -> TurnoDto.conPersona(t, nomi)).toList();
    }

    /** I turni dello staff degli impianti in cui lavoro: per vedere con chi sono e proporre scambi. */
    @Transactional(readOnly = true)
    List<TurnoDto> turniStaff(Utente u, LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);
        Set<UUID> impianti = permessi.impiantiStaff(u);
        if (impianti.isEmpty()) return List.of();
        List<Turno> elenco = turni.nelPeriodo(da, a, impianti).stream().filter(Turno::pianificato).toList();
        var nomi = persone.nomiCompleti(elenco.stream().map(Turno::getPersonaId).filter(java.util.Objects::nonNull).distinct().toList());
        return elenco.stream().map(t -> TurnoDto.conPersona(t, nomi)).toList();
    }

    /** I colleghi dello staff (escluso me), per chiedere una sostituzione a qualcuno in particolare. */
    @Transactional(readOnly = true)
    List<PersonaBreve> colleghi(Utente u) {
        var unici = new LinkedHashMap<UUID, PersonaBreve>();
        for (UUID imp : permessi.impiantiStaff(u)) for (PersonaBreve p : persone.conPermesso(Permesso.TURNI, imp)) unici.putIfAbsent(p.id(), p);
        unici.remove(u.personaId());
        return unici.values().stream().sorted(Comparator.comparing(PersonaBreve::nome)).toList();
    }

    @Transactional
    TurnoDto crea(Utente u, TurnoInput in) {
        Postazione p = postazione(in.postazioneId());
        permessi.richiedi(u, Permesso.PIANIFICAZIONE, p.getMansione().getImpiantoId());
        controlla(p, in, TurnoRepository.NESSUNO);
        Turno t = turni.saveAndFlush(new Turno(p, in.data(), in.inizio(), in.fine(), in.personaId(), in.note(), null));
        return conNome(t);
    }

    @Transactional
    TurnoDto modifica(Utente u, UUID id, TurnoInput in) {
        Turno t = turno(id);
        permessi.richiedi(u, Permesso.PIANIFICAZIONE, t.impiantoId());
        if (!t.pianificato()) throw new Conflitto("Il turno è annullato: non si può modificare.");
        Postazione p = in.postazioneId() == null ? t.getPostazione() : postazione(in.postazioneId());
        permessi.richiedi(u, Permesso.PIANIFICAZIONE, p.getMansione().getImpiantoId());
        controlla(p, in, t.getId());
        t.modifica(p, in.data(), in.inizio(), in.fine(), in.personaId(), in.note());
        turni.flush();
        return conNome(t);
    }

    @Transactional
    TurnoDto annulla(Utente u, UUID id) {
        Turno t = turno(id);
        permessi.richiedi(u, Permesso.PIANIFICAZIONE, t.impiantoId());
        t.annulla();
        return conNome(t);
    }

    @Transactional(readOnly = true)
    List<PostazioneDto> postazioni(Utente u) {
        Set<UUID> impianti = permessi.impianti(u, Permesso.PIANIFICAZIONE);
        if (impianti.isEmpty()) return List.of();
        return postazioni.attive(impianti).stream()
            .map(p -> new PostazioneDto(p.getId(), p.getNome(), p.getMansione().getNome(), p.getMansione().getImpiantoId(), p.getMansione().getId(), p.getSoloSesso()))
            .toList();
    }

    /** Lo staff degli impianti gestiti: le persone a cui si possono assegnare i turni. */
    @Transactional(readOnly = true)
    List<PersonaStaff> personale(Utente u) {
        var unici = new LinkedHashMap<UUID, PersonaBreve>();
        for (UUID imp : permessi.impianti(u, Permesso.PIANIFICAZIONE)) for (PersonaBreve p : persone.conPermesso(Permesso.TURNI, imp)) unici.putIfAbsent(p.id(), p);
        var sessi = persone.sessi(unici.keySet());
        return unici.values().stream().sorted(Comparator.comparing(PersonaBreve::nome))
            .map(p -> new PersonaStaff(p.id(), p.nome(), sessi.get(p.id()))).toList();
    }

    // ── Modello settimanale ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<ModelloDto> modelli(Utente u) {
        Set<UUID> impianti = permessi.impianti(u, Permesso.PIANIFICAZIONE);
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
        controllaRiga(m, p, in, null);
        m.aggiungiRiga(p, in.giornoSettimana(), in.inizio(), in.fine(), in.personaId());
        modelli.flush();
        return modelloDto(m, nomiRighe(m));
    }

    @Transactional
    ModelloDto creaModello(Utente u, ModelloInput in) {
        if (in.impiantoId() == null) throw new IllegalArgumentException("Scegli l'impianto.");
        permessi.richiedi(u, Permesso.PIANIFICAZIONE, in.impiantoId());
        ModelloSettimanale m = new ModelloSettimanale(in.impiantoId(), in.nome(), in.validoDal(), in.validoAl());
        if (in.copiaDa() != null) {
            ModelloSettimanale origine = modello(u, in.copiaDa());
            if (!origine.getImpiantoId().equals(in.impiantoId())) throw new IllegalArgumentException("Il modello da copiare è di un altro impianto.");
            for (ModelloRiga r : origine.getRighe())
                m.aggiungiRiga(r.getPostazione(), r.getGiornoSettimana(), r.getInizio(), r.getFine(), r.getPersonaId());
        }
        modelli.saveAndFlush(m);
        return modelloDto(m, nomiRighe(m));
    }

    @Transactional
    ModelloDto modificaModello(Utente u, UUID id, ModelloInput in) {
        ModelloSettimanale m = modello(u, id);
        m.modifica(in.nome(), in.validoDal(), in.validoAl());
        modelli.flush();
        return modelloDto(m, nomiRighe(m));
    }

    /** Si elimina solo un modello mai usato per generare turni; altrimenti lo si chiude con una data di fine. */
    @Transactional
    void eliminaModello(Utente u, UUID id) {
        ModelloSettimanale m = modello(u, id);
        var righe = m.getRighe().stream().map(ModelloRiga::getId).toList();
        if (!righe.isEmpty() && turni.existsByModelloRigaIdIn(righe))
            throw new Conflitto("Il modello è già stato usato per generare turni: invece di eliminarlo, indica fino a quando vale.");
        modelli.delete(m);
    }

    @Transactional
    ModelloDto modificaRiga(Utente u, UUID modelloId, UUID rigaId, RigaInput in) {
        ModelloSettimanale m = modello(u, modelloId);
        ModelloRiga r = m.getRighe().stream().filter(x -> x.getId().equals(rigaId)).findFirst()
            .orElseThrow(() -> new NonTrovato("Riga del modello non trovata."));
        Postazione p = postazione(in.postazioneId());
        if (!p.getMansione().getImpiantoId().equals(m.getImpiantoId())) throw new IllegalArgumentException("La postazione è di un altro impianto.");
        controllaRiga(m, p, in, rigaId);
        r.modifica(p, in.giornoSettimana(), in.inizio(), in.fine(), in.personaId());
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
     * Crea i turni del mese dalla settimana tipo. Si può rilanciare: dove la postazione ha già un turno in
     * quell'orario non se ne crea un altro. Rispetta il calendario della stagione: nei giorni di chiusura non crea
     * turni, a orario ridotto li accorcia. Se la persona del modello ha già un turno che si accavalla, o la
     * postazione è riservata all'altro sesso, il turno nasce scoperto e compare un avviso.
     */
    @Transactional
    EsitoGenerazione genera(Utente u, UUID modelloId, YearMonth mese) {
        ModelloSettimanale m = modello(u, modelloId);
        int creati = 0, scoperti = 0, presenti = 0, chiusi = 0;
        List<String> avvisi = new ArrayList<>();
        var nomi = nomiRighe(m);
        var sessi = persone.sessi(nomi.keySet());
        var chiuso = chiusure.diImpianto(m.getImpiantoId(), mese.atDay(1), mese.atEndOfMonth());
        Set<UUID> disattivate = new java.util.HashSet<>();
        for (LocalDate g = mese.atDay(1); !g.isAfter(mese.atEndOfMonth()); g = g.plusDays(1)) {
            if (!m.validoIl(g)) continue;
            int dow = g.getDayOfWeek().getValue();
            for (ModelloRiga r : m.getRighe()) {
                if (r.getGiornoSettimana() != dow) continue;
                Postazione p = r.getPostazione();
                if (!p.isAttiva()) {
                    if (disattivate.add(p.getId())) avvisi.add(p.getNome() + " è disattivata: le sue righe del modello sono state saltate.");
                    continue;
                }
                LocalTime[] orario = aperto(r.getInizio(), r.getFine(), chiuso.getOrDefault(g, List.of()));
                if (orario == null) { chiusi++; continue; }
                LocalTime inizio = orario[0], fine = orario[1];
                if (!turni.sullaPostazione(p.getId(), g, inizio, fine, TurnoRepository.NESSUNO).isEmpty()) { presenti++; continue; }
                UUID persona = r.getPersonaId();
                if (persona != null && !p.ammette(sessi.get(persona))) {
                    avvisi.add(g + " · " + p.getNome() + ": " + nomi.getOrDefault(persona, "?") + " non può lavorare qui ("
                        + riservata(p) + "), il turno resta scoperto.");
                    persona = null;
                }
                if (persona != null && turni.sovrapposto(persona, g, inizio, fine, TurnoRepository.NESSUNO)) {
                    avvisi.add(g + " · " + p.getNome() + ": " + nomi.getOrDefault(persona, "?")
                        + " ha già un turno in quell'orario, il turno resta scoperto.");
                    persona = null;
                }
                turni.saveAndFlush(new Turno(p, g, inizio, fine, persona, null, r.getId()));
                if (persona == null) scoperti++; else creati++;
            }
        }
        return new EsitoGenerazione(creati, scoperti, presenti, chiusi, avvisi);
    }

    /**
     * L'orario del turno tolte le chiusure del giorno: null se l'impianto è chiuso per tutto il turno. Una chiusura
     * a cavallo dell'inizio lo posticipa, una a cavallo della fine lo anticipa; una tutta dentro il turno non lo
     * cambia (il coordinatore lo sistemerà a mano).
     */
    static LocalTime[] aperto(LocalTime inizio, LocalTime fine, List<Chiusure.Fascia> chiuso) {
        for (Chiusure.Fascia f : chiuso) {
            if (!f.inizio().isBefore(fine) || !f.fine().isAfter(inizio)) continue;
            if (!f.inizio().isAfter(inizio) && !f.fine().isBefore(fine)) return null;
            if (!f.inizio().isAfter(inizio)) inizio = f.fine();
            else if (!f.fine().isBefore(fine)) fine = f.inizio();
        }
        return fine.isAfter(inizio) ? new LocalTime[] {inizio, fine} : null;
    }

    // ── Controlli ───────────────────────────────────────────────────────────

    static void controllaIntervallo(LocalDate da, LocalDate a) {
        if (a.isBefore(da)) throw new IllegalArgumentException("La data finale è prima di quella iniziale.");
        if (ChronoUnit.DAYS.between(da, a) > GIORNI_MASSIMI)
            throw new IllegalArgumentException("Intervallo troppo lungo: al massimo " + GIORNI_MASSIMI + " giorni.");
    }

    private void controllaPersona(UUID personaId, UUID impiantoId) {
        if (personaId != null && !persone.haPermesso(personaId, Permesso.TURNI, impiantoId))
            throw new IllegalArgumentException("Questa persona non fa parte dello staff dell'impianto.");
    }

    /**
     * Le regole di un turno: una persona dello staff, che può lavorare su quella postazione (sesso), e una sola
     * persona alla volta sulla postazione. Due turni accavallati della stessa persona sono solo un avviso.
     */
    private void controlla(Postazione p, TurnoInput in, UUID escluso) {
        if (in.data() == null) throw new IllegalArgumentException("Indica il giorno del turno.");
        if (in.inizio() == null || in.fine() == null || !in.fine().isAfter(in.inizio())) throw new IllegalArgumentException("La fine del turno deve essere dopo l'inizio.");
        controllaPersona(in.personaId(), p.getMansione().getImpiantoId());
        controllaSesso(p, in.personaId());
        var occupata = turni.sullaPostazione(p.getId(), in.data(), in.inizio(), in.fine(), escluso);
        if (!occupata.isEmpty()) {
            Turno o = occupata.getFirst();
            String chi = o.getPersonaId() == null ? "un turno scoperto" : persone.nomiCompleti(List.of(o.getPersonaId())).getOrDefault(o.getPersonaId(), "un'altra persona");
            throw new Conflitto("Su " + p.getNome() + " c'è già " + chi + " dalle " + o.getInizio() + " alle " + o.getFine()
                + ": una postazione è per una persona alla volta. Per una persona in più aggiungi una postazione.");
        }
        if (in.personaId() != null && !Boolean.TRUE.equals(in.forza())) {
            var stesso = turni.diPersona(in.personaId(), in.data(), in.data()).stream()
                .filter(x -> x.pianificato() && !x.getId().equals(escluso) && x.getInizio().isBefore(in.fine()) && x.getFine().isAfter(in.inizio()))
                .findFirst();
            if (stesso.isPresent()) {
                Turno o = stesso.get();
                String nome = persone.nomiCompleti(List.of(in.personaId())).getOrDefault(in.personaId(), "La persona");
                throw new Avviso(nome + " ha già un turno dalle " + o.getInizio() + " alle " + o.getFine() + " (" + o.getPostazione().getNome()
                    + "): i due turni si accavallano.");
            }
        }
    }

    private void controllaSesso(Postazione p, UUID personaId) {
        if (personaId == null || p.getSoloSesso() == null) return;
        if (!p.ammette(persone.sessi(List.of(personaId)).get(personaId)))
            throw new IllegalArgumentException(p.getNome() + " è " + riservata(p) + ".");
    }

    static String riservata(Postazione p) {
        return "F".equals(p.getSoloSesso()) ? "riservata allo staff femminile" : "riservata allo staff maschile";
    }

    /** Nella settimana tipo valgono le stesse regole: sesso, e una riga alla volta per postazione e orario. */
    private void controllaRiga(ModelloSettimanale m, Postazione p, RigaInput in, UUID esclusa) {
        if (in.inizio() == null || in.fine() == null || !in.fine().isAfter(in.inizio())) throw new IllegalArgumentException("La fine del turno deve essere dopo l'inizio.");
        controllaPersona(in.personaId(), m.getImpiantoId());
        controllaSesso(p, in.personaId());
        boolean occupata = m.getRighe().stream().anyMatch(r -> !r.getId().equals(esclusa) && r.getPostazione().getId().equals(p.getId())
            && r.getGiornoSettimana() == in.giornoSettimana() && r.getInizio().isBefore(in.fine()) && r.getFine().isAfter(in.inizio()));
        if (occupata) throw new Conflitto("In quel giorno " + p.getNome() + " ha già un turno che si accavalla: per una persona in più aggiungi una postazione.");
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
        permessi.richiedi(u, Permesso.PIANIFICAZIONE, m.getImpiantoId());
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
