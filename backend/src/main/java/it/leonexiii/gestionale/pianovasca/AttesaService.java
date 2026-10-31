package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.notifiche.Posta;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista d'attesa dei turni dei corsi pieni (ADR 0015): chi aspetta, in ordine di arrivo; quando un turno ha un posto
 * libero (subito o da una data, perché qualcuno si ritira o cambia turno) la segreteria avvisa il primo e lo iscrive.
 */
@Service
class AttesaService {

    /**
     * Una voce della lista: l'ultimo avviso (email o a voce) con il giorno entro cui rispondere; senzaRispostaIl se il posto
     * è già stato proposto al prossimo; confermataIl se ha detto che aspetta ancora.
     */
    record VoceAttesa(UUID id, UUID personaId, String nome, String cognome, LocalDate dataNascita, Integer eta, Instant aggiuntaIl,
                      String note, Instant avvisataIl, boolean avvisoAVoce, LocalDate rispostaEntro, Instant senzaRispostaIl,
                      Instant confermaChiestaIl, Instant confermataIl, int posizione, List<String> contatti) {}

    /** Un turno del corso con i posti: occupati oggi, liberi (negativi se in soprannumero), da quando si libera un posto. */
    record TurnoPosti(UUID lezioneId, UUID corsoId, String corso, String tipo, String colore, Integer etaDa, Integer etaA, int giornoSettimana, LocalTime inizio,
                      LocalTime fine, Integer posti, int occupati, Integer liberi, LocalDate liberoDal, int inAttesa, List<VoceAttesa> attesa) {}

    /** La pagina: i turni con chi aspetta, i giorni per rispondere a un avviso, le ultime cose fatte. */
    record PaginaAttesa(LocalDate oggi, String societa, int giorniRisposta, List<TurnoPosti> turni, List<EventoAttesa> storico) {}

    /** Una riga dello storico: a chi, in quale turno, cosa e chi l'ha fatto. */
    record EventoAttesa(Instant quando, String tipo, UUID personaId, String nome, String cognome, String corso, int giornoSettimana,
                        LocalTime inizio, String da) {}

    /** Domanda a più persone in attesa se aspettano ancora: una sola email per persona. */
    record ChiediConferma(List<UUID> ids, String oggetto, String testo) {}

    /** Quante email sono partite e chi va chiamato (senza indirizzo o con un errore). */
    record ConfermeChieste(int inviate, int nonInviate, List<String> daChiamare) {}

    record NuovaAttesa(UUID personaId, IscrizioniService.NuovaPersona nuovaPersona, String note) {}

    record IscriviDaAttesa(LocalDate dal, Boolean forza) {}

    record Avviso(String oggetto, String testo) {}

    private static final int STORICO = 30;

    /** Per la scheda del cliente (vedi {@link Frequenze#attesaDi}). */
    record AttesaDiPersona(UUID id, UUID lezioneId, UUID corsoId, String corso, int giornoSettimana, LocalTime inizio, LocalTime fine,
                           Instant aggiuntaIl, int posizione, Integer liberi) {}

    private final AttesaRepository attese;
    private final AttesaEventoRepository eventi;
    private final LezioneRepository lezioni;
    private final IscrizioneRepository iscrizioni;
    private final IscrizioniService iscrizioniService;
    private final Persone persone;
    private final Posta posta;
    private final AccessoPiano accesso;
    private final Clock orologio;
    private final it.leonexiii.gestionale.societa.Societa societa;

    AttesaService(AttesaRepository attese, AttesaEventoRepository eventi, LezioneRepository lezioni, IscrizioneRepository iscrizioni, IscrizioniService iscrizioniService,
                  Persone persone, Posta posta, AccessoPiano accesso, Clock orologio,
                  it.leonexiii.gestionale.societa.Societa societa) {
        this.attese = attese;
        this.eventi = eventi;
        this.lezioni = lezioni;
        this.iscrizioni = iscrizioni;
        this.iscrizioniService = iscrizioniService;
        this.persone = persone;
        this.posta = posta;
        this.accesso = accesso;
        this.orologio = orologio;
        this.societa = societa;
    }

    /** Tutti i turni con qualcuno in attesa, dove l'utente gestisce le iscrizioni; prima quelli con un posto libero. */
    @Transactional(readOnly = true)
    PaginaAttesa pagina(Utente u) {
        Set<UUID> impianti = accesso.impiantiConIscrizioni(u);
        List<Lezione> turni = lezioni.findAllById(attese.turniConAttesa()).stream()
            .filter(l -> impianti.contains(l.getCorso().getImpiantoId())).toList();
        List<TurnoPosti> out = posti(turni, true).stream()
            .sorted(Comparator.comparing((TurnoPosti t) -> t.liberoDal() == null ? LocalDate.MAX : t.liberoDal())
                .thenComparing(TurnoPosti::corso).thenComparingInt(TurnoPosti::giornoSettimana).thenComparing(TurnoPosti::inizio))
            .toList();
        return new PaginaAttesa(oggi(), societa.nome(), giorniRisposta(), out, storico(impianti));
    }

    /** Gli ultimi eventi delle liste d'attesa degli impianti dell'utente. */
    private List<EventoAttesa> storico(Set<UUID> impianti) {
        List<AttesaEvento> ultimi = eventi.ultimi(org.springframework.data.domain.Limit.of(STORICO * 3));
        if (ultimi.isEmpty()) return List.of();
        Map<UUID, Attesa> voci = attese.findAllById(ultimi.stream().map(AttesaEvento::getAttesaId).distinct().toList()).stream()
            .collect(Collectors.toMap(Attesa::getId, Function.identity()));
        Map<UUID, Lezione> turni = lezioni.findAllById(voci.values().stream().map(Attesa::getLezioneId).distinct().toList()).stream()
            .collect(Collectors.toMap(Lezione::getId, Function.identity()));
        List<AttesaEvento> visibili = ultimi.stream().filter(e -> {
            Attesa a = voci.get(e.getAttesaId());
            Lezione l = a == null ? null : turni.get(a.getLezioneId());
            return l != null && impianti.contains(l.getCorso().getImpiantoId());
        }).limit(STORICO).toList();
        Set<UUID> pid = new java.util.HashSet<>();
        visibili.forEach(e -> { pid.add(voci.get(e.getAttesaId()).getPersonaId()); if (e.getDa() != null) pid.add(e.getDa()); });
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(pid);
        return visibili.stream().map(e -> {
            Attesa a = voci.get(e.getAttesaId());
            Lezione l = turni.get(a.getLezioneId());
            Persone.PersonaScheda p = schede.get(a.getPersonaId());
            Persone.PersonaScheda da = e.getDa() == null ? null : schede.get(e.getDa());
            return new EventoAttesa(e.getQuando(), e.getTipo().name(), a.getPersonaId(), p == null ? "" : p.nome(), p == null ? "" : p.cognome(),
                l.getCorso().getNome(), l.getGiornoSettimana(), l.getInizio(), da == null ? null : da.nome() + " " + da.cognome());
        }).toList();
    }

    /** I turni (ancora validi) di un corso, con posti e attesa: per iscrivere o cambiare turno dalla scheda. */
    @Transactional(readOnly = true)
    List<TurnoPosti> turniDelCorso(Utente u, UUID corsoId) {
        LocalDate oggi = oggi();
        List<Lezione> turni = lezioni.deiCorsi(List.of(corsoId)).stream().filter(l -> l.getValidaAl() == null || !l.getValidaAl().isBefore(oggi)).toList();
        if (!turni.isEmpty() && !accesso.gestisceIscrizioni(u, turni.getFirst().getCorso().getImpiantoId()))
            throw new AccessDeniedException("Per iscrivere serve il permesso «Iscrizioni».");
        return posti(turni, false).stream().sorted(Comparator.comparingInt(TurnoPosti::giornoSettimana).thenComparing(TurnoPosti::inizio)).toList();
    }

    @Transactional
    VoceAttesa aggiungi(Utente u, UUID lezioneId, NuovaAttesa in) {
        Lezione l = lezioni.completa(lezioneId).orElseThrow(() -> new NonTrovato("Turno del corso non trovato."));
        richiedi(u, l);
        LocalDate oggi = oggi();
        if (l.getValidaAl() != null && l.getValidaAl().isBefore(oggi)) throw new IllegalArgumentException("Il turno è finito.");
        Persone.PersonaScheda p;
        if (in.personaId() != null) {
            p = persone.schede(List.of(in.personaId())).get(in.personaId());
            if (p == null) throw new NonTrovato("Persona non trovata.");
        } else if (in.nuovaPersona() != null) {
            p = persone.crea(in.nuovaPersona().nome(), in.nuovaPersona().cognome(), in.nuovaPersona().dataNascita());
        } else throw new IllegalArgumentException("Scegli chi mettere in lista d'attesa.");
        boolean iscritta = iscrizioni.findByLezioneIdOrderByDal(lezioneId).stream()
            .anyMatch(i -> i.getPersonaId().equals(p.id()) && (i.getAl() == null || !i.getAl().isBefore(oggi)));
        if (iscritta) throw new Conflitto(p.nome() + " " + p.cognome() + " è già iscritto a questo turno.");
        if (attese.inAttesa(lezioneId, p.id()).isPresent()) throw new Conflitto(p.nome() + " " + p.cognome() + " è già in lista d'attesa per questo turno.");
        Attesa a = attese.saveAndFlush(new Attesa(lezioneId, p.id(), in.note(), u.personaId(), orologio.instant()));
        evento(a, AttesaEvento.Tipo.AGGIUNTA, u);
        return voci(attese.inAttesa(List.of(lezioneId))).get(lezioneId).stream().filter(v -> v.id().equals(a.getId())).findFirst().orElseThrow();
    }

    /** La lista d'attesa di un turno, in ordine. */
    @Transactional(readOnly = true)
    List<VoceAttesa> diTurno(Utente u, UUID lezioneId) {
        richiedi(u, lezioni.completa(lezioneId).orElseThrow(() -> new NonTrovato("Turno del corso non trovato.")));
        return voci(attese.inAttesa(List.of(lezioneId))).getOrDefault(lezioneId, List.of());
    }

    /** Iscrive dalla lista d'attesa (rispettando i posti, salvo conferma del soprannumero): la voce si chiude. */
    @Transactional
    IscrizioniService.IscrittoDto iscrivi(Utente u, UUID attesaId, IscriviDaAttesa in) {
        Attesa a = aperta(attesaId);
        return iscrizioniService.iscrivi(u, a.getLezioneId(), new IscrizioniService.NuovaIscrizione(a.getPersonaId(), null, in.dal(), in.forza()));
    }

    @Transactional
    void rinuncia(Utente u, UUID attesaId) {
        Attesa a = aperta(attesaId);
        richiedi(u, lezioni.completa(a.getLezioneId()).orElseThrow());
        a.chiudi(Attesa.Stato.RINUNCIA, orologio.instant());
        evento(a, AttesaEvento.Tipo.RINUNCIA, u);
    }

    /** Scrive alla famiglia (ai tutori che ricevono le comunicazioni, altrimenti alla persona) che c'è un posto. */
    @Transactional
    Posta.Esito avvisa(Utente u, UUID attesaId, Avviso in) {
        Attesa a = aperta(attesaId);
        richiedi(u, lezioni.completa(a.getLezioneId()).orElseThrow());
        if (in.oggetto() == null || in.oggetto().isBlank() || in.testo() == null || in.testo().isBlank())
            throw new IllegalArgumentException("Scrivi oggetto e testo.");
        List<String> aChi = persone.destinatari(List.of(a.getPersonaId())).getOrDefault(a.getPersonaId(), List.of());
        Posta.Esito esito = posta.invia(a.getPersonaId(), aChi.isEmpty() ? null : String.join(", ", aChi), in.oggetto().strip(), in.testo().strip(), u.personaId());
        if (esito.stato() == Posta.Stato.INVIATA || esito.stato() == Posta.Stato.NON_INVIATA) {
            a.avvisata(orologio.instant(), false, oggi().plusDays(giorniRisposta()));
            evento(a, AttesaEvento.Tipo.AVVISATA, u);
        }
        return esito;
    }

    /** Avvisata per telefono o di persona: come l'email, ha i giorni della società per rispondere. */
    @Transactional
    void avvisataAVoce(Utente u, UUID attesaId) {
        Attesa a = aperta(attesaId);
        richiedi(u, lezioni.completa(a.getLezioneId()).orElseThrow());
        a.avvisata(orologio.instant(), true, oggi().plusDays(giorniRisposta()));
        evento(a, AttesaEvento.Tipo.AVVISATA_A_VOCE, u);
    }

    /** Avvisata, non ha risposto: resta in lista (con la sua posizione), ma il posto si propone al prossimo. */
    @Transactional
    void senzaRisposta(Utente u, UUID attesaId) {
        Attesa a = aperta(attesaId);
        richiedi(u, lezioni.completa(a.getLezioneId()).orElseThrow());
        if (a.getAvvisataIl() == null) throw new IllegalArgumentException("Non è ancora stata avvisata.");
        a.senzaRisposta(orologio.instant());
        evento(a, AttesaEvento.Tipo.SENZA_RISPOSTA, u);
    }

    /** Ha detto che aspetta ancora. */
    @Transactional
    void confermata(Utente u, UUID attesaId) {
        Attesa a = aperta(attesaId);
        richiedi(u, lezioni.completa(a.getLezioneId()).orElseThrow());
        a.confermata(orologio.instant());
        evento(a, AttesaEvento.Tipo.CONFERMATA, u);
    }

    /** Chiede alle persone scelte se aspettano ancora: una sola email per persona, anche se è in più liste. */
    @Transactional
    ConfermeChieste chiediConferma(Utente u, ChiediConferma in) {
        if (in.ids() == null || in.ids().isEmpty()) throw new IllegalArgumentException("Scegli a chi chiedere.");
        if (in.oggetto() == null || in.oggetto().isBlank() || in.testo() == null || in.testo().isBlank())
            throw new IllegalArgumentException("Scrivi oggetto e testo.");
        List<Attesa> scelte = in.ids().stream().distinct().map(this::aperta).toList();
        Map<UUID, Lezione> turni = lezioni.findAllById(scelte.stream().map(Attesa::getLezioneId).distinct().toList()).stream()
            .collect(Collectors.toMap(Lezione::getId, Function.identity()));
        scelte.forEach(a -> richiedi(u, turni.get(a.getLezioneId())));
        Map<UUID, List<Attesa>> perPersona = scelte.stream().collect(Collectors.groupingBy(Attesa::getPersonaId, java.util.LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<String>> contatti = persone.destinatari(perPersona.keySet());
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(perPersona.keySet());
        int inviate = 0, non = 0;
        List<String> daChiamare = new ArrayList<>();
        for (var e : perPersona.entrySet()) {
            List<String> aChi = contatti.getOrDefault(e.getKey(), List.of());
            Persone.PersonaScheda p = schede.get(e.getKey());
            String testo = in.testo().strip().replace("{nome}", p.nome());
            Posta.Esito esito = posta.invia(e.getKey(), aChi.isEmpty() ? null : String.join(", ", aChi), in.oggetto().strip().replace("{nome}", p.nome()), testo, u.personaId());
            if (esito.stato() == Posta.Stato.INVIATA) inviate++;
            else {
                non++;
                daChiamare.add(p.nome() + " " + p.cognome());
            }
            if (esito.stato() == Posta.Stato.INVIATA || esito.stato() == Posta.Stato.NON_INVIATA)
                for (Attesa a : e.getValue()) {
                    a.confermaChiesta(orologio.instant());
                    evento(a, AttesaEvento.Tipo.CONFERMA_CHIESTA, u);
                }
        }
        return new ConfermeChieste(inviate, non, daChiamare);
    }

    private void evento(Attesa a, AttesaEvento.Tipo tipo, Utente u) {
        eventi.save(new AttesaEvento(a.getId(), tipo, orologio.instant(), u.personaId()));
    }

    private int giorniRisposta() {
        Integer g = societa.dati().giorniRispostaAttesa();
        return g == null ? 3 : g;
    }

    /** Le liste d'attesa in cui è la persona, con la posizione. */
    @Transactional(readOnly = true)
    List<AttesaDiPersona> attesaDi(UUID personaId) {
        List<Attesa> sue = attese.inAttesaDi(personaId);
        if (sue.isEmpty()) return List.of();
        Map<UUID, List<Attesa>> perTurno = attese.inAttesa(sue.stream().map(Attesa::getLezioneId).toList()).stream()
            .collect(Collectors.groupingBy(Attesa::getLezioneId));
        Map<UUID, TurnoPosti> posti = posti(lezioni.findAllById(perTurno.keySet()), false).stream()
            .collect(Collectors.toMap(TurnoPosti::lezioneId, Function.identity()));
        return sue.stream().filter(a -> posti.containsKey(a.getLezioneId())).map(a -> {
            TurnoPosti t = posti.get(a.getLezioneId());
            int pos = perTurno.get(a.getLezioneId()).indexOf(a) + 1;
            return new AttesaDiPersona(a.getId(), t.lezioneId(), t.corsoId(), t.corso(), t.giornoSettimana(), t.inizio(), t.fine(), a.getAggiuntaIl(), pos, t.liberi());
        }).toList();
    }

    // ── Posti ─────────────────────────────────────────────────────────────

    private List<TurnoPosti> posti(Collection<Lezione> turni, boolean conAttesa) {
        if (turni.isEmpty()) return List.of();
        LocalDate oggi = oggi();
        List<UUID> ids = turni.stream().map(Lezione::getId).toList();
        Map<UUID, List<Iscrizione>> iscritti = iscrizioni.nelPeriodo(ids, oggi, LocalDate.of(9999, 12, 31)).stream()
            .collect(Collectors.groupingBy(Iscrizione::getLezioneId));
        Map<UUID, List<Attesa>> inAttesa = attese.inAttesa(ids).stream().collect(Collectors.groupingBy(Attesa::getLezioneId));
        Map<UUID, List<VoceAttesa>> voci = conAttesa ? voci(inAttesa.values().stream().flatMap(List::stream).toList()) : Map.of();
        List<TurnoPosti> out = new ArrayList<>();
        for (Lezione l : turni) {
            List<Iscrizione> sue = iscritti.getOrDefault(l.getId(), List.of());
            int occupati = (int) sue.stream().filter(i -> i.attivaIl(oggi)).count();
            Integer posti = l.getPosti();
            Corso c = l.getCorso();
            out.add(new TurnoPosti(l.getId(), c.getId(), c.getNome(), c.getTipo() == null ? null : c.getTipo().name(), c.getColore(), c.getEtaDa(), l.getCorso().getEtaA(),
                l.getGiornoSettimana(), l.getInizio(), l.getFine(), posti, occupati, posti == null ? null : posti - occupati, liberoDal(posti, sue, oggi),
                inAttesa.getOrDefault(l.getId(), List.of()).size(), voci.getOrDefault(l.getId(), List.of())));
        }
        return out;
    }

    /** Il primo giorno, da oggi, in cui il turno ha meno iscritti dei posti (i cambi avvengono quando un'iscrizione finisce). */
    static LocalDate liberoDal(Integer posti, List<Iscrizione> iscritti, LocalDate oggi) {
        if (posti == null) return oggi;
        Set<LocalDate> date = new TreeSet<>();
        date.add(oggi);
        iscritti.stream().filter(i -> i.getAl() != null && !i.getAl().isBefore(oggi)).forEach(i -> date.add(i.getAl().plusDays(1)));
        for (LocalDate d : date) if (iscritti.stream().filter(i -> i.attivaIl(d)).count() < posti) return d;
        return null;
    }

    /** Le voci per turno, ciascuna con la sua posizione. */
    private Map<UUID, List<VoceAttesa>> voci(List<Attesa> lista) {
        if (lista.isEmpty()) return Map.of();
        List<UUID> pid = lista.stream().map(Attesa::getPersonaId).distinct().toList();
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(pid);
        Map<UUID, List<String>> contatti = persone.destinatari(pid);
        LocalDate oggi = oggi();
        Map<UUID, List<Attesa>> perTurno = lista.stream().collect(Collectors.groupingBy(Attesa::getLezioneId));
        Map<UUID, List<VoceAttesa>> out = new java.util.HashMap<>();
        for (List<Attesa> turno : perTurno.values()) {
            List<VoceAttesa> voci = new ArrayList<>();
            List<Attesa> ordinate = turno.stream().sorted(Comparator.comparing(Attesa::getAggiuntaIl).thenComparing(Attesa::getId)).toList();
            for (int k = 0; k < ordinate.size(); k++) {
                Attesa a = ordinate.get(k);
                Persone.PersonaScheda p = schede.get(a.getPersonaId());
                Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), oggi).getYears();
                voci.add(new VoceAttesa(a.getId(), p.id(), p.nome(), p.cognome(), p.dataNascita(), eta, a.getAggiuntaIl(), a.getNote(), a.getAvvisataIl(),
                    a.isAvvisoAVoce(), a.getRispostaEntro(), a.getSenzaRispostaIl(), a.getConfermaChiestaIl(), a.getConfermataIl(), k + 1, contatti.getOrDefault(p.id(), List.of())));
            }
            out.put(turno.getFirst().getLezioneId(), voci);
        }
        return out;
    }

    private Attesa aperta(UUID id) {
        Attesa a = attese.findById(id).orElseThrow(() -> new NonTrovato("Voce della lista d'attesa non trovata."));
        if (!a.inAttesa()) throw new Conflitto("Non è più in lista d'attesa.");
        return a;
    }

    private void richiedi(Utente u, Lezione l) {
        if (!accesso.gestisceIscrizioni(u, l.getCorso().getImpiantoId())) throw new AccessDeniedException("Per la lista d'attesa serve il permesso «Iscrizioni».");
    }

    private LocalDate oggi() {
        return LocalDate.now(orologio);
    }
}
