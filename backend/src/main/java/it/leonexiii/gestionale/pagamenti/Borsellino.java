package it.leonexiii.gestionale.pagamenti;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il borsellino di ogni persona (ADR 0018, parti 7–8): credito in euro (rimborsi; si usa per pagare; può scadere; si
 * trasferisce ai familiari) e crediti dei servizi (phon, docce; si comprano con una ricarica in segreteria). Qui anche
 * la cauzione del braccialetto: addebitata alla consegna, restituita alla riconsegna. Le regole sono della società.
 */
@Service
public class Borsellino {

    public record Regole(BigDecimal valoreCredito, BigDecimal ricaricaMinima, Integer validitaEuroMesi, boolean trasferibile, boolean restituibile,
                         BigDecimal cauzione, String cauzioneRiconsegna, boolean creditiRiconsegna, String creditiSmarrimento) {}

    public record Saldo(BigDecimal euro, int crediti, BigDecimal valoreCrediti, LocalDate prossimaScadenza, BigDecimal inScadenza) {}

    /** Esito di un consumo di crediti da parte di una macchina. */
    public record Consumo(boolean fatto, int residui) {}

    /** Alla riconsegna di un braccialetto: quanto è stato restituito della cauzione e dei crediti, e come. */
    public record EsitoRiconsegna(BigDecimal cauzione, String modoCauzione, int crediti, BigDecimal valoreCrediti, String modoCrediti) {}

    record MovimentoDto(UUID id, String saldo, BigDecimal quantita, String causale, String descrizione, LocalDate scadenza, String controparte,
                        java.time.Instant creatoIl, String creatoDa) {}

    record Destinatario(UUID personaId, String nome, String relazione) {}

    record Scheda(UUID personaId, Saldo saldo, Regole regole, List<MovimentoDto> movimenti, List<Destinatario> trasferibileA) {}

    record NuovaRicarica(UUID paganteId, BigDecimal importo, List<Pagamenti.MetodoIn> metodi, String note) {}

    record Trasferimento(UUID aPersonaId, BigDecimal importo) {}

    record Restituzione(BigDecimal importo, UUID metodoId) {}

    record Correzione(String saldo, BigDecimal quantita, String motivo) {}

    private final Movimenti movimenti;
    private final MovimentoRepository repo;
    private final RegoleBorsellinoRepository regole;
    private final AddebitoRepository addebiti;
    private final UscitaRepository uscite;
    private final MetodoRepository metodi;
    private final Pagamenti pagamenti;
    private final Persone persone;
    private final Clock orologio;

    Borsellino(Movimenti movimenti, MovimentoRepository repo, RegoleBorsellinoRepository regole, AddebitoRepository addebiti, UscitaRepository uscite,
               MetodoRepository metodi, Pagamenti pagamenti, Persone persone, Clock orologio) {
        this.movimenti = movimenti;
        this.repo = repo;
        this.regole = regole;
        this.addebiti = addebiti;
        this.uscite = uscite;
        this.metodi = metodi;
        this.pagamenti = pagamenti;
        this.persone = persone;
        this.orologio = orologio;
    }

    // ── Per gli altri moduli ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Regole regole() {
        return movimenti.regole().dto();
    }

    @Transactional(readOnly = true)
    public Saldo saldo(UUID persona) {
        var lotti = movimenti.lotti(persona);
        LocalDate prossima = lotti.stream().map(Movimenti.Lotto::scadenza).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        BigDecimal inScadenza = prossima == null ? Movimenti.ZERO
            : lotti.stream().filter(l -> prossima.equals(l.scadenza())).map(Movimenti.Lotto::residuo).reduce(Movimenti.ZERO, BigDecimal::add);
        int crediti = movimenti.crediti(persona);
        return new Saldo(movimenti.euro(persona), crediti, valore(crediti), prossima, inScadenza);
    }

    /** Euro nel borsellino (es. rimborso per recesso), con la scadenza delle regole. */
    @Transactional
    public void accredita(UUID persona, BigDecimal euro, String descrizione, UUID riferimentoId, UUID da) {
        if (euro == null || euro.signum() <= 0) return;
        movimenti.blocca(persona);
        movimenti.salva(new MovimentoBorsellino(persona, MovimentoBorsellino.Saldo.EURO, euro, MovimentoBorsellino.Causale.ACCREDITO, descrizione,
            movimenti.scadenzaNuova(), da, orologio.instant()).conRiferimento(riferimentoId));
    }

    /** Una macchina scala i crediti; se non bastano non scala niente. */
    @Transactional
    public Consumo consuma(UUID persona, int crediti, String descrizione, UUID riferimentoId) {
        movimenti.blocca(persona);
        int saldo = movimenti.crediti(persona);
        if (crediti <= 0) return new Consumo(true, saldo);
        if (saldo < crediti) return new Consumo(false, saldo);
        movimenti.salva(new MovimentoBorsellino(persona, MovimentoBorsellino.Saldo.CREDITI, BigDecimal.valueOf(-crediti), MovimentoBorsellino.Causale.CONSUMO,
            descrizione, null, null, orologio.instant()).conRiferimento(riferimentoId));
        return new Consumo(true, saldo - crediti);
    }

    /** Alla consegna di un braccialetto: la cauzione delle regole diventa un addebito (se c'è). */
    @Transactional
    public void addebitaCauzione(UUID persona, UUID braccialettoId, String codice, UUID da) {
        BigDecimal c = movimenti.regole().dto().cauzione();
        if (c.signum() <= 0) return;
        pagamenti.addebita(List.of(new Pagamenti.NuovoAddebito(persona, "Cauzione braccialetto " + codice, c, LocalDate.now(orologio),
            Pagamenti.Origine.CAUZIONE, braccialettoId, null, null, null, null)), da);
    }

    /**
     * Riconsegna di un braccialetto. La cauzione pagata si restituisce in denaro (modo DENARO, con il metodo) o nel
     * borsellino, o si trattiene (TRATTIENI); quella non pagata si annulla. «modoCrediti» (DENARO, BORSELLINO, o null per
     * non rimborsarli) vale per i crediti dei servizi, convertiti in euro.
     */
    @Transactional
    public EsitoRiconsegna riconsegna(UUID persona, UUID braccialettoId, String modoCauzione, String modoCrediti, UUID metodoId, UUID da) {
        Regole r = regole();
        LocalDate oggi = LocalDate.now(orologio);
        BigDecimal pagata = pagamenti.pagatoPer(Pagamenti.Origine.CAUZIONE, braccialettoId);
        pagamenti.chiudi(Pagamenti.Origine.CAUZIONE, braccialettoId, "Braccialetto riconsegnato", da);
        String modo = pagata.signum() == 0 ? null : modo(modoCauzione, r.cauzioneRiconsegna(), true);
        if ("DENARO".equals(modo)) esce(persona, pagata, metodoId, "Restituzione cauzione del braccialetto", da, oggi);
        else if ("BORSELLINO".equals(modo)) accredita(persona, pagata, "Cauzione del braccialetto restituita", braccialettoId, da);

        int crediti = movimenti.crediti(persona);
        String mc = modoCrediti == null || modoCrediti.isBlank() || crediti <= 0 ? null : modo(modoCrediti, r.cauzioneRiconsegna(), false);
        BigDecimal valore = Movimenti.ZERO;
        if (mc != null) {
            movimenti.blocca(persona);
            valore = valore(crediti);
            var m = movimenti.salva(new MovimentoBorsellino(persona, MovimentoBorsellino.Saldo.CREDITI, BigDecimal.valueOf(-crediti),
                MovimentoBorsellino.Causale.RIMBORSO, "Crediti rimborsati alla riconsegna del braccialetto", null, da, orologio.instant()));
            m.conRiferimento(braccialettoId);
            if ("DENARO".equals(mc)) esce(persona, valore, metodoId, "Rimborso di " + crediti + " crediti (riconsegna del braccialetto)", da, oggi);
            else accredita(persona, valore, "Rimborso di " + crediti + " crediti (riconsegna del braccialetto)", braccialettoId, da);
        }
        return new EsitoRiconsegna(modo == null || "TRATTIENI".equals(modo) ? Movimenti.ZERO : pagata, modo, mc == null ? 0 : crediti, valore, mc);
    }

    /** Braccialetto smarrito: con la regola «PERSI» i crediti dei servizi si perdono. Restituisce quanti. */
    @Transactional
    public int smarrito(UUID persona, UUID braccialettoId, UUID da) {
        if (!"PERSI".equals(regole().creditiSmarrimento())) return 0;
        movimenti.blocca(persona);
        int crediti = movimenti.crediti(persona);
        if (crediti <= 0) return 0;
        movimenti.salva(new MovimentoBorsellino(persona, MovimentoBorsellino.Saldo.CREDITI, BigDecimal.valueOf(-crediti), MovimentoBorsellino.Causale.SMARRIMENTO,
            "Braccialetto smarrito: crediti persi (regola della società)", null, da, orologio.instant()).conRiferimento(braccialettoId));
        return crediti;
    }

    // ── Dalla scheda ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    Scheda scheda(UUID persona) {
        if (persone.nomiCompleti(List.of(persona)).isEmpty()) throw new NonTrovato("Persona non trovata.");
        var lista = repo.findByPersonaIdOrderByCreatoIlDesc(persona);
        Set<UUID> ids = new HashSet<>();
        lista.forEach(m -> {
            if (m.getControparteId() != null) ids.add(m.getControparteId());
            if (m.getCreatoDa() != null) ids.add(m.getCreatoDa());
        });
        Map<UUID, String> nomi = ids.isEmpty() ? Map.of() : persone.nomiCompleti(ids);
        var dto = lista.stream().limit(100).map(m -> new MovimentoDto(m.getId(), m.saldo().name(), m.getQuantita(), m.causale().name(), m.getDescrizione(),
            m.getScadenza(), m.getControparteId() == null ? null : nomi.get(m.getControparteId()), m.getCreatoIl(),
            m.getCreatoDa() == null ? null : nomi.get(m.getCreatoDa()))).toList();
        return new Scheda(persona, saldo(persona), regole(), dto, familiari(persona).values().stream().toList());
    }

    /** Ricarica dei crediti dei servizi: un addebito pagato subito (anche col borsellino), e i crediti. */
    @Transactional
    UUID ricarica(UUID persona, NuovaRicarica in, UUID da, boolean flessibile) {
        Regole r = regole();
        if (in.importo() == null || in.importo().signum() <= 0) throw new IllegalArgumentException("Scrivi l'importo della ricarica.");
        if (in.importo().compareTo(r.ricaricaMinima()) < 0) throw new IllegalArgumentException("La ricarica minima è di " + r.ricaricaMinima() + " €.");
        BigDecimal[] div = in.importo().divideAndRemainder(r.valoreCredito());
        if (div[1].signum() != 0)
            throw new IllegalArgumentException("L'importo deve essere un multiplo del valore di un credito (" + r.valoreCredito() + " €).");
        int crediti = div[0].intValueExact();
        UUID addebito = pagamenti.addebita(List.of(new Pagamenti.NuovoAddebito(persona, "Ricarica di " + crediti + " crediti (phon e docce)", in.importo(),
            LocalDate.now(orologio), Pagamenti.Origine.RICARICA, null, null, null, null, null)), da).getFirst();
        UUID pagamento = pagamenti.registra(new Pagamenti.NuovoPagamento(in.paganteId() != null ? in.paganteId() : persona, null, in.importo(),
            List.of(addebito), in.metodi(), in.note(), false), da, flessibile).getFirst();
        movimenti.blocca(persona);
        movimenti.salva(new MovimentoBorsellino(persona, MovimentoBorsellino.Saldo.CREDITI, BigDecimal.valueOf(crediti), MovimentoBorsellino.Causale.RICARICA,
            "Ricarica di " + in.importo() + " €", null, da, orologio.instant()).conPagamento(pagamento));
        return pagamento;
    }

    /** Euro a un familiare: le scadenze passano con loro (prima quelli che scadono prima). */
    @Transactional
    void trasferisci(UUID persona, Trasferimento in, UUID da) {
        if (!regole().trasferibile()) throw new Conflitto("Le regole della società non permettono di trasferire il credito.");
        if (in.aPersonaId() == null || !familiari(persona).containsKey(in.aPersonaId()))
            throw new IllegalArgumentException("Il credito si trasferisce solo a un familiare (genitore, figlio, fratello o sorella).");
        if (in.importo() == null || in.importo().signum() <= 0 || in.importo().scale() > 2) throw new IllegalArgumentException("Scrivi quanto trasferire.");
        // blocchi sempre nello stesso ordine, per non incastrarsi con un trasferimento al contrario
        Stream.of(persona, in.aPersonaId()).sorted().forEach(movimenti::blocca);
        var lotti = movimenti.lotti(persona);
        String nomeA = persone.nomiCompleti(List.of(in.aPersonaId())).get(in.aPersonaId());
        String nomeDa = persone.nomiCompleti(List.of(persona)).get(persona);
        movimenti.togliEuro(persona, in.importo(), MovimentoBorsellino.Causale.TRASFERIMENTO, "Trasferito a " + nomeA, null, da).conControparte(in.aPersonaId());
        BigDecimal resto = in.importo();
        for (Movimenti.Lotto l : lotti) {
            if (resto.signum() == 0) break;
            BigDecimal parte = l.residuo().min(resto);
            movimenti.salva(new MovimentoBorsellino(in.aPersonaId(), MovimentoBorsellino.Saldo.EURO, parte, MovimentoBorsellino.Causale.TRASFERIMENTO,
                "Ricevuto da " + nomeDa, l.scadenza(), da, orologio.instant()).conControparte(persona));
            resto = resto.subtract(parte);
        }
    }

    /** Il credito in euro restituito in denaro (se le regole lo permettono). */
    @Transactional
    void restituisci(UUID persona, Restituzione in, UUID da) {
        if (!regole().restituibile()) throw new Conflitto("Le regole della società non permettono di restituire il credito in denaro.");
        if (in.importo() == null || in.importo().signum() <= 0 || in.importo().scale() > 2) throw new IllegalArgumentException("Scrivi quanto restituire.");
        LocalDate oggi = LocalDate.now(orologio);
        UUID uscita = esce(persona, in.importo(), in.metodoId(), "Credito del borsellino restituito", da, oggi);
        movimenti.togliEuro(persona, in.importo(), MovimentoBorsellino.Causale.RIMBORSO, "Restituito in denaro", null, da).conUscita(uscita);
    }

    @Transactional
    void correggi(UUID persona, Correzione in, UUID da) {
        if (in.motivo() == null || in.motivo().isBlank()) throw new IllegalArgumentException("Scrivi il motivo della correzione.");
        MovimentoBorsellino.Saldo s;
        try {
            s = MovimentoBorsellino.Saldo.valueOf(String.valueOf(in.saldo()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Saldo da correggere: EURO o CREDITI.");
        }
        if (in.quantita() == null || in.quantita().signum() == 0) throw new IllegalArgumentException("Scrivi di quanto correggere (positivo o negativo).");
        if (s == MovimentoBorsellino.Saldo.CREDITI && in.quantita().stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("I crediti sono interi.");
        if (in.quantita().scale() > 2) throw new IllegalArgumentException("Al massimo due decimali.");
        movimenti.blocca(persona);
        String motivo = in.motivo().strip();
        movimenti.salva(new MovimentoBorsellino(persona, s, in.quantita(), MovimentoBorsellino.Causale.CORREZIONE, "Correzione: " + motivo,
            movimenti.scadenzaNuova(), da, orologio.instant()));
    }

    @Transactional
    Regole modificaRegole(Regole in) {
        RegoleBorsellino r = movimenti.regole();
        r.modifica(in);
        return regole.saveAndFlush(r).dto();
    }

    /** Ogni notte: gli euro con la scadenza passata escono dal borsellino. */
    @Scheduled(cron = "${gestionale.borsellino.cron:0 15 1 * * *}", zone = "Europe/Rome")
    @Transactional
    public int scadenze() {
        LocalDate oggi = LocalDate.now(orologio);
        int quanti = 0;
        for (UUID p : repo.conEuroScaduti(oggi)) {
            movimenti.blocca(p);
            BigDecimal scaduto = movimenti.lotti(p).stream().filter(l -> l.scadenza() != null && l.scadenza().isBefore(oggi))
                .map(Movimenti.Lotto::residuo).reduce(Movimenti.ZERO, BigDecimal::add);
            if (scaduto.signum() <= 0) continue;
            movimenti.salva(new MovimentoBorsellino(p, MovimentoBorsellino.Saldo.EURO, scaduto.negate(), MovimentoBorsellino.Causale.SCADENZA,
                "Credito scaduto", null, null, orologio.instant()));
            quanti++;
        }
        return quanti;
    }

    // ── Aiuti ───────────────────────────────────────────────────────────────

    private BigDecimal valore(int crediti) {
        return movimenti.regole().dto().valoreCredito().multiply(BigDecimal.valueOf(crediti)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Il modo scelto, o quello delle regole se non lascia scegliere. TRATTIENI solo per la cauzione. */
    private static String modo(String scelto, String regola, boolean cauzione) {
        if (cauzione && "TRATTIENI".equals(scelto)) return "TRATTIENI";
        if (!"A_SCELTA".equals(regola)) return regola;
        if (!"DENARO".equals(scelto) && !"BORSELLINO".equals(scelto))
            throw new IllegalArgumentException("Scegli come restituire: in denaro o nel borsellino" + (cauzione ? " (o trattenere la cauzione)." : "."));
        return scelto;
    }

    private UUID esce(UUID persona, BigDecimal importo, UUID metodoId, String descrizione, UUID da, LocalDate oggi) {
        MetodoPagamento m = metodoId == null
            ? metodi.findAllByOrderByOrdineAscNomeAsc().stream().filter(x -> x.isAttivo() && !x.isBorsellino() && !x.isDaIncassare()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Scegli con quale metodo restituire i soldi."))
            : metodi.findById(metodoId).orElseThrow(() -> new NonTrovato("Metodo di pagamento non trovato."));
        if (m.isBorsellino()) throw new IllegalArgumentException("Per restituire nel borsellino scegli «nel borsellino», non il metodo.");
        return uscite.saveAndFlush(new Uscita(persona, oggi, importo, m.getId(), descrizione, da, orologio.instant())).getId();
    }

    /** Genitori, figli, fratelli e sorelle: a chi si può trasferire il credito. */
    private Map<UUID, Destinatario> familiari(UUID persona) {
        Map<UUID, Destinatario> m = new LinkedHashMap<>();
        Stream.of(persone.tutoriDi(persona), persone.assistitiDi(persona), persone.fratelliDi(persona)).flatMap(List::stream)
            .filter(f -> !f.personaId().equals(persona))
            .forEach(f -> m.putIfAbsent(f.personaId(), new Destinatario(f.personaId(), (f.nome() + " " + f.cognome()).strip(), f.relazione())));
        return m;
    }
}
