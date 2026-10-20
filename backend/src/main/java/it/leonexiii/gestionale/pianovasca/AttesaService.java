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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista d'attesa dei turni dei corsi pieni (ADR 0015): chi aspetta, in ordine di arrivo; quando un turno ha un posto
 * libero (subito o da una data, perché qualcuno si ritira o cambia turno) la segreteria avvisa il primo e lo iscrive.
 */
@Service
class AttesaService {

    record VoceAttesa(UUID id, UUID personaId, String nome, String cognome, LocalDate dataNascita, Integer eta, Instant aggiuntaIl,
                      String note, Instant avvisataIl, int posizione, List<String> contatti) {}

    /** Un turno del corso con i posti: occupati oggi, liberi (negativi se in soprannumero), da quando si libera un posto. */
    record TurnoPosti(UUID lezioneId, UUID corsoId, String corso, Integer etaDa, Integer etaA, int giornoSettimana, LocalTime inizio,
                      LocalTime fine, Integer posti, int occupati, Integer liberi, LocalDate liberoDal, int inAttesa, List<VoceAttesa> attesa) {}

    record PaginaAttesa(LocalDate oggi, String societa, List<TurnoPosti> turni) {}

    record NuovaAttesa(UUID personaId, IscrizioniService.NuovaPersona nuovaPersona, String note) {}

    record IscriviDaAttesa(LocalDate dal, Boolean forza) {}

    record Avviso(String oggetto, String testo) {}

    /** Per la scheda del cliente (vedi {@link Frequenze#attesaDi}). */
    record AttesaDiPersona(UUID id, UUID lezioneId, UUID corsoId, String corso, int giornoSettimana, LocalTime inizio, LocalTime fine,
                           Instant aggiuntaIl, int posizione, Integer liberi) {}

    private final AttesaRepository attese;
    private final LezioneRepository lezioni;
    private final IscrizioneRepository iscrizioni;
    private final IscrizioniService iscrizioniService;
    private final Persone persone;
    private final Posta posta;
    private final AccessoPiano accesso;
    private final Clock orologio;
    private final String societa;

    AttesaService(AttesaRepository attese, LezioneRepository lezioni, IscrizioneRepository iscrizioni, IscrizioniService iscrizioniService,
                  Persone persone, Posta posta, AccessoPiano accesso, Clock orologio,
                  @Value("${gestionale.societa.nome:Leone XIII Sport}") String societa) {
        this.attese = attese;
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
        return new PaginaAttesa(oggi(), societa, out);
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
        if (esito.stato() == Posta.Stato.INVIATA || esito.stato() == Posta.Stato.NON_INVIATA) a.avvisata(orologio.instant());
        return esito;
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
            out.add(new TurnoPosti(l.getId(), l.getCorso().getId(), l.getCorso().getNome(), l.getCorso().getEtaDa(), l.getCorso().getEtaA(),
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
                    k + 1, contatti.getOrDefault(p.id(), List.of())));
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
