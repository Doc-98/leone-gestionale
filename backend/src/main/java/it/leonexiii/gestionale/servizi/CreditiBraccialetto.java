package it.leonexiii.gestionale.servizi;

import it.leonexiii.gestionale.accessi.Braccialetti;
import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.pagamenti.Borsellino;
import it.leonexiii.gestionale.pagamenti.Pagamenti;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * I crediti di phon e docce scritti sul braccialetto (ADR 0025), per le macchine non collegate alla rete: le macchine
 * leggono e scalano i crediti nella memoria del braccialetto e non dicono niente al gestionale. Il gestionale scrive il
 * saldo alla ricarica e ne conosce solo l'ultimo valore visto; a ogni lettura i crediti spariti diventano «consumati
 * alle macchine» nel registro della persona. I byte li interpreta il {@link FormatoBraccialetto}.
 */
@Service
class CreditiBraccialetto {

    record Stato(boolean sulBraccialetto, String formato, String nomeFormato, boolean pronto, String nota, FormatoBraccialetto.Accesso accesso) {}

    /** Una ricarica scritta (o forse scritta) sul braccialetto ma non ancora registrata. */
    record InSospeso(UUID operazione, int creditiPrima, int creditiDopo, BigDecimal importo, Instant creataIl) {}

    /** L'ultimo saldo visto su un braccialetto della persona (null = mai visto). */
    record SaldoDto(UUID braccialettoId, String codice, boolean attivo, Integer crediti, String fonte, Instant aggiornatoIl, InSospeso inSospeso) {}

    /** La memoria letta dal lettore, oppure i crediti scritti a mano (quelli che mostra il display della macchina). */
    record Lettura(MemoriaBraccialetto memoria, Integer crediti) {}

    record EsitoLettura(int crediti, Integer prima, int consumati, InSospeso inSospeso) {}

    record NuovaRicarica(MemoriaBraccialetto memoria, BigDecimal importo) {}

    record RicaricaPreparata(UUID operazione, int creditiPrima, int creditiDopo, int comprati, Map<Integer, String> scrivi) {}

    record Conferma(MemoriaBraccialetto memoria, UUID paganteId, List<Pagamenti.MetodoIn> metodi, String note) {}

    /** I blocchi da scrivere per azzerare un braccialetto che torna alla società. */
    record Azzeramento(int creditiPrima, Map<Integer, String> scrivi) {}

    record UsoMacchina(UUID servizioId, MemoriaBraccialetto memoria) {}

    /** Cosa farebbe una macchina non collegata: parte o no, e i blocchi che scriverebbe (niente arriva al gestionale). */
    record EsitoMacchina(boolean via, String messaggio, int crediti, int creditiUsati, Map<Integer, String> scrivi) {}

    private final FormatoBraccialetto formato;
    private final SaldoBraccialettoRepository saldi;
    private final ScritturaBraccialettoRepository scritture;
    private final ServizioRepository servizi;
    private final Braccialetti braccialetti;
    private final Borsellino borsellino;
    private final Clock orologio;

    CreditiBraccialetto(FormatoBraccialetto formato, SaldoBraccialettoRepository saldi, ScritturaBraccialettoRepository scritture,
                        ServizioRepository servizi, Braccialetti braccialetti, Borsellino borsellino, Clock orologio) {
        this.formato = formato;
        this.saldi = saldi;
        this.scritture = scritture;
        this.servizi = servizi;
        this.braccialetti = braccialetti;
        this.borsellino = borsellino;
        this.orologio = orologio;
    }

    @Transactional(readOnly = true)
    Stato stato() {
        return new Stato(borsellino.regole().sulBraccialetto(), formato.codice(), formato.nome(), formato.pronto(), formato.nota(),
            formato.pronto() ? formato.accesso() : null);
    }

    @Transactional(readOnly = true)
    List<SaldoDto> saldi(UUID personaId) {
        return braccialetti.diPersona(personaId).stream().map(t -> {
            var s = saldi.findById(t.braccialettoId()).orElse(null);
            return new SaldoDto(t.braccialettoId(), t.codice(), t.attivo(), s == null ? null : s.getCrediti(), s == null ? null : s.getFonte(),
                s == null ? null : s.getAggiornatoIl(), inSospeso(t.braccialettoId()));
        }).toList();
    }

    /**
     * Una lettura al banco (dal lettore o dal display della macchina): aggiorna l'ultimo saldo visto e, per un
     * braccialetto attivo, segna nel registro della persona i crediti consumati nel frattempo.
     */
    @Transactional
    EsitoLettura leggi(UUID da, UUID braccialettoId, Lettura in) {
        richiediSulBraccialetto();
        var t = titolare(braccialettoId);
        int crediti;
        SaldoBraccialetto.Fonte fonte;
        if (in.memoria() != null) {
            richiediPronto();
            controllaSeriale(t, in.memoria());
            crediti = formato.crediti(in.memoria());
            fonte = SaldoBraccialetto.Fonte.LETTURA;
        } else {
            if (in.crediti() == null || in.crediti() < 0) throw new IllegalArgumentException("Scrivi i crediti che mostra la macchina (zero o più).");
            crediti = in.crediti();
            fonte = SaldoBraccialetto.Fonte.A_MANO;
        }
        InSospeso sospesa = inSospeso(braccialettoId);
        // con una ricarica in sospeso non si tocca il registro: prima la si registra o la si annulla
        if (sospesa != null) return new EsitoLettura(crediti, ultimo(braccialettoId), 0, sospesa);
        Integer prima = ultimo(braccialettoId);
        int consumati = aggiorna(t, crediti, fonte, da);
        return new EsitoLettura(crediti, prima, consumati, null);
    }

    /** Primo tempo della ricarica: dalla memoria letta, i blocchi da scrivere con il saldo nuovo. Niente è ancora pagato. */
    @Transactional
    RicaricaPreparata preparaRicarica(UUID da, UUID braccialettoId, NuovaRicarica in) {
        richiediSulBraccialetto();
        richiediPronto();
        var t = titolare(braccialettoId);
        if (!t.attivo()) throw new Conflitto("Il braccialetto non è attivo: si ricarica solo un braccialetto in uso.");
        if (in.memoria() == null) throw new IllegalArgumentException("Prima leggi il braccialetto sul lettore.");
        controllaSeriale(t, in.memoria());
        if (scritture.aperta(braccialettoId).isPresent())
            throw new Conflitto("Su questo braccialetto c'è una ricarica in sospeso: registrala o annullala prima di farne un'altra.");
        int prima = formato.crediti(in.memoria());
        aggiorna(t, prima, SaldoBraccialetto.Fonte.LETTURA, da);
        int comprati = borsellino.creditiPer(in.importo());
        int dopo = prima + comprati;
        Map<Integer, String> blocchi = formato.scrivi(in.memoria(), dopo);
        var s = scritture.saveAndFlush(new ScritturaBraccialetto(braccialettoId, t.personaId(), prima, dopo, in.importo(), orologio.instant(), da));
        return new RicaricaPreparata(s.getId(), prima, dopo, comprati, blocchi);
    }

    /** Secondo tempo: il braccialetto riletto dice il saldo nuovo, quindi si registrano addebito, pagamento e crediti. */
    @Transactional
    UUID confermaRicarica(UUID da, UUID operazione, Conferma in, boolean flessibile) {
        var s = scritture.findById(operazione).orElseThrow(() -> new NonTrovato("Ricarica non trovata."));
        if (!s.aperta()) throw new Conflitto("Questa ricarica è già chiusa.");
        var t = titolare(s.getBraccialettoId());
        if (in.memoria() == null) throw new IllegalArgumentException("Rileggi il braccialetto per controllare che la ricarica sia scritta.");
        controllaSeriale(t, in.memoria());
        int letti = formato.crediti(in.memoria());
        if (letti != s.getCreditiDopo())
            throw new Conflitto("Sul braccialetto ci sono " + letti + " crediti, non " + s.getCreditiDopo() + ": la scrittura non è riuscita. "
                + "Riprova a scrivere oppure annulla la ricarica.");
        UUID pagamento = borsellino.ricaricaScritta(s.getPersonaId(), in.paganteId(), s.getImporto(), in.metodi(), in.note(), s.getBraccialettoId(),
            da, flessibile);
        s.conferma(pagamento, orologio.instant());
        salva(s.getBraccialettoId(), letti, SaldoBraccialetto.Fonte.SCRITTURA, da);
        return pagamento;
    }

    /**
     * La ricarica non si fa (il braccialetto non è stato scritto). Se riletto risulta già scritto, non si annulla: i
     * crediti ci sono, va registrato il pagamento (o riscritto il saldo di prima).
     */
    @Transactional
    void annullaRicarica(UUID operazione, MemoriaBraccialetto riletta) {
        var s = scritture.findById(operazione).orElseThrow(() -> new NonTrovato("Ricarica non trovata."));
        if (riletta != null && formato.pronto()) {
            controllaSeriale(titolare(s.getBraccialettoId()), riletta);
            if (formato.crediti(riletta) == s.getCreditiDopo() && s.getCreditiDopo() != s.getCreditiPrima())
                throw new Conflitto("I crediti nuovi sono già sul braccialetto: registra il pagamento invece di annullare.");
        }
        s.annulla(orologio.instant());
    }

    /**
     * Un braccialetto che torna alla società (riconsegnato) si azzera, perché chi lo riceve dopo non trovi i crediti di
     * un altro. I crediti sono già stati restituiti con la riconsegna: qui si aggiorna solo l'ultimo saldo visto.
     */
    @Transactional
    Azzeramento azzera(UUID da, UUID braccialettoId, MemoriaBraccialetto memoria) {
        richiediSulBraccialetto();
        richiediPronto();
        var t = titolare(braccialettoId);
        if (t.attivo()) throw new Conflitto("Si azzera solo un braccialetto riconsegnato: questo è ancora in uso.");
        if (memoria == null) throw new IllegalArgumentException("Prima leggi il braccialetto sul lettore.");
        controllaSeriale(t, memoria);
        return new Azzeramento(formato.crediti(memoria), formato.scrivi(memoria, 0));
    }

    /**
     * Il simulatore di una macchina non collegata: legge la memoria, scala i crediti del servizio e restituisce i blocchi
     * da scrivere. Come la macchina vera, non registra niente nel gestionale (e non conta gli usi gratuiti).
     */
    @Transactional(readOnly = true)
    EsitoMacchina simulaMacchina(UsoMacchina in) {
        richiediPronto();
        var servizio = servizi.findById(in.servizioId()).orElseThrow(() -> new NonTrovato("Servizio non trovato."));
        if (in.memoria() == null) throw new IllegalArgumentException("Appoggia un braccialetto sul lettore.");
        int crediti = formato.crediti(in.memoria());
        if (!servizio.isAttivo()) return new EsitoMacchina(false, "Servizio spento", crediti, 0, Map.of());
        if (crediti < servizio.getCrediti()) return new EsitoMacchina(false, "Crediti finiti", crediti, 0, Map.of());
        int resto = crediti - servizio.getCrediti();
        return new EsitoMacchina(true, "Restano " + resto + (resto == 1 ? " credito" : " crediti"), resto, servizio.getCrediti(), formato.scrivi(in.memoria(), resto));
    }

    // ── interni ──

    /** Aggiorna l'ultimo saldo visto e allinea il registro della persona (solo per un braccialetto attivo). */
    private int aggiorna(Braccialetti.Titolare t, int crediti, SaldoBraccialetto.Fonte fonte, UUID da) {
        Integer prima = ultimo(t.braccialettoId());
        int consumati = 0;
        if (t.attivo()) {
            // la prima volta che lo si vede, il riferimento è quanto dice il registro (se è l'unico braccialetto attivo)
            int riferimento = prima != null ? prima
                : braccialetti.diPersona(t.personaId()).stream().filter(Braccialetti.Titolare::attivo).count() == 1 ? borsellino.saldo(t.personaId()).crediti() : 0;
            int differenza = crediti - riferimento;
            String dove = fonte == SaldoBraccialetto.Fonte.A_MANO ? "dal display della macchina" : "letti sul braccialetto";
            if (prima == null && differenza != 0) {
                // la prima volta non si sa da quando: il registro si allinea senza parlare di consumi
                borsellino.allineaCrediti(t.personaId(), differenza, "Prima lettura del braccialetto: " + crediti + " crediti (" + dove + "), il registro si allinea",
                    t.braccialettoId(), da);
            } else if (differenza < 0) {
                consumati = -differenza;
                borsellino.allineaCrediti(t.personaId(), differenza, "Consumati alle macchine: " + consumati + " crediti (" + dove + ")", t.braccialettoId(), da);
            } else if (differenza > 0) {
                borsellino.allineaCrediti(t.personaId(), differenza, "Trovati sul braccialetto: " + differenza + " crediti in più (" + dove + ")",
                    t.braccialettoId(), da);
            }
        }
        salva(t.braccialettoId(), crediti, fonte, da);
        return consumati;
    }

    private void salva(UUID braccialettoId, int crediti, SaldoBraccialetto.Fonte fonte, UUID da) {
        var s = saldi.findById(braccialettoId).orElseGet(() -> new SaldoBraccialetto(braccialettoId));
        s.aggiorna(crediti, fonte, orologio.instant(), da);
        saldi.save(s);
    }

    private Integer ultimo(UUID braccialettoId) {
        return saldi.findById(braccialettoId).map(SaldoBraccialetto::getCrediti).orElse(null);
    }

    private InSospeso inSospeso(UUID braccialettoId) {
        return scritture.aperta(braccialettoId)
            .map(s -> new InSospeso(s.getId(), s.getCreditiPrima(), s.getCreditiDopo(), s.getImporto(), s.getCreataIl())).orElse(null);
    }

    private Braccialetti.Titolare titolare(UUID braccialettoId) {
        return braccialetti.perId(braccialettoId).orElseThrow(() -> new NonTrovato("Braccialetto non trovato."));
    }

    /** Il braccialetto sul lettore deve essere quello scelto, non un altro appoggiato per sbaglio. */
    private void controllaSeriale(Braccialetti.Titolare t, MemoriaBraccialetto m) {
        if (!braccialetti.normalizza(m.uid()).equals(t.codice()))
            throw new Conflitto("Sul lettore c'è un altro braccialetto (" + m.uid() + "), non " + t.codice() + ".");
    }

    private void richiediSulBraccialetto() {
        if (!borsellino.regole().sulBraccialetto())
            throw new Conflitto("I crediti stanno nel gestionale (macchine collegate): non c'è niente da leggere sul braccialetto.");
    }

    private void richiediPronto() {
        if (!formato.pronto()) throw new Conflitto(formato.nota());
    }
}
