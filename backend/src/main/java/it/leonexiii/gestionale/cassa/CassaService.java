package it.leonexiii.gestionale.cassa;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.pagamenti.Pagamenti;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La cassa (ADR 0018, parte 11). Una sessione conta i soldi passati da una cassa dalla chiusura prima fino alla sua:
 * fondo + contanti entrati − contanti restituiti − versamenti e spese + aggiunte = contanti attesi; alla chiusura si
 * scrivono i contati, si vede la differenza e si dice quanto si lascia per la volta dopo (il resto è «versato»).
 * I pagamenti fatti a cassa chiusa finiscono nella sessione dopo. Con la chiusura per turno chi entra apre la cassa;
 * altrimenti (e senza un fondo da contare) la sessione si apre da sola.
 */
@Service
public class CassaService {

    static final ZoneId FUSO = ZoneId.of("Europe/Rome");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'alle' HH:mm").withZone(FUSO);

    public record CassaDto(UUID id, String nome, boolean attiva, boolean principale, int ordine) {}

    public record ImpostazioniDto(String casse, String fondo, BigDecimal importoFondo, String chiusura) {}

    record NuovaCassa(String nome) {}

    record ModificaCassa(String nome, Boolean attiva, Boolean principale, Integer ordine) {}

    /** Un'entrata o uscita in contanti della sessione; «aCassaChiusa»: registrata prima che la cassa fosse aperta. */
    record VoceContanti(UUID id, String tipo, Instant istante, String metodo, BigDecimal importo, String chi, String descrizione, boolean aCassaChiusa) {}

    record MovimentoDto(UUID id, String tipo, BigDecimal importo, String descrizione, String registratoDa, Instant registratoIl, boolean annullato) {}

    record Conti(BigDecimal fondo, BigDecimal entrati, BigDecimal usciti, BigDecimal versamenti, BigDecimal spese, BigDecimal aggiunte, BigDecimal attesi,
                 List<Sessione.TotaleMetodo> perMetodo, int pagamenti) {}

    /**
     * Una sessione (aperta, chiusa, o quella che si aprirebbe adesso: id vuoto). «contanti» e «movimenti» ci sono solo
     * nel dettaglio.
     */
    record SessioneDto(UUID id, UUID cassaId, String cassa, Instant dal, Instant apertaIl, String apertaDa, boolean esplicita, BigDecimal fondo,
                       BigDecimal fondoAtteso, Instant chiusaIl, String chiusaDa, BigDecimal attesi, BigDecimal contati, BigDecimal differenza,
                       BigDecimal lasciato, BigDecimal versato, String note, Conti conti, List<VoceContanti> contanti, List<MovimentoDto> movimenti) {}

    /** Una cassa adesso: la sessione in corso (o quella che si aprirebbe), l'ultima chiusura e il fondo da cui si parte. */
    record StatoCassa(CassaDto cassa, boolean aperta, SessioneDto corrente, SessioneDto ultimaChiusa, BigDecimal fondoProposto) {}

    record Pagina(ImpostazioniDto impostazioni, List<CassaDto> casse, List<StatoCassa> stati) {}

    record Apertura(BigDecimal fondo) {}

    /** «attesiVisti»: i contanti attesi che si avevano davanti; se nel frattempo sono cambiati, non si chiude. */
    record Chiusura(BigDecimal contati, BigDecimal lasciato, String note, BigDecimal attesiVisti) {}

    record NuovoMovimento(String tipo, BigDecimal importo, String descrizione) {}

    record Motivo(String motivo) {}

    private final CassaRepository casse;
    private final ImpostazioniCassaRepository impostazioni;
    private final SessioneRepository sessioni;
    private final MovimentoCassaRepository movimenti;
    private final Pagamenti pagamenti;
    private final Persone persone;
    private final it.leonexiii.gestionale.societa.Societa societa;
    private final Clock orologio;

    CassaService(CassaRepository casse, ImpostazioniCassaRepository impostazioni, SessioneRepository sessioni, MovimentoCassaRepository movimenti,
                 Pagamenti pagamenti, Persone persone, it.leonexiii.gestionale.societa.Societa societa, Clock orologio) {
        this.casse = casse;
        this.impostazioni = impostazioni;
        this.sessioni = sessioni;
        this.movimenti = movimenti;
        this.pagamenti = pagamenti;
        this.persone = persone;
        this.societa = societa;
        this.orologio = orologio;
    }

    // ── Pagina e impostazioni ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    Pagina pagina() {
        ImpostazioniCassa imp = imp();
        List<Cassa> tutte = casse.findAllByOrderByOrdineAscNomeAsc();
        List<StatoCassa> stati = tutte.stream().filter(c -> c.isAttiva() && (imp.perPostazione() || c.isPrincipale())).map(c -> stato(c, imp, tutte)).toList();
        return new Pagina(imp.dto(), tutte.stream().map(Cassa::dto).toList(), stati);
    }

    @Transactional
    ImpostazioniDto modifica(ImpostazioniDto in) {
        ImpostazioniCassa imp = imp();
        boolean cambiaCasse = !Objects.equals(in.casse(), imp.dto().casse());
        if (cambiaCasse && sessioni.existsByChiusaIlIsNull())
            throw new Conflitto("Prima di passare " + ("UNICA".equals(in.casse()) ? "a una cassa sola" : "a una cassa per postazione") + " chiudi le casse aperte.");
        imp.modifica(in);
        return imp.dto();
    }

    @Transactional
    CassaDto creaCassa(NuovaCassa in) {
        int ordine = casse.findAll().stream().mapToInt(Cassa::getOrdine).max().orElse(0) + 1;
        if (in == null) throw new IllegalArgumentException("Dai un nome alla cassa.");
        controllaNome(in.nome(), null);
        return casse.saveAndFlush(new Cassa(in.nome(), ordine)).dto();
    }

    @Transactional
    CassaDto modificaCassa(UUID id, ModificaCassa in) {
        Cassa c = casse.findById(id).orElseThrow(() -> new NonTrovato("Cassa non trovata."));
        if (in.nome() != null) {
            controllaNome(in.nome(), id);
            c.rinomina(in.nome());
        }
        if (Boolean.TRUE.equals(in.principale()) && !c.isPrincipale()) {
            if (!c.isAttiva() && !Boolean.TRUE.equals(in.attiva())) throw new IllegalArgumentException("Una cassa spenta non può essere la principale.");
            c.accendi(true);
            casse.findByPrincipaleTrue().ifPresent(p -> {
                p.principale(false);
                casse.saveAndFlush(p);
            });
            c.principale(true);
        }
        if (in.attiva() != null && in.attiva() != c.isAttiva()) {
            if (!in.attiva() && sessioni.findByCassaIdAndChiusaIlIsNull(id).isPresent())
                throw new Conflitto("«" + c.getNome() + "» è aperta: chiudila prima di spegnerla.");
            c.accendi(in.attiva());
        }
        if (in.ordine() != null) c.ordine(in.ordine());
        return casse.saveAndFlush(c).dto();
    }

    // ── Aprire, muovere, chiudere ───────────────────────────────────────────

    @Transactional
    SessioneDto apri(UUID cassaId, Apertura in, UUID da) {
        ImpostazioniCassa imp = imp();
        Cassa c = cassaUsabile(cassaId, imp);
        sessioni.findByCassaIdAndChiusaIlIsNull(c.getId()).ifPresent(s -> {
            throw new Conflitto("«" + c.getNome() + "» è già aperta" + (s.getApertaDa() != null && s.isEsplicita() ? " (da " + nome(s.getApertaDa()) + ")" : "") + ".");
        });
        BigDecimal proposto = fondoProposto(c, imp);
        BigDecimal fondo = proposto;
        BigDecimal atteso = null;
        if ("CONTATO".equals(imp.getFondo())) {
            if (in == null || in.fondo() == null) throw new IllegalArgumentException("Conta il fondo cassa e scrivi quanto c'è.");
            fondo = euro(in.fondo(), "il fondo cassa");
            atteso = proposto;
        }
        Sessione s = sessioni.saveAndFlush(new Sessione(c.getId(), inizio(c), orologio.instant(), da, true, fondo, atteso));
        return dettaglio(s, c, imp);
    }

    @Transactional
    SessioneDto movimento(UUID cassaId, NuovoMovimento in, UUID da) {
        ImpostazioniCassa imp = imp();
        Cassa c = cassaUsabile(cassaId, imp);
        MovimentoCassa.Tipo tipo;
        try {
            tipo = MovimentoCassa.Tipo.valueOf(in.tipo());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Scegli se è un versamento, una spesa o un'aggiunta.");
        }
        BigDecimal importo = euro(in.importo(), "l'importo");
        if (importo.signum() == 0) throw new IllegalArgumentException("L'importo deve essere più di zero.");
        String descrizione = in.descrizione() == null || in.descrizione().isBlank()
            ? switch (tipo) { case VERSAMENTO -> "Versamento"; case SPESA -> "Spesa"; case AGGIUNTA -> "Aggiunta"; }
            : in.descrizione().strip();
        if (descrizione.length() > 200) throw new IllegalArgumentException("La descrizione è troppo lunga (al massimo 200 caratteri).");
        Sessione s = inCorso(c, imp, da);
        movimenti.saveAndFlush(new MovimentoCassa(s.getId(), tipo, importo, descrizione, da, orologio.instant()));
        return dettaglio(s, c, imp);
    }

    @Transactional
    SessioneDto annullaMovimento(UUID id) {
        MovimentoCassa m = movimenti.findById(id).orElseThrow(() -> new NonTrovato("Movimento non trovato."));
        Sessione s = sessioni.findById(m.getSessioneId()).orElseThrow();
        if (!s.aperta()) throw new Conflitto("La cassa di quel movimento è già chiusa: riaprila prima di correggere.");
        m.annulla(orologio.instant());
        movimenti.flush();
        return dettaglio(s, casse.findById(s.getCassaId()).orElseThrow(), imp());
    }

    @Transactional
    SessioneDto chiudi(UUID cassaId, Chiusura in, UUID da) {
        ImpostazioniCassa imp = imp();
        Cassa c = cassaUsabile(cassaId, imp);
        if (in == null || in.contati() == null) throw new IllegalArgumentException("Conta i contanti nel cassetto e scrivi quanto c'è.");
        BigDecimal contati = euro(in.contati(), "i contati");
        Sessione s = inCorso(c, imp, da);
        Instant ora = orologio.instant();
        Conti conti = conti(s, c, imp, ora, movimentiDi(s));
        if (in.attesiVisti() != null && conti.attesi().compareTo(in.attesiVisti()) != 0)
            throw new Conflitto("Nel frattempo i contanti attesi sono cambiati (ora " + euroTesto(conti.attesi()) + "): ricontrolla prima di chiudere.");
        BigDecimal lasciato = in.lasciato() != null ? euro(in.lasciato(), "il fondo che lasci") : contati.min(imp.getImportoFondo());
        if (lasciato.compareTo(contati) > 0) throw new IllegalArgumentException("Non puoi lasciare più di quello che hai contato (" + euroTesto(contati) + ").");
        if (in.note() != null && in.note().length() > 500) throw new IllegalArgumentException("La nota è troppo lunga (al massimo 500 caratteri).");
        s.chiudi(ora, da, conti.attesi(), contati, lasciato, in.note(),
            new Sessione.Riepilogo(conti.perMetodo(), conti.pagamenti(), conti.entrati(), conti.usciti(), conti.versamenti(), conti.spese(), conti.aggiunte()));
        sessioni.saveAndFlush(s);
        return dettaglio(s, c, imp);
    }

    /** Riapre l'ultima chiusura di una cassa (es. contati sbagliati), se dopo non si è aperto niente. */
    @Transactional
    SessioneDto riapri(UUID sessioneId, Motivo in, UUID da) {
        if (in == null || in.motivo() == null || in.motivo().isBlank()) throw new IllegalArgumentException("Scrivi perché riapri la cassa.");
        if (in.motivo().strip().length() > 300) throw new IllegalArgumentException("Il motivo è troppo lungo (al massimo 300 caratteri).");
        Sessione s = sessioni.findById(sessioneId).orElseThrow(() -> new NonTrovato("Chiusura non trovata."));
        Cassa c = casse.bloccata(s.getCassaId()).orElseThrow();
        if (s.aperta()) throw new Conflitto("La cassa è già aperta.");
        if (sessioni.findByCassaIdAndChiusaIlIsNull(c.getId()).isPresent())
            throw new Conflitto("«" + c.getNome() + "» è già stata riaperta dopo: si riapre solo l'ultima chiusura, a cassa chiusa.");
        Sessione ultima = sessioni.findFirstByCassaIdAndChiusaIlIsNotNullOrderByChiusaIlDesc(c.getId()).orElseThrow();
        if (!ultima.getId().equals(s.getId())) throw new Conflitto("Si riapre solo l'ultima chiusura di «" + c.getNome() + "».");
        s.riapri(da == null ? null : nome(da), QUANDO.format(orologio.instant()), in.motivo().strip());
        sessioni.saveAndFlush(s);
        return dettaglio(s, c, imp());
    }

    // ── Storico ─────────────────────────────────────────────────────────────

    /** Le chiusure fatte tra «da» e «a» (giorni compresi), di tutte le casse o di una. */
    @Transactional(readOnly = true)
    List<SessioneDto> storico(LocalDate da, LocalDate a, UUID cassaId) {
        LocalDate oggi = LocalDate.now(orologio.withZone(FUSO));
        LocalDate inizio = da != null ? da : oggi, fine = a != null ? a : inizio;
        if (fine.isBefore(inizio)) throw new IllegalArgumentException("La data di fine viene prima di quella di inizio.");
        if (ChronoUnit.DAYS.between(inizio, fine) > 366) throw new IllegalArgumentException("Al massimo un anno alla volta.");
        Map<UUID, Cassa> tutte = casse.findAll().stream().collect(Collectors.toMap(Cassa::getId, x -> x));
        var lista = sessioni.findByChiusaIlGreaterThanEqualAndChiusaIlLessThanOrderByChiusaIlDesc(inizio.atStartOfDay(FUSO).toInstant(),
            fine.plusDays(1).atStartOfDay(FUSO).toInstant()).stream().filter(s -> cassaId == null || cassaId.equals(s.getCassaId())).toList();
        Map<UUID, String> nomi = nomi(lista.stream().flatMap(s -> Stream.of(s.getApertaDa(), s.getChiusaDa())).toList());
        return lista.stream().map(s -> dto(s, tutte.get(s.getCassaId()), contiChiusi(s), nomi, null, null)).toList();
    }

    @Transactional(readOnly = true)
    SessioneDto dettaglio(UUID id) {
        Sessione s = sessioni.findById(id).orElseThrow(() -> new NonTrovato("Chiusura non trovata."));
        return dettaglio(s, casse.findById(s.getCassaId()).orElseThrow(), imp());
    }

    @Transactional(readOnly = true)
    byte[] pdf(UUID id) {
        SessioneDto s = dettaglio(id);
        if (s.chiusaIl() == null) throw new Conflitto("La cassa è ancora aperta: il riepilogo si stampa dopo la chiusura.");
        return PdfChiusura.crea(s, societa.dati().nome());
    }

    // ── Conti ───────────────────────────────────────────────────────────────

    private StatoCassa stato(Cassa c, ImpostazioniCassa imp, List<Cassa> tutte) {
        var aperta = sessioni.findByCassaIdAndChiusaIlIsNull(c.getId());
        var ultima = sessioni.findFirstByCassaIdAndChiusaIlIsNotNullOrderByChiusaIlDesc(c.getId());
        BigDecimal proposto = fondoProposto(c, imp);
        SessioneDto corrente;
        if (aperta.isPresent()) corrente = dettaglio(aperta.get(), c, imp);
        else {
            // quella che si aprirebbe adesso: per vedere i conti anche prima di aprire
            Instant ora = orologio.instant();
            Sessione virtuale = new Sessione(c.getId(), inizio(c), ora, null, false, proposto, null);
            corrente = dettaglioVirtuale(virtuale, c, imp, ora);
        }
        Map<UUID, String> nomi = ultima.map(s -> nomi(Stream.of(s.getApertaDa(), s.getChiusaDa()).toList())).orElse(Map.of());
        return new StatoCassa(c.dto(), aperta.isPresent(), corrente, ultima.map(s -> dto(s, c, contiChiusi(s), nomi, null, null)).orElse(null), proposto);
    }

    private SessioneDto dettaglio(Sessione s, Cassa c, ImpostazioniCassa imp) {
        List<MovimentoCassa> mov = movimentiDi(s);
        Instant fine = s.aperta() ? null : s.getChiusaIl();
        List<Pagamenti.VoceCassa> voci = vociDi(s.getDal(), fine, c, imp);
        Conti conti = s.aperta() ? conti(s, voci, mov) : contiChiusi(s);
        Map<UUID, String> nomi = nomi(Stream.concat(Stream.of(s.getApertaDa(), s.getChiusaDa()), mov.stream().map(MovimentoCassa::getRegistratoDa)).toList());
        return dto(s, c, conti, nomi, contanti(s, voci, imp), mov.stream().map(m -> new MovimentoDto(m.getId(), m.getTipo().name(), m.getImporto(),
            m.getDescrizione(), m.getRegistratoDa() == null ? null : nomi.get(m.getRegistratoDa()), m.getRegistratoIl(), m.getAnnullatoIl() != null)).toList());
    }

    private SessioneDto dettaglioVirtuale(Sessione s, Cassa c, ImpostazioniCassa imp, Instant ora) {
        List<Pagamenti.VoceCassa> voci = vociDi(s.getDal(), null, c, imp);
        SessioneDto d = dto(s, c, conti(s, voci, List.of()), Map.of(), contanti(s, voci, imp), List.of());
        return new SessioneDto(null, d.cassaId(), d.cassa(), d.dal(), null, null, false, d.fondo(), null, null, null, null, null, null, null, null, null,
            d.conti(), d.contanti(), d.movimenti());
    }

    private Conti conti(Sessione s, Cassa c, ImpostazioniCassa imp, Instant fine, List<MovimentoCassa> mov) {
        return conti(s, vociDi(s.getDal(), fine, c, imp), mov);
    }

    private static Conti conti(Sessione s, List<Pagamenti.VoceCassa> voci, List<MovimentoCassa> mov) {
        Map<String, Sessione.TotaleMetodo> per = new LinkedHashMap<>();
        BigDecimal entrati = ZERO, usciti = ZERO;
        Set<UUID> pagati = new HashSet<>();
        for (var v : voci) {
            boolean entrata = "ENTRATA".equals(v.tipo());
            if (entrata) pagati.add(v.id());
            per.merge(v.metodo(), new Sessione.TotaleMetodo(v.metodo(), v.contanti(), entrata ? v.importo() : ZERO, entrata ? ZERO : v.importo()),
                (x, y) -> new Sessione.TotaleMetodo(x.metodo(), x.contanti(), x.entrate().add(y.entrate()), x.uscite().add(y.uscite())));
            if (!v.contanti()) continue;
            if (entrata) entrati = entrati.add(v.importo());
            else usciti = usciti.add(v.importo());
        }
        BigDecimal versamenti = somma(mov, MovimentoCassa.Tipo.VERSAMENTO), spese = somma(mov, MovimentoCassa.Tipo.SPESA), aggiunte = somma(mov, MovimentoCassa.Tipo.AGGIUNTA);
        BigDecimal attesi = s.getFondo().add(entrati).subtract(usciti).subtract(versamenti).subtract(spese).add(aggiunte);
        return new Conti(s.getFondo(), entrati, usciti, versamenti, spese, aggiunte, attesi, List.copyOf(per.values()), pagati.size());
    }

    private static Conti contiChiusi(Sessione s) {
        var r = s.getRiepilogo();
        if (r == null) return new Conti(s.getFondo(), ZERO, ZERO, ZERO, ZERO, ZERO, s.getAttesi(), List.of(), 0);
        return new Conti(s.getFondo(), r.entrati(), r.usciti(), r.versamenti(), r.spese(), r.aggiunte(), s.getAttesi(), r.perMetodo(), r.pagamenti());
    }

    private static BigDecimal somma(List<MovimentoCassa> mov, MovimentoCassa.Tipo tipo) {
        return mov.stream().filter(m -> m.getAnnullatoIl() == null && m.getTipo() == tipo).map(MovimentoCassa::getImporto).reduce(ZERO, BigDecimal::add);
    }

    /** Le entrate e uscite di quella cassa nella finestra: con una cassa sola tutte; se no le sue (e alla principale quelle senza cassa). */
    private List<Pagamenti.VoceCassa> vociDi(Instant dal, Instant fine, Cassa c, ImpostazioniCassa imp) {
        var voci = pagamenti.vociCassa(dal, fine);
        if (!imp.perPostazione()) return voci;
        Set<UUID> conosciute = casse.findAll().stream().map(Cassa::getId).collect(Collectors.toSet());
        return voci.stream().filter(v -> c.getId().equals(v.cassaId()) || (c.isPrincipale() && (v.cassaId() == null || !conosciute.contains(v.cassaId())))).toList();
    }

    /** Solo i contanti, per l'elenco: segnati quelli arrivati a cassa chiusa (prima dell'apertura, o un altro giorno). */
    private static List<VoceContanti> contanti(Sessione s, List<Pagamenti.VoceCassa> voci, ImpostazioniCassa imp) {
        Instant soglia = s.isEsplicita() ? s.getApertaIl()
            : LocalDate.ofInstant(s.getChiusaIl() != null ? s.getChiusaIl() : s.getApertaIl(), FUSO).atStartOfDay(FUSO).toInstant();
        return voci.stream().filter(Pagamenti.VoceCassa::contanti).map(v -> new VoceContanti(v.id(), v.tipo(), v.istante(), v.metodo(), v.importo(), v.chi(),
            v.descrizione(), v.istante().isBefore(soglia))).sorted(Comparator.comparing(VoceContanti::istante).reversed()).toList();
    }

    private SessioneDto dto(Sessione s, Cassa c, Conti conti, Map<UUID, String> nomi, List<VoceContanti> contanti, List<MovimentoDto> mov) {
        return new SessioneDto(s.getId(), s.getCassaId(), c == null ? "?" : c.getNome(), s.getDal(), s.getApertaIl(),
            s.getApertaDa() == null ? null : nomi.get(s.getApertaDa()), s.isEsplicita(), s.getFondo(), s.getFondoAtteso(), s.getChiusaIl(),
            s.getChiusaDa() == null ? null : nomi.get(s.getChiusaDa()), s.getAttesi(), s.getContati(), s.getDifferenza(), s.getLasciato(), s.getVersato(),
            s.getNote(), conti, contanti, mov);
    }

    // ── Aiuti ───────────────────────────────────────────────────────────────

    private ImpostazioniCassa imp() {
        return impostazioni.findById((short) 1).orElseThrow();
    }

    /** La cassa, bloccata, se si può usare: accesa e, con una cassa sola, la principale. */
    private Cassa cassaUsabile(UUID id, ImpostazioniCassa imp) {
        Cassa c = casse.bloccata(id).orElseThrow(() -> new NonTrovato("Cassa non trovata."));
        if (!c.isAttiva()) throw new IllegalArgumentException("«" + c.getNome() + "» è spenta.");
        if (!imp.perPostazione() && !c.isPrincipale()) throw new IllegalArgumentException("C'è una cassa sola: «" + c.getNome() + "» non si usa.");
        return c;
    }

    /** La sessione aperta, o una nuova che si apre da sola (non con il fondo da contare: lì qualcuno deve aprire). */
    private Sessione inCorso(Cassa c, ImpostazioniCassa imp, UUID da) {
        var aperta = sessioni.findByCassaIdAndChiusaIlIsNull(c.getId());
        if (aperta.isPresent()) return aperta.get();
        if ("CONTATO".equals(imp.getFondo())) throw new Conflitto("Prima apri «" + c.getNome() + "» contando il fondo cassa.");
        return sessioni.saveAndFlush(new Sessione(c.getId(), inizio(c), orologio.instant(), da, false, fondoProposto(c, imp), null));
    }

    /** Da quando conta la sessione (escluso): dalla chiusura prima, o (la prima volta) dall'inizio del giorno. */
    private Instant inizio(Cassa c) {
        return sessioni.findFirstByCassaIdAndChiusaIlIsNotNullOrderByChiusaIlDesc(c.getId()).map(Sessione::getChiusaIl)
            .orElseGet(() -> LocalDate.now(orologio.withZone(FUSO)).atStartOfDay(FUSO).toInstant().minusNanos(1000));
    }

    /** Il fondo da cui si parte: quello fisso, o quello lasciato alla chiusura prima (la prima volta, quello delle impostazioni). */
    private BigDecimal fondoProposto(Cassa c, ImpostazioniCassa imp) {
        if ("FISSO".equals(imp.getFondo())) return imp.getImportoFondo();
        return sessioni.findFirstByCassaIdAndChiusaIlIsNotNullOrderByChiusaIlDesc(c.getId()).map(Sessione::getLasciato).orElse(imp.getImportoFondo());
    }

    private List<MovimentoCassa> movimentiDi(Sessione s) {
        return movimenti.findBySessioneIdInOrderByRegistratoIl(List.of(s.getId()));
    }

    private void controllaNome(String nome, UUID tranne) {
        if (nome == null) return;
        boolean doppio = casse.findAll().stream().anyMatch(x -> !x.getId().equals(tranne) && x.getNome().equalsIgnoreCase(nome.strip()));
        if (doppio) throw new Conflitto("C'è già una cassa che si chiama «" + nome.strip() + "».");
    }

    private Map<UUID, String> nomi(List<UUID> ids) {
        List<UUID> veri = ids.stream().filter(Objects::nonNull).distinct().toList();
        return veri.isEmpty() ? Map.of() : persone.nomiCompleti(veri);
    }

    private String nome(UUID id) {
        return persone.nomiCompleti(List.of(id)).getOrDefault(id, "qualcuno");
    }

    /** Un importo in euro valido: non negativo, al massimo due decimali e sotto il milione. */
    static BigDecimal euro(BigDecimal v, String cosa) {
        if (v == null || v.signum() < 0 || v.stripTrailingZeros().scale() > 2 || v.compareTo(new BigDecimal("1000000")) >= 0)
            throw new IllegalArgumentException("Scrivi " + cosa + " in euro (non negativo, al massimo due decimali).");
        return v.setScale(2);
    }

    static String euroTesto(BigDecimal v) {
        return PdfChiusura.euro(v);
    }
}
