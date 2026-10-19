package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chi frequenta i turni dei corsi (corso + giorno + orario) e le presenze di ogni data.
 * La lista la vedono l'istruttore del turno, i coordinatori e la segreteria; iscrive la segreteria (o un
 * coordinatore); le presenze le segna l'istruttore (o un coordinatore), solo per le date in cui il turno si tiene.
 */
@Service
class IscrizioniService {

    private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    record IscrittoDto(UUID iscrizioneId, UUID personaId, String nome, String cognome, LocalDate dataNascita, Integer eta,
                       LocalDate dal, LocalDate al, Boolean presente) {}

    record ElencoIscritti(UUID lezioneId, UUID corsoId, String corso, int giornoSettimana, LocalTime inizio, LocalTime fine,
                          LocalDate data, boolean siTiene, Integer posti, List<IscrittoDto> iscritti,
                          boolean puoSegnarePresenze, boolean puoIscrivere) {}

    record NuovaPersona(String nome, String cognome, LocalDate dataNascita) {}

    record NuovaIscrizione(UUID personaId, NuovaPersona nuovaPersona, LocalDate dal, Boolean forza) {}

    record Termine(LocalDate al) {}

    record PresenzaInput(UUID personaId, boolean presente) {}

    record Presenze(LocalDate data, List<PresenzaInput> presenze) {}

    private final LezioneRepository lezioni;
    private final IscrizioneRepository iscrizioni;
    private final PresenzaRepository presenze;
    private final Persone persone;
    private final AccessoPiano accesso;

    IscrizioniService(LezioneRepository lezioni, IscrizioneRepository iscrizioni, PresenzaRepository presenze, Persone persone, AccessoPiano accesso) {
        this.lezioni = lezioni;
        this.iscrizioni = iscrizioni;
        this.presenze = presenze;
        this.persone = persone;
        this.accesso = accesso;
    }

    /** Gli iscritti attivi alla data (oggi se manca), con la presenza segnata in quella data. */
    @Transactional(readOnly = true)
    ElencoIscritti elenco(Utente u, UUID lezioneId, LocalDate data) {
        Lezione l = lezione(lezioneId);
        boolean iscrive = accesso.gestisceIscrizioni(u, impianto(l));
        boolean istruttore = u.personaId().equals(l.istruttoreEffettivo());
        boolean coordinatore = accesso.segnaTutteLePresenze(u, impianto(l));
        if (!iscrive && !istruttore && !coordinatore)
            throw new AccessDeniedException("La lista degli iscritti la vedono l'istruttore del turno e chi gestisce iscrizioni o presenze.");
        LocalDate giorno = data != null ? data : LocalDate.now(FUSO);

        List<Iscrizione> attive = iscrizioni.findByLezioneIdOrderByDal(lezioneId).stream().filter(i -> i.attivaIl(giorno)).toList();
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(attive.stream().map(Iscrizione::getPersonaId).toList());
        Map<UUID, Boolean> segnate = presenze.delGiorno(lezioneId, giorno).stream()
            .collect(Collectors.toMap(p -> p.getChiave().personaId(), Presenza::isPresente));
        List<IscrittoDto> elenco = attive.stream().map(i -> {
                Persone.PersonaScheda p = schede.get(i.getPersonaId());
                Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), giorno).getYears();
                return new IscrittoDto(i.getId(), p.id(), p.nome(), p.cognome(), p.dataNascita(), eta, i.getDal(), i.getAl(), segnate.get(p.id()));
            })
            .sorted(Comparator.comparing(IscrittoDto::cognome).thenComparing(IscrittoDto::nome)).toList();
        boolean siTiene = siTiene(l, giorno);
        boolean segna = siTiene && !giorno.isAfter(LocalDate.now(FUSO)) && (istruttore || coordinatore);
        return new ElencoIscritti(l.getId(), l.getCorso().getId(), l.getCorso().getNome(), l.getGiornoSettimana(), l.getInizio(), l.getFine(),
            giorno, siTiene, l.getPosti(), elenco, segna, iscrive);
    }

    @Transactional
    IscrittoDto iscrivi(Utente u, UUID lezioneId, NuovaIscrizione in) {
        Lezione l = lezione(lezioneId);
        if (!accesso.gestisceIscrizioni(u, impianto(l))) throw new AccessDeniedException("Per iscrivere serve il permesso «Iscrizioni».");
        LocalDate dal = in.dal() != null ? in.dal() : LocalDate.now(FUSO);
        if (l.getValidaAl() != null && dal.isAfter(l.getValidaAl())) throw new IllegalArgumentException("Il turno finisce prima di quella data.");
        if (l.getPosti() != null && !Boolean.TRUE.equals(in.forza())) {
            long occupati = iscrizioni.findByLezioneIdOrderByDal(lezioneId).stream().filter(i -> i.attivaIl(dal)).count();
            if (occupati >= l.getPosti())
                throw new Conflitto("Il turno è al completo (" + occupati + "/" + l.getPosti() + "). Per iscrivere comunque, conferma il soprannumero.");
        }
        Persone.PersonaScheda p;
        if (in.personaId() != null) {
            p = persone.schede(List.of(in.personaId())).get(in.personaId());
            if (p == null) throw new NonTrovato("Persona non trovata.");
        } else if (in.nuovaPersona() != null) {
            p = persone.crea(in.nuovaPersona().nome(), in.nuovaPersona().cognome(), in.nuovaPersona().dataNascita());
        } else throw new IllegalArgumentException("Scegli chi iscrivere.");
        Iscrizione i = iscrizioni.saveAndFlush(new Iscrizione(lezioneId, p.id(), dal));
        Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), dal).getYears();
        return new IscrittoDto(i.getId(), p.id(), p.nome(), p.cognome(), p.dataNascita(), eta, i.getDal(), null, null);
    }

    /** Ultimo giorno di frequenza (ritiro, cambio di turno). */
    @Transactional
    void termina(Utente u, UUID iscrizioneId, Termine in) {
        Iscrizione i = iscrizioni.findById(iscrizioneId).orElseThrow(() -> new NonTrovato("Iscrizione non trovata."));
        Lezione l = lezione(i.getLezioneId());
        if (!accesso.gestisceIscrizioni(u, impianto(l))) throw new AccessDeniedException("Per iscrivere serve il permesso «Iscrizioni».");
        i.termina(in.al() != null ? in.al() : LocalDate.now(FUSO));
    }

    @Transactional
    ElencoIscritti segnaPresenze(Utente u, UUID lezioneId, Presenze in) {
        Lezione l = lezione(lezioneId);
        boolean istruttore = u.personaId().equals(l.istruttoreEffettivo());
        if (!istruttore && !accesso.segnaTutteLePresenze(u, impianto(l)))
            throw new AccessDeniedException("Le presenze le segna l'istruttore del turno (o chi ha «Presenze di tutti»).");
        LocalDate g = in.data();
        if (g == null) throw new IllegalArgumentException("Indica la data.");
        if (g.isAfter(LocalDate.now(FUSO))) throw new IllegalArgumentException("Le presenze si segnano il giorno della lezione o dopo, non prima.");
        if (!siTiene(l, g)) throw new IllegalArgumentException("In quella data il turno non si tiene.");
        Set<UUID> iscritti = iscrizioni.findByLezioneIdOrderByDal(lezioneId).stream().filter(i -> i.attivaIl(g)).map(Iscrizione::getPersonaId)
            .collect(Collectors.toSet());
        Map<UUID, Presenza> esistenti = new HashMap<>();
        presenze.delGiorno(lezioneId, g).forEach(p -> esistenti.put(p.getChiave().personaId(), p));
        for (PresenzaInput p : in.presenze() == null ? List.<PresenzaInput>of() : in.presenze()) {
            if (!iscritti.contains(p.personaId())) throw new IllegalArgumentException("Una persona non è iscritta a questo turno in quella data.");
            Presenza riga = esistenti.computeIfAbsent(p.personaId(), k -> new Presenza(new Presenza.Chiave(lezioneId, g, k)));
            riga.segna(p.presente(), u.personaId());
            presenze.save(riga);
        }
        presenze.flush();
        return elenco(u, lezioneId, g);
    }

    /** Il turno si tiene in quella data: è il suo giorno (non saltato né spostato altrove), oppure è stato spostato lì. */
    static boolean siTiene(Lezione l, LocalDate g) {
        boolean spostataQui = l.getEccezioni().stream().anyMatch(e -> e.getTipo() == TipoEccezione.SPOSTATA && g.equals(e.getNuovaData()));
        if (spostataQui) return true;
        return l.cadeIl(g) && l.eccezioneIl(g).isEmpty();
    }

    private Lezione lezione(UUID id) {
        return lezioni.completa(id).orElseThrow(() -> new NonTrovato("Turno del corso non trovato."));
    }

    private static UUID impianto(Lezione l) {
        return l.getCorso().getImpiantoId();
    }
}
