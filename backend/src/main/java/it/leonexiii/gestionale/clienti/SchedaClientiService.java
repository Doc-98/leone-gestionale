package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
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
                          boolean sospesoOggi, BigDecimal importo, String note, boolean valido) {}

    record StagioneDto(String stagione, LocalDate dal, LocalDate al, BigDecimal importo) {}

    record Famiglia(List<Persone.Familiare> tutori, List<Persone.Familiare> assistiti, List<Persone.Familiare> fratelli, List<String> destinatari) {}

    record ConsensoDto(UUID id, String tipo, String firmatoDa, LocalDate datoIl, LocalDate validoFino, boolean email, boolean sms, boolean cartaceo,
                       Instant revocatoIl, boolean valido) {}

    record NuovoConsenso(String tipo, String firmatoDa, LocalDate datoIl, LocalDate validoFino, Boolean email, Boolean sms, Boolean cartaceo) {}

    record ModificaLegame(String relazione, boolean comunicazioni, boolean delegatoRitiro) {}

    record Scheda(PersonaDto persona, Persone.Anagrafica anagrafica, Persone.Contatti contatti, Famiglia famiglia, List<ConsensoDto> consensi,
                  List<CertificatoDto> certificati, List<TesseramentoDto> tesseramenti,
                  List<AbbonamentoDto> abbonamenti, List<Frequenze.TurnoFrequentato> turni, List<Frequenze.InAttesa> attesa, StagioneDto stagioneProposta,
                  List<it.leonexiii.gestionale.notifiche.Posta.EmailBreve> email) {}

    record NuoviContatti(String email, String telefono) {}

    record NuovoCliente(String nome, String cognome, LocalDate dataNascita) {}

    record NuovoCertificato(String tipo, LocalDate rilasciatoIl, LocalDate scadenza, String note, String firmatoDa) {}

    record NuovaSospensione(LocalDate dal, LocalDate al, String motivo) {}

    record NuovoTesseramento(String stagione, LocalDate dal, LocalDate al, BigDecimal importo, LocalDate pagatoIl) {}

    record NuovoAbbonamento(String tipo, UUID corsoId, Integer volteASettimana, Integer ingressi, LocalDate dal, LocalDate al, BigDecimal importo, String note) {}

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

    SchedaClientiService(Persone persone, Clienti clienti, Frequenze frequenze, CertificatoRepository certificati, TesseramentoRepository tesseramenti,
                         AbbonamentoRepository abbonamenti, ConsultazioneRepository consultazioni, SospensioneRepository sospensioni,
                         ConsensoRepository consensi, it.leonexiii.gestionale.notifiche.Posta posta, Clock orologio) {
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
                a.dal(), a.al(), a.alEffettivo(), a.sospensioni(), a.sospesoIl(oggi).isPresent(), e.getImporto(), e.getNote(), valido);
        }).toList();
        Stagione s = Stagione.di(oggi);
        Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), oggi).getYears();
        var famiglia = new Famiglia(persone.tutoriDi(personaId), persone.assistitiDi(personaId), persone.fratelliDi(personaId),
            persone.destinatari(List.of(personaId)).getOrDefault(personaId, List.of()));
        var cons = consensi.findByPersonaIdOrderByDatoIlDesc(personaId).stream().map(c -> new ConsensoDto(c.getId(), c.tipo().name(), c.getFirmatoDa(),
            c.getDatoIl(), c.getValidoFino(), c.isEmail(), c.isSms(), c.isCartaceo(), c.getRevocatoIl(), c.validoIl(oggi))).toList();
        return new Scheda(new PersonaDto(p.id(), p.nome(), p.cognome(), p.dataNascita(), eta), persone.anagrafica(personaId),
            persone.contatti(List.of(personaId)).get(personaId), famiglia, cons,
            cert, tess, abb, frequenze.turniDi(personaId, oggi), frequenze.attesaDi(personaId), new StagioneDto(s.nome(), s.dal(), s.al(), s.importo()), posta.diPersona(personaId, 10));
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

    /** Senza date si usa la stagione in corso (settembre–luglio) con la quota di base. */
    @Transactional
    UUID nuovoTesseramento(Utente u, UUID personaId, NuovoTesseramento in) {
        persona(personaId);
        Stagione s = Stagione.di(LocalDate.now(orologio));
        String nome = in.stagione() == null || in.stagione().isBlank() ? s.nome() : in.stagione().strip();
        LocalDate dal = in.dal() != null ? in.dal() : s.dal();
        LocalDate al = in.al() != null ? in.al() : s.al();
        BigDecimal importo = in.importo() != null ? in.importo() : s.importo();
        LocalDate pagato = in.pagatoIl() != null ? in.pagatoIl() : LocalDate.now(orologio);
        return tesseramenti.saveAndFlush(new Tesseramento(personaId, nome, dal, al, importo, pagato, u.personaId(), orologio.instant())).getId();
    }

    @Transactional
    UUID nuovoAbbonamento(Utente u, UUID personaId, NuovoAbbonamento in) {
        persona(personaId);
        LocalDate dal = in.dal() != null ? in.dal() : LocalDate.now(orologio);
        Abbonamento a;
        if (in.corsoId() != null && frequenze.corsi().stream().noneMatch(c -> c.id().equals(in.corsoId()))) throw new NonTrovato("Corso non trovato.");
        if (Abbonamento.CARNET.equals(in.tipo()))
            a = Abbonamento.carnet(personaId, in.ingressi(), in.corsoId(), dal, in.al(), in.importo(), in.note(), u.personaId(), orologio.instant());
        else if (Abbonamento.CORSO.equals(in.tipo())) {
            a = Abbonamento.corso(personaId, in.corsoId(), in.volteASettimana(), dal, in.al(), in.importo(), in.note(), u.personaId(), orologio.instant());
        } else throw new IllegalArgumentException("Scegli il tipo: abbonamento al corso o carnet.");
        return abbonamenti.saveAndFlush(a).getId();
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
