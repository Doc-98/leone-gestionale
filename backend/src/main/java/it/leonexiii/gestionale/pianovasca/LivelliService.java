package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.societa.Societa;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Livelli dei corsi (ADR 0026): gli esiti di fine stagione («passa al livello successivo» o «continua nello stesso»)
 * e il passaggio durante l'anno. Li danno gli istruttori per i loro turni e chi ha il permesso «Livelli» per tutti.
 */
@Service
class LivelliService {

    /** Un turno di un corso che ha un livello successivo, con quanti esiti sono stati dati. */
    record TurnoLivelli(UUID lezioneId, UUID corsoId, String corso, String colore, int giornoSettimana, LocalTime inizio, LocalTime fine,
                        UUID successivoId, String successivo, String coloreSuccessivo, int iscritti, int passano, int continuano, boolean mio) {}

    /** La pagina: la stagione degli esiti e i turni che l'utente può valutare (i suoi prima). */
    record PaginaLivelli(UUID stagioneId, String stagione, LocalDate riferimento, boolean tutti, List<TurnoLivelli> turni) {}

    /** Un passaggio durante l'anno ancora aperto: in attesa di un posto nel livello nuovo (la posizione migliore tra i turni). */
    record PassaggioAperto(UUID id, Instant decisoIl, Integer posizione) {}

    record Bambino(UUID iscrizioneId, UUID personaId, String nome, String cognome, Integer eta, EsitoLivello.Esito esito, PassaggioAperto passaggio) {}

    record DettaglioTurno(UUID stagioneId, String stagione, TurnoLivelli turno, List<Bambino> bambini) {}

    /** {@code esito} null = togliere l'esito dato. */
    record EsitoInput(UUID personaId, EsitoLivello.Esito esito) {}

    record Esiti(List<EsitoInput> esiti) {}

    record NuovoPassaggio(UUID iscrizioneId) {}

    private final LezioneRepository lezioni;
    private final CorsoRepository corsi;
    private final IscrizioneRepository iscrizioni;
    private final EsitoLivelloRepository esiti;
    private final PassaggioLivelloRepository passaggi;
    private final AttesaRepository attese;
    private final AttesaEventoRepository eventi;
    private final Persone persone;
    private final Autorizzazioni autorizzazioni;
    private final AccessoPiano accesso;
    private final Societa societa;
    private final Clock orologio;

    LivelliService(LezioneRepository lezioni, CorsoRepository corsi, IscrizioneRepository iscrizioni, EsitoLivelloRepository esiti,
                   PassaggioLivelloRepository passaggi, AttesaRepository attese, AttesaEventoRepository eventi, Persone persone,
                   Autorizzazioni autorizzazioni, AccessoPiano accesso, Societa societa, Clock orologio) {
        this.lezioni = lezioni;
        this.corsi = corsi;
        this.iscrizioni = iscrizioni;
        this.esiti = esiti;
        this.passaggi = passaggi;
        this.attese = attese;
        this.eventi = eventi;
        this.persone = persone;
        this.autorizzazioni = autorizzazioni;
        this.accesso = accesso;
        this.societa = societa;
        this.orologio = orologio;
    }

    // ── Pagina ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    PaginaLivelli pagina(Utente u) {
        Societa.Stagione s = stagione();
        LocalDate rif = riferimento(s);
        List<Lezione> turni = turniVisibili(u, rif);
        Map<UUID, List<Iscrizione>> iscritti = iscrittiIl(turni, rif);
        Map<UUID, Corso> successivi = successivi(turni);
        Map<String, EsitoLivello.Esito> dati = esitiDi(s, turni);
        List<TurnoLivelli> out = turni.stream()
            .map(l -> turno(u, l, successivi.get(l.getCorso().getLivelloSuccessivoId()), iscritti.getOrDefault(l.getId(), List.of()), dati))
            .sorted(Comparator.comparing((TurnoLivelli t) -> !t.mio()).thenComparing(TurnoLivelli::corso)
                .thenComparingInt(TurnoLivelli::giornoSettimana).thenComparing(TurnoLivelli::inizio))
            .toList();
        return new PaginaLivelli(s.id(), s.nome(), rif, !ambitoLivelli(u).isEmpty(), out);
    }

    @Transactional(readOnly = true)
    DettaglioTurno turno(Utente u, UUID lezioneId) {
        Societa.Stagione s = stagione();
        LocalDate rif = riferimento(s);
        Lezione l = valutabile(u, lezioneId);
        List<Iscrizione> sue = iscrittiIl(List.of(l), rif).getOrDefault(l.getId(), List.of());
        Corso dopo = corsi.findById(l.getCorso().getLivelloSuccessivoId()).orElseThrow();
        Map<String, EsitoLivello.Esito> dati = esitiDi(s, List.of(l));
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(sue.stream().map(Iscrizione::getPersonaId).toList());
        Map<UUID, PassaggioLivello> aperti = passaggi.aperti(sue.stream().map(Iscrizione::getId).toList()).stream()
            .collect(Collectors.toMap(PassaggioLivello::getIscrizioneId, Function.identity()));
        List<Bambino> bambini = sue.stream().map(i -> {
            Persone.PersonaScheda p = schede.get(i.getPersonaId());
            Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), rif).getYears();
            PassaggioLivello pl = aperti.get(i.getId());
            return new Bambino(i.getId(), p.id(), p.nome(), p.cognome(), eta, dati.get(chiave(p.id(), l.getCorso().getId())),
                pl == null ? null : new PassaggioAperto(pl.getId(), pl.getDecisoIl(), posizione(pl)));
        }).sorted(Comparator.comparing(Bambino::cognome).thenComparing(Bambino::nome)).toList();
        return new DettaglioTurno(s.id(), s.nome(), turno(u, l, dopo, sue, dati), bambini);
    }

    // ── Esiti di fine stagione ────────────────────────────────────────────

    @Transactional
    DettaglioTurno esiti(Utente u, UUID lezioneId, Esiti in) {
        Societa.Stagione s = stagione();
        LocalDate rif = riferimento(s);
        Lezione l = valutabile(u, lezioneId);
        if (in == null || in.esiti() == null || in.esiti().isEmpty()) throw new IllegalArgumentException("Nessun esito da salvare.");
        Set<UUID> iscritti = iscrittiIl(List.of(l), rif).getOrDefault(l.getId(), List.of()).stream().map(Iscrizione::getPersonaId).collect(Collectors.toSet());
        UUID corsoId = l.getCorso().getId();
        Map<UUID, EsitoLivello> gia = esiti.findByStagioneIdAndCorsoIdIn(s.id(), List.of(corsoId)).stream()
            .collect(Collectors.toMap(EsitoLivello::getPersonaId, Function.identity()));
        Instant adesso = orologio.instant();
        for (EsitoInput e : in.esiti()) {
            if (e.personaId() == null || !iscritti.contains(e.personaId())) throw new IllegalArgumentException("Si dà l'esito solo a chi è iscritto al turno.");
            EsitoLivello x = gia.get(e.personaId());
            if (e.esito() == null) {
                if (x != null) esiti.delete(x);
                continue;
            }
            if (x == null) {
                x = new EsitoLivello(s.id(), e.personaId(), corsoId);
                x.decidi(e.esito(), u.personaId(), adesso);
                gia.put(e.personaId(), esiti.save(x));
            } else x.decidi(e.esito(), u.personaId(), adesso);
        }
        esiti.flush();
        return turno(u, lezioneId);
    }

    // ── Passaggio durante l'anno ──────────────────────────────────────────

    /**
     * «Passa adesso al livello successivo»: il bambino resta nel suo turno ed entra nella lista d'attesa di tutti i turni
     * del livello nuovo, davanti a chi aspetta senza essere passato.
     */
    @Transactional
    DettaglioTurno passa(Utente u, NuovoPassaggio in) {
        if (in == null || in.iscrizioneId() == null) throw new IllegalArgumentException("Scegli chi passa di livello.");
        Iscrizione i = iscrizioni.findById(in.iscrizioneId()).orElseThrow(() -> new NonTrovato("Iscrizione non trovata."));
        LocalDate oggi = oggi();
        if (!i.attivaIl(oggi)) throw new Conflitto("L'iscrizione non è in corso.");
        Lezione l = valutabile(u, i.getLezioneId());
        if (!passaggi.aperti(List.of(i.getId())).isEmpty()) throw new Conflitto("Il passaggio è già in corso.");
        UUID dopoId = l.getCorso().getLivelloSuccessivoId();
        List<Lezione> nuovi = lezioni.deiCorsi(List.of(dopoId)).stream()
            .filter(x -> x.getValidaAl() == null || !x.getValidaAl().isBefore(oggi)).toList();
        if (nuovi.isEmpty()) throw new Conflitto("Il livello successivo non ha turni in cui aspettare un posto.");
        Instant adesso = orologio.instant();
        PassaggioLivello p = passaggi.saveAndFlush(new PassaggioLivello(i, l.getCorso().getId(), dopoId, u.personaId(), adesso));
        String nota = "Passa di livello da " + l.getCorso().getNome();
        for (Lezione x : nuovi) {
            Attesa a = attese.inAttesa(x.getId(), i.getPersonaId()).orElse(null);
            if (a == null) {
                a = attese.save(new Attesa(x.getId(), i.getPersonaId(), nota, u.personaId(), adesso));
                eventi.save(new AttesaEvento(a.getId(), AttesaEvento.Tipo.AGGIUNTA, adesso, u.personaId()));
            }
            a.perPassaggio(p.getId());
        }
        attese.flush();
        return turno(u, l.getId());
    }

    /** Annulla un passaggio ancora aperto: esce dalle liste d'attesa del livello nuovo. */
    @Transactional
    DettaglioTurno annulla(Utente u, UUID passaggioId) {
        PassaggioLivello p = passaggi.findById(passaggioId).orElseThrow(() -> new NonTrovato("Passaggio non trovato."));
        if (!p.aperto()) throw new Conflitto("Il passaggio è già chiuso.");
        Iscrizione i = iscrizioni.findById(p.getIscrizioneId()).orElseThrow();
        valutabile(u, i.getLezioneId());
        Instant adesso = orologio.instant();
        p.chiudi(PassaggioLivello.Stato.ANNULLATO, adesso);
        for (Attesa a : attese.delPassaggio(p.getId())) {
            a.chiudi(Attesa.Stato.RINUNCIA, adesso);
            eventi.save(new AttesaEvento(a.getId(), AttesaEvento.Tipo.RINUNCIA, adesso, u.personaId()));
        }
        attese.flush();
        return turno(u, i.getLezioneId());
    }

    // ── Accesso e dati ────────────────────────────────────────────────────

    /** I turni dei corsi con un livello successivo, validi alla data: quelli dove l'utente insegna e, con «Livelli», tutti. */
    private List<Lezione> turniVisibili(Utente u, LocalDate rif) {
        Set<UUID> ambito = ambitoLivelli(u);
        Set<UUID> impianti = accesso.visibili(u);
        if (impianti.isEmpty()) return List.of();
        List<UUID> conSuccessivo = corsi.findByImpiantoIdInOrderByNome(impianti).stream()
            .filter(c -> c.isAttivo() && c.getLivelloSuccessivoId() != null).map(Corso::getId).toList();
        if (conSuccessivo.isEmpty()) return List.of();
        return lezioni.deiCorsi(conSuccessivo).stream()
            .filter(l -> !l.getValidaDal().isAfter(rif) && (l.getValidaAl() == null || !l.getValidaAl().isBefore(rif)))
            .filter(l -> insegna(u, l) || ambito.contains(l.getCorso().getImpiantoId()))
            .toList();
    }

    private Lezione valutabile(Utente u, UUID lezioneId) {
        Lezione l = lezioni.findById(lezioneId).orElseThrow(() -> new NonTrovato("Turno non trovato."));
        if (l.getCorso().getLivelloSuccessivoId() == null) throw new Conflitto("Il corso non ha un livello successivo.");
        if (!insegna(u, l) && !autorizzazioni.puo(u, Permesso.LIVELLI, l.getCorso().getImpiantoId()))
            throw new AccessDeniedException("Gli esiti li danno gli istruttori del turno o chi ha il permesso «Livelli».");
        return l;
    }

    /** Chi tiene il turno (o, se il turno non ne ha, il parco del corso). */
    private static boolean insegna(Utente u, Lezione l) {
        UUID io = u.personaId();
        if (io == null) return false;
        return l.getIstruttori().isEmpty() ? l.getCorso().getIstruttori().contains(io) : l.getIstruttori().contains(io);
    }

    private Set<UUID> ambitoLivelli(Utente u) {
        Autorizzazioni.Ambito a = autorizzazioni.ambito(u, Permesso.LIVELLI);
        if (!a.tutti()) return a.impianti();
        return accesso.visibili(u);
    }

    private TurnoLivelli turno(Utente u, Lezione l, Corso dopo, List<Iscrizione> iscritti, Map<String, EsitoLivello.Esito> dati) {
        Corso c = l.getCorso();
        int passano = 0, continuano = 0;
        for (UUID p : iscritti.stream().map(Iscrizione::getPersonaId).collect(Collectors.toSet())) {
            EsitoLivello.Esito e = dati.get(chiave(p, c.getId()));
            if (e == EsitoLivello.Esito.PASSA) passano++;
            else if (e == EsitoLivello.Esito.CONTINUA) continuano++;
        }
        int quanti = (int) iscritti.stream().map(Iscrizione::getPersonaId).distinct().count();
        return new TurnoLivelli(l.getId(), c.getId(), c.getNome(), c.getColore(), l.getGiornoSettimana(), l.getInizio(), l.getFine(),
            dopo == null ? null : dopo.getId(), dopo == null ? null : dopo.getNome(), dopo == null ? null : dopo.getColore(),
            quanti, passano, continuano, insegna(u, l));
    }

    private Map<UUID, List<Iscrizione>> iscrittiIl(List<Lezione> turni, LocalDate rif) {
        if (turni.isEmpty()) return Map.of();
        return iscrizioni.nelPeriodo(turni.stream().map(Lezione::getId).toList(), rif, rif).stream()
            .collect(Collectors.groupingBy(Iscrizione::getLezioneId));
    }

    private Map<UUID, Corso> successivi(List<Lezione> turni) {
        Set<UUID> ids = new HashSet<>();
        turni.forEach(l -> ids.add(l.getCorso().getLivelloSuccessivoId()));
        return corsi.findAllById(ids).stream().collect(Collectors.toMap(Corso::getId, Function.identity()));
    }

    private Map<String, EsitoLivello.Esito> esitiDi(Societa.Stagione s, List<Lezione> turni) {
        if (turni.isEmpty()) return Map.of();
        Map<String, EsitoLivello.Esito> out = new HashMap<>();
        esiti.findByStagioneIdAndCorsoIdIn(s.id(), turni.stream().map(l -> l.getCorso().getId()).distinct().toList())
            .forEach(e -> out.put(chiave(e.getPersonaId(), e.getCorsoId()), e.getEsito()));
        return out;
    }

    private static String chiave(UUID persona, UUID corso) {
        return persona + "/" + corso;
    }

    /** La posizione migliore tra le liste d'attesa del passaggio (1 = il prossimo a entrare). */
    private Integer posizione(PassaggioLivello p) {
        List<Attesa> sue = attese.delPassaggio(p.getId());
        if (sue.isEmpty()) return null;
        Map<UUID, List<Attesa>> perTurno = attese.inAttesa(sue.stream().map(Attesa::getLezioneId).toList()).stream()
            .collect(Collectors.groupingBy(Attesa::getLezioneId));
        List<Integer> pos = new ArrayList<>();
        for (Attesa a : sue) pos.add(perTurno.getOrDefault(a.getLezioneId(), List.of()).indexOf(a) + 1);
        return pos.stream().filter(x -> x > 0).min(Integer::compare).orElse(null);
    }

    /** Gli esiti valgono per la stagione in corso (o, tra due stagioni, per l'ultima finita). */
    private Societa.Stagione stagione() {
        LocalDate oggi = oggi();
        return societa.stagioneIl(oggi).orElseGet(() -> societa.stagioni().stream()
            .filter(s -> s.al().isBefore(oggi)).max(Comparator.comparing(Societa.Stagione::al))
            .orElseThrow(() -> new Conflitto("Crea prima la stagione in Impostazioni › Dati, stagioni e gruppi.")));
    }

    /** Il giorno in cui si guardano gli iscritti: oggi o, a stagione finita, il suo ultimo giorno. */
    private LocalDate riferimento(Societa.Stagione s) {
        LocalDate oggi = oggi();
        return oggi.isAfter(s.al()) ? s.al() : oggi;
    }

    private LocalDate oggi() {
        return LocalDate.now(orologio);
    }
}
