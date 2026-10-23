package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.listino.Listino;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La scheda cliente per la segreteria: certificati, tesseramenti, abbonamenti, turni frequentati e liste d'attesa. */
@Service
class SchedaClientiService {

    record PersonaDto(UUID id, String nome, String cognome, LocalDate dataNascita, Integer eta) {}

    record CertificatoDto(UUID id, String tipo, LocalDate rilasciatoIl, LocalDate scadenza, String note, String firmatoDa, boolean valido) {}

    record TesseramentoDto(UUID id, String stagione, LocalDate dal, LocalDate al, BigDecimal importo, LocalDate pagatoIl, boolean valido) {}

    record AbbonamentoDto(UUID id, String tipo, UUID corsoId, String corso, Integer volteASettimana, Integer ingressi, int usati,
                          Integer residui, Instant ultimoIngresso, LocalDate dal, LocalDate al, LocalDate alEffettivo, List<Clienti.Periodo> sospensioni,
                          boolean sospesoOggi, BigDecimal importo, String note, boolean valido, UUID offertaId, BigDecimal importoListino,
                          String motivoImporto) {}

    record StagioneDto(String stagione, LocalDate dal, LocalDate al, BigDecimal importo) {}

    private static StagioneDto dto(it.leonexiii.gestionale.societa.Societa.Stagione s) {
        return new StagioneDto(s.nome(), s.dal(), s.al(), s.quota());
    }

    record Famiglia(List<Persone.Familiare> tutori, List<Persone.Familiare> assistiti, List<Persone.Familiare> fratelli, List<String> destinatari) {}

    record ConsensoDto(UUID id, String tipo, String firmatoDa, LocalDate datoIl, LocalDate validoFino, boolean email, boolean sms, boolean cartaceo,
                       Instant revocatoIl, boolean valido) {}

    record NuovoConsenso(String tipo, String firmatoDa, LocalDate datoIl, LocalDate validoFino, Boolean email, Boolean sms, Boolean cartaceo) {}

    record ModificaLegame(String relazione, boolean comunicazioni, boolean delegatoRitiro) {}

    record Scheda(PersonaDto persona, Persone.Anagrafica anagrafica, Persone.Contatti contatti, Famiglia famiglia, List<ConsensoDto> consensi,
                  List<CertificatoDto> certificati, List<TesseramentoDto> tesseramenti,
                  List<AbbonamentoDto> abbonamenti, List<Frequenze.TurnoFrequentato> turni, List<Frequenze.InAttesa> attesa, StagioneDto stagioneProposta,
                  List<StagioneDto> stagioniTesseramento, List<Clienti.GruppoDi> gruppi,
                  List<it.leonexiii.gestionale.notifiche.Posta.EmailBreve> email) {}

    record NuoviContatti(String email, String telefono) {}

    record NuovoCliente(String nome, String cognome, LocalDate dataNascita) {}

    record NuovoCertificato(String tipo, LocalDate rilasciatoIl, LocalDate scadenza, String note, String firmatoDa) {}

    record NuovaSospensione(LocalDate dal, LocalDate al, String motivo) {}

    record NuovoTesseramento(String stagione, LocalDate dal, LocalDate al, BigDecimal importo, LocalDate pagatoIl) {}

    /**
     * Dal listino: offertaId (+ volteASettimana per i corsi, corsoId se l'offerta vale per più corsi), e l'importo solo se
     * diverso da quello proposto (con motivoImporto). Senza offerta: tipo, corso, ingressi e importo scritti a mano.
     */
    record NuovoAbbonamento(String tipo, UUID corsoId, Integer volteASettimana, Integer ingressi, LocalDate dal, LocalDate al, BigDecimal importo, String note,
                            UUID offertaId, String motivoImporto) {}

    record Termine(LocalDate al) {}

    private final Persone persone;
    private final Clienti clienti;
    private final Frequenze frequenze;
    private final CertificatoRepository certificati;
    private final TesseramentoRepository tesseramenti;
    private final AbbonamentoRepository abbonamenti;
    private final ConsultazioneRepository consultazioni;
    private final SospensioneRepository sospensioni;
    private final ConsensoRepository consensi;
    private final it.leonexiii.gestionale.notifiche.Posta posta;
    private final Clock orologio;
    private final it.leonexiii.gestionale.societa.Societa societa;
    private final Listino listino;
    private final Autorizzazioni autorizzazioni;

    SchedaClientiService(Persone persone, Clienti clienti, Frequenze frequenze, CertificatoRepository certificati, TesseramentoRepository tesseramenti,
                         AbbonamentoRepository abbonamenti, ConsultazioneRepository consultazioni, SospensioneRepository sospensioni,
                         ConsensoRepository consensi, it.leonexiii.gestionale.notifiche.Posta posta, Clock orologio,
                         it.leonexiii.gestionale.societa.Societa societa, Listino listino, Autorizzazioni autorizzazioni) {
        this.listino = listino;
        this.autorizzazioni = autorizzazioni;
        this.societa = societa;
        this.consensi = consensi;
        this.posta = posta;
        this.sospensioni = sospensioni;
        this.persone = persone;
        this.clienti = clienti;
        this.frequenze = frequenze;
        this.certificati = certificati;
        this.tesseramenti = tesseramenti;
        this.abbonamenti = abbonamenti;
        this.consultazioni = consultazioni;
        this.orologio = orologio;
    }

    /** Apre la scheda e registra chi l'ha aperta. */
    @Transactional
    Scheda scheda(Utente u, UUID personaId) {
        Persone.PersonaScheda p = persona(personaId);
        LocalDate oggi = LocalDate.now(orologio);
        consultazioni.save(new ConsultazioneScheda(personaId, u.personaId(), orologio.instant()));
        var cert = certificati.findByPersonaIdOrderByScadenzaDesc(personaId).stream().filter(c -> c.getAnnullatoIl() == null)
            .map(c -> new CertificatoDto(c.getId(), c.getTipo(), c.getRilasciatoIl(), c.getScadenza(), c.getNote(), c.getFirmatoDa(), c.validoIl(oggi))).toList();
        var tess = tesseramenti.findByPersonaIdOrderByDalDesc(personaId).stream().filter(t -> t.getAnnullatoIl() == null)
            .map(t -> new TesseramentoDto(t.getId(), t.getStagione(), t.getDal(), t.getAl(), t.getImporto(), t.getPagatoIl(), t.validoIl(oggi))).toList();
        var note = abbonamenti.findByPersonaIdOrderByDalDesc(personaId).stream().filter(a -> a.getAnnullatoIl() == null).toList();
        var abb = clienti.abbonamentiDi(personaId).stream().map(a -> {
            Abbonamento e = note.stream().filter(x -> x.getId().equals(a.id())).findFirst().orElseThrow();
            boolean valido = a.validoIl(oggi) && (!a.carnet() || a.residui() > 0);
            return new AbbonamentoDto(a.id(), a.tipo(), a.corsoId(), a.corso(), a.volteASettimana(), a.ingressi(), a.usati(), a.residui(), a.ultimoIngresso(),
                a.dal(), a.al(), a.alEffettivo(), a.sospensioni(), a.sospesoIl(oggi).isPresent(), e.getImporto(), e.getNote(), valido,
                e.getOffertaId(), e.getImportoListino(), e.getMotivoImporto());
        }).toList();
        var daProporre = societa.stagioniDaProporre(oggi).stream().map(SchedaClientiService::dto).toList();
        var proposta = societa.stagioneIl(oggi).map(SchedaClientiService::dto).orElse(daProporre.isEmpty() ? null : daProporre.getFirst());
        Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), oggi).getYears();
        var famiglia = new Famiglia(persone.tutoriDi(personaId), persone.assistitiDi(personaId), persone.fratelliDi(personaId),
            persone.destinatari(List.of(personaId)).getOrDefault(personaId, List.of()));
        var cons = consensi.findByPersonaIdOrderByDatoIlDesc(personaId).stream().map(c -> new ConsensoDto(c.getId(), c.tipo().name(), c.getFirmatoDa(),
            c.getDatoIl(), c.getValidoFino(), c.isEmail(), c.isSms(), c.isCartaceo(), c.getRevocatoIl(), c.validoIl(oggi))).toList();
        return new Scheda(new PersonaDto(p.id(), p.nome(), p.cognome(), p.dataNascita(), eta), persone.anagrafica(personaId),
            persone.contatti(List.of(personaId)).get(personaId), famiglia, cons,
            cert, tess, abb, frequenze.turniDi(personaId, oggi), frequenze.attesaDi(personaId), proposta, daProporre, clienti.gruppiDi(personaId), posta.diPersona(personaId, 10));
    }

    @Transactional
    Persone.Anagrafica anagrafica(UUID personaId, Persone.ModificaAnagrafica in) {
        return persone.aggiornaAnagrafica(personaId, in);
    }

    @Transactional
    UUID tutore(UUID personaId, Persone.NuovoTutore in) {
        return persone.aggiungiTutore(personaId, in);
    }

    @Transactional
    void modificaLegame(UUID legameId, ModificaLegame in) {
        persone.modificaLegame(legameId, in.relazione(), in.comunicazioni(), in.delegatoRitiro());
    }

    @Transactional
    void chiudiLegame(UUID legameId) {
        persone.chiudiLegame(legameId);
    }

    /** Un consenso firmato (per i minori da un genitore): senza «valido fino» vale finché non si revoca. */
    @Transactional
    UUID consenso(Utente u, UUID personaId, NuovoConsenso in) {
        persona(personaId);
        Consenso.Tipo tipo;
        try {
            tipo = Consenso.Tipo.valueOf(String.valueOf(in.tipo()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo di consenso non valido: TRATTAMENTO, MARKETING o IMMAGINI.");
        }
        LocalDate dato = in.datoIl() != null ? in.datoIl() : LocalDate.now(orologio);
        return consensi.saveAndFlush(new Consenso(personaId, tipo, in.firmatoDa(), dato, in.validoFino(), Boolean.TRUE.equals(in.email()),
            Boolean.TRUE.equals(in.sms()), Boolean.TRUE.equals(in.cartaceo()), u.personaId(), orologio.instant())).getId();
    }

    @Transactional
    void revocaConsenso(UUID id) {
        consensi.findById(id).orElseThrow(() -> new NonTrovato("Consenso non trovato.")).revoca(orologio.instant());
    }

    @Transactional
    Persone.Contatti contatti(UUID personaId, NuoviContatti in) {
        return persone.aggiornaContatti(personaId, in.email(), in.telefono());
    }

    @Transactional
    PersonaDto nuovoCliente(NuovoCliente in) {
        var p = persone.crea(in.nome(), in.cognome(), in.dataNascita());
        Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), LocalDate.now(orologio)).getYears();
        return new PersonaDto(p.id(), p.nome(), p.cognome(), p.dataNascita(), eta);
    }

    @Transactional
    UUID nuovoCertificato(Utente u, UUID personaId, NuovoCertificato in) {
        persona(personaId);
        return certificati.saveAndFlush(new CertificatoMedico(personaId, in.tipo() == null ? "NON_AGONISTICO" : in.tipo(), in.rilasciatoIl(), in.scadenza(),
            in.note(), in.firmatoDa(), u.personaId(), orologio.instant())).getId();
    }

    /**
     * Senza stagione si usa quella da proporre oggi (la in corso, o la prossima se manca poco); date e quota vengono dalla
     * stagione impostata dalla società, se non indicate.
     */
    @Transactional
    UUID nuovoTesseramento(Utente u, UUID personaId, NuovoTesseramento in) {
        persona(personaId);
        LocalDate oggi = LocalDate.now(orologio);
        var s = in.stagione() == null || in.stagione().isBlank()
            ? societa.stagioniDaProporre(oggi).stream().findFirst()
            : societa.stagione(in.stagione());
        if (s.isEmpty() && (in.stagione() == null || in.dal() == null || in.al() == null))
            throw new IllegalArgumentException(in.stagione() == null || in.stagione().isBlank()
                ? "Nessuna stagione in corso: aggiungila in «Impostazioni della società»." : "Stagione non trovata: aggiungila in «Impostazioni della società».");
        String nome = s.map(it.leonexiii.gestionale.societa.Societa.Stagione::nome).orElseGet(() -> in.stagione().strip());
        LocalDate dal = in.dal() != null ? in.dal() : s.get().dal();
        LocalDate al = in.al() != null ? in.al() : s.get().al();
        BigDecimal importo = in.importo() != null ? in.importo() : s.map(it.leonexiii.gestionale.societa.Societa.Stagione::quota).orElse(null);
        LocalDate pagato = in.pagatoIl() != null ? in.pagatoIl() : LocalDate.now(orologio);
        return tesseramenti.saveAndFlush(new Tesseramento(personaId, nome, dal, al, importo, pagato, u.personaId(), orologio.instant())).getId();
    }

    @Transactional
    UUID nuovoAbbonamento(Utente u, UUID personaId, NuovoAbbonamento in) {
        persona(personaId);
        LocalDate dal = in.dal() != null ? in.dal() : LocalDate.now(orologio);
        if (in.offertaId() != null) return dalListino(u, personaId, dal, in);
        Abbonamento a;
        if (in.corsoId() != null && frequenze.corsi().stream().noneMatch(c -> c.id().equals(in.corsoId()))) throw new NonTrovato("Corso non trovato.");
        if (Abbonamento.CARNET.equals(in.tipo()))
            a = Abbonamento.carnet(personaId, in.ingressi(), in.corsoId(), dal, in.al(), in.importo(), in.note(), u.personaId(), orologio.instant());
        else if (Abbonamento.CORSO.equals(in.tipo())) {
            a = Abbonamento.corso(personaId, in.corsoId(), in.volteASettimana(), dal, in.al(), in.importo(), in.note(), u.personaId(), orologio.instant());
        } else throw new IllegalArgumentException("Scegli il tipo: abbonamento al corso o carnet.");
        return abbonamenti.saveAndFlush(a).getId();
    }

    /** L'importo proposto dal listino per questa persona (con i suoi gruppi) e questa data d'inizio. */
    @Transactional(readOnly = true)
    Listino.Proposta proposta(UUID personaId, UUID offertaId, Integer volte, LocalDate dal) {
        persona(personaId);
        return listino.proponi(offertaId, volte, gruppiDi(personaId), dal != null ? dal : LocalDate.now(orologio));
    }

    /**
     * Venduto dal listino: tipo, corso, ingressi e scadenza vengono dall'offerta. L'importo è quello proposto; se è
     * diverso serve il permesso «Cambiare l'importo proposto» e il motivo.
     */
    private UUID dalListino(Utente u, UUID personaId, LocalDate dal, NuovoAbbonamento in) {
        Listino.Proposta p = listino.proponi(in.offertaId(), in.volteASettimana(), gruppiDi(personaId), dal);
        Listino.OffertaDto o = listino.offerta(in.offertaId());
        BigDecimal importo = in.importo() != null ? in.importo() : p.proposto();
        String motivo = in.motivoImporto() == null || in.motivoImporto().isBlank() ? null : in.motivoImporto().strip();
        if (importo.compareTo(p.proposto()) != 0) {
            if (!autorizzazioni.puo(u, Permesso.SCONTI)) throw new AccessDeniedException("Per cambiare l'importo proposto serve il permesso «Cambiare l'importo proposto».");
            if (motivo == null) throw new IllegalArgumentException("L'importo è diverso da quello proposto (" + p.proposto() + " €): scrivi il motivo.");
        }
        LocalDate al = in.al() != null ? in.al() : p.al();
        Abbonamento a = switch (o.tipo()) {
            case "CORSO" -> {
                UUID corso = in.corsoId() != null ? in.corsoId() : o.corsi().size() == 1 ? o.corsi().getFirst() : null;
                if (corso == null) throw new IllegalArgumentException("Scegli il corso: l'offerta vale per più corsi.");
                if (!o.corsi().contains(corso)) throw new IllegalArgumentException("Questo corso non è compreso nell'offerta.");
                Integer volte = in.volteASettimana() != null ? in.volteASettimana() : p.volte();
                // prezzo unico → una volta a settimana; «open» → tutti i giorni
                int v = volte == null ? 1 : volte == 0 ? 7 : volte;
                yield Abbonamento.corso(personaId, corso, v, dal, al, importo, in.note(), u.personaId(), orologio.instant());
            }
            case "CARNET" -> Abbonamento.carnet(personaId, o.ingressi(), o.corsoId(), dal, al, importo, in.note(), u.personaId(), orologio.instant());
            default -> throw new IllegalArgumentException("«" + o.nome() + "» non è un abbonamento né un carnet: si addebita con i pagamenti.");
        };
        a.daListino(o.id(), p.proposto(), importo.compareTo(p.proposto()) != 0 ? motivo : null);
        return abbonamenti.saveAndFlush(a).getId();
    }

    /** Quanto si rimborserebbe recedendo in quella data, secondo la regola della stagione dell'offerta (vuoto se non c'è). */
    @Transactional(readOnly = true)
    Optional<Listino.Rimborso> recesso(UUID abbonamentoId, LocalDate data) {
        Abbonamento a = abbonamenti.findById(abbonamentoId).orElseThrow(() -> new NonTrovato("Abbonamento non trovato."));
        if (a.getOffertaId() == null) return Optional.empty();
        return listino.recesso(listino.stagioneDi(a.getOffertaId()), a.getImporto(), data != null ? data : LocalDate.now(orologio));
    }

    private Set<UUID> gruppiDi(UUID personaId) {
        return clienti.gruppiDi(personaId).stream().map(Clienti.GruppoDi::gruppoId).collect(Collectors.toSet());
    }

    @Transactional
    void terminaAbbonamento(UUID id, Termine in) {
        abbonamenti.findById(id).orElseThrow(() -> new NonTrovato("Abbonamento non trovato.")).termina(in.al() != null ? in.al() : LocalDate.now(orologio));
    }

    /** Sospende l'abbonamento (es. infortunio): non vale in quei giorni e la scadenza si allunga di altrettanti. */
    @Transactional
    UUID sospendi(Utente u, UUID abbonamentoId, NuovaSospensione in) {
        Abbonamento a = abbonamenti.findById(abbonamentoId).orElseThrow(() -> new NonTrovato("Abbonamento non trovato."));
        if (a.getAnnullatoIl() != null) throw new IllegalArgumentException("L'abbonamento è annullato.");
        Sospensione s = new Sospensione(abbonamentoId, in.dal(), in.al(), in.motivo(), u.personaId(), orologio.instant());
        if (in.dal().isBefore(a.getDal())) throw new IllegalArgumentException("La sospensione inizia prima dell'abbonamento.");
        boolean accavallata = sospensioni.findByAbbonamentoIdInOrderByDal(List.of(abbonamentoId)).stream()
            .anyMatch(x -> x.getAnnullataIl() == null && x.siAccavallaCon(in.dal(), in.al()));
        if (accavallata) throw new it.leonexiii.gestionale.condiviso.Conflitto("C'è già una sospensione in quei giorni.");
        return sospensioni.saveAndFlush(s).getId();
    }

    @Transactional
    void annullaSospensione(UUID id) {
        sospensioni.findById(id).orElseThrow(() -> new NonTrovato("Sospensione non trovata.")).annulla(orologio.instant());
    }

    /** Per gli errori di inserimento: la riga resta nello storico ma non conta più. */
    @Transactional
    void annulla(String cosa, UUID id) {
        Instant ora = orologio.instant();
        switch (cosa) {
            case "certificati" -> certificati.findById(id).orElseThrow(() -> new NonTrovato("Certificato non trovato.")).annulla(ora);
            case "tesseramenti" -> tesseramenti.findById(id).orElseThrow(() -> new NonTrovato("Tesseramento non trovato.")).annulla(ora);
            case "abbonamenti" -> abbonamenti.findById(id).orElseThrow(() -> new NonTrovato("Abbonamento non trovato.")).annulla(ora);
            default -> throw new NonTrovato("Non trovato.");
        }
    }

    private Persone.PersonaScheda persona(UUID id) {
        var p = persone.schede(List.of(id)).get(id);
        if (p == null) throw new NonTrovato("Persona non trovata.");
        return p;
    }
}
