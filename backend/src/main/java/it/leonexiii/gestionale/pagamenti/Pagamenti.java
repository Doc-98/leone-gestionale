package it.leonexiii.gestionale.pagamenti;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Addebiti e pagamenti. Gli altri moduli creano gli addebiti (vendita di un abbonamento, tesseramento), chiedono
 * quanto è stato pagato e chiudono gli addebiti (recesso, annullamento); il resto lo fa la segreteria dalla scheda.
 */
@Service
public class Pagamenti {

    public enum Origine { ABBONAMENTO, TESSERAMENTO, ALTRO, RIMBORSO, CAUZIONE, RICARICA }

    /** Un addebito da creare; importo negativo = credito. Per le rate: rata «numero» di «rate». */
    public record NuovoAddebito(UUID personaId, String descrizione, BigDecimal importo, LocalDate scadenza, Origine origine, UUID riferimentoId,
                                UUID offertaId, Integer rata, Integer rate, String note) {}

    /** Evento: gli addebiti di un tesseramento (o altro riferimento) risultano tutti pagati il «saldatoIl», o non più (null). */
    public record Saldato(Origine origine, UUID riferimentoId, LocalDate saldatoIl) {}

    /**
     * Quanto una persona deve di già scaduto (per «chi non paga», ADR 0018 parte 6): il totale ancora da pagare degli
     * addebiti scaduti, la scadenza più vecchia, l'addebito più vecchio (cambia quando lo si paga) e quante voci sono.
     */
    public record Arretrato(UUID personaId, BigDecimal importo, LocalDate primaScadenza, UUID primoAddebitoId, int voci) {
        /** Giorni passati dalla scadenza più vecchia. */
        public long giorniDi(LocalDate oggi) {
            return java.time.temporal.ChronoUnit.DAYS.between(primaScadenza, oggi);
        }
    }

    /** Quello che serve per fare la ricevuta di un pagamento (modulo ricevute): chi paga, quando, come, cosa. */
    public record PerRicevuta(UUID id, UUID paganteId, LocalDate data, BigDecimal importo, boolean attivo, List<String> metodi, List<VoceRicevuta> voci) {}

    /** Una riga della ricevuta: cosa si è pagato, per chi (il beneficiario) e quanto. */
    public record VoceRicevuta(UUID addebitoId, UUID personaId, String descrizione, BigDecimal importo, Origine origine) {}

    /** Un pagamento è stato annullato (le sue ricevute si annullano con lui). */
    public record PagamentoAnnullato(UUID pagamentoId, String motivo, UUID da) {}

    public record Metodo(UUID id, String nome, boolean daIncassare, boolean contanti, boolean attivo, int ordine, boolean borsellino) {}

    /**
     * Un'entrata o un'uscita vista dalla cassa (modulo cassa): un metodo usato in un pagamento (tipo ENTRATA) o dei soldi
     * restituiti (USCITA), con la cassa da cui sono passati (vuoto = la principale) e se il metodo finisce nel cassetto.
     */
    public record VoceCassa(UUID id, String tipo, Instant istante, UUID cassaId, UUID metodoId, String metodo, boolean contanti, boolean daIncassare,
                            BigDecimal importo, String chi, String descrizione) {}

    record PagamentoDiAddebito(UUID pagamentoId, LocalDate data, BigDecimal importo) {}

    record AddebitoDto(UUID id, UUID personaId, String persona, String descrizione, BigDecimal importo, BigDecimal pagato, BigDecimal residuo,
                       LocalDate scadenza, String stato, Origine origine, UUID riferimentoId, Integer rata, Integer rate, String note,
                       Instant annullatoIl, String motivoAnnullamento, List<PagamentoDiAddebito> pagamenti) {}

    record MetodoUsato(UUID id, UUID metodoId, String metodo, BigDecimal importo, boolean daIncassare, boolean contanti, LocalDate incassatoIl, String riferimento) {}

    record VocePagata(UUID addebitoId, String descrizione, UUID personaId, String persona, BigDecimal importo) {}

    record PagamentoDto(UUID id, LocalDate data, BigDecimal importo, UUID paganteId, String pagante, List<MetodoUsato> metodi, List<VocePagata> voci,
                        String note, String registratoDa, Instant registratoIl, Instant annullatoIl, String motivoAnnullamento) {}

    record Totali(BigDecimal daPagare, BigDecimal scaduto, BigDecimal credito, BigDecimal daIncassare) {}

    /** Un familiare con quello che ha da pagare (per pagare tutto insieme). */
    record Familiare(UUID personaId, String nome, String relazione, Totali totali, List<AddebitoDto> aperti) {}

    record Pagante(UUID personaId, String nome, String relazione) {}

    /** Il borsellino di chi può pagare: euro disponibili (per pagare con il metodo «Borsellino») e crediti dei servizi. */
    record BorsellinoDi(UUID personaId, BigDecimal euro, int crediti) {}

    record Conto(UUID personaId, Totali totali, List<AddebitoDto> addebiti, List<PagamentoDto> pagamenti, List<Familiare> famiglia,
                 List<Pagante> paganti, List<BorsellinoDi> borsellini) {}

    record NuovoAddebitoManuale(UUID personaId, String descrizione, BigDecimal importo, LocalDate scadenza, UUID offertaId, String note) {}

    public record MetodoIn(UUID metodoId, BigDecimal importo, String riferimento) {}

    /** Gli addebiti nell'ordine in cui pagarli (di base: prima quelli che scadono prima); importo vuoto = somma dei metodi. */
    /**
     * «separati»: con addebiti di più persone, un pagamento per persona invece di uno unico (conta solo se la società
     * lascia scegliere: impostazione «pagamenti di famiglia»).
     */
    record NuovoPagamento(UUID paganteId, LocalDate data, BigDecimal importo, List<UUID> addebiti, List<MetodoIn> metodi, String note, Boolean separati) {}

    record Motivo(String motivo) {}

    record Incasso(LocalDate il) {}

    /** Per metodo: incassato, di cui da incassare, e uscito (soldi restituiti). */
    record TotaleMetodo(UUID metodoId, String metodo, BigDecimal importo, BigDecimal daIncassare, BigDecimal uscite) {}

    record UscitaDto(UUID id, LocalDate data, UUID personaId, String persona, String metodo, boolean contanti, BigDecimal importo, String descrizione, String registratoDa, Instant registratoIl) {}

    record Incassi(LocalDate da, LocalDate a, BigDecimal totale, List<TotaleMetodo> perMetodo, List<PagamentoDto> pagamenti, List<UscitaDto> uscite) {}

    record DaIncassare(UUID id, UUID pagamentoId, LocalDate data, UUID paganteId, String pagante, String metodo, BigDecimal importo, String riferimento,
                       List<String> per) {}

    record NuovoMetodo(String nome, Boolean daIncassare, Boolean contanti, Boolean attivo, Integer ordine) {}

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AddebitoRepository addebiti;
    private final PagamentoRepository pagamenti;
    private final MetodoRepository metodi;
    private final Persone persone;
    private final ApplicationEventPublisher eventi;
    private final Clock orologio;
    private final it.leonexiii.gestionale.societa.Societa societa;
    private final Movimenti movimenti;
    private final UscitaRepository uscite;

    Pagamenti(AddebitoRepository addebiti, PagamentoRepository pagamenti, MetodoRepository metodi, Persone persone,
              ApplicationEventPublisher eventi, Clock orologio, it.leonexiii.gestionale.societa.Societa societa, Movimenti movimenti,
              UscitaRepository uscite) {
        this.movimenti = movimenti;
        this.uscite = uscite;
        this.societa = societa;
        this.addebiti = addebiti;
        this.pagamenti = pagamenti;
        this.metodi = metodi;
        this.persone = persone;
        this.eventi = eventi;
        this.orologio = orologio;
    }

    // ── Per gli altri moduli ────────────────────────────────────────────────

    @Transactional
    public List<UUID> addebita(List<NuovoAddebito> nuovi, UUID da) {
        Instant ora = orologio.instant();
        return addebiti.saveAllAndFlush(nuovi.stream().map(n -> new Addebito(n, da, ora)).toList()).stream().map(Addebito::getId).toList();
    }

    /** Gli arretrati di tutti, per persona: solo chi ha qualcosa di scaduto e non ancora pagato. */
    @Transactional(readOnly = true)
    public Map<UUID, Arretrato> arretrati(LocalDate oggi) {
        return arretratiDi(addebiti.scadutiPrima(oggi));
    }

    /** Gli arretrati di una persona, se ne ha. */
    @Transactional(readOnly = true)
    public Optional<Arretrato> arretratoDi(UUID personaId, LocalDate oggi) {
        return Optional.ofNullable(arretratiDi(addebiti.scadutiDiPrima(personaId, oggi)).get(personaId));
    }

    private Map<UUID, Arretrato> arretratiDi(List<Addebito> scaduti) {
        Map<UUID, BigDecimal> pagato = pagatoPerAddebito(scaduti.stream().map(Addebito::getId).toList());
        Map<UUID, Arretrato> per = new HashMap<>();
        scaduti.stream().sorted(Comparator.comparing(Addebito::getScadenza).thenComparing(Addebito::getCreatoIl)).forEach(a -> {
            BigDecimal resta = a.getImporto().subtract(pagato.getOrDefault(a.getId(), ZERO));
            if (resta.signum() <= 0) return;
            per.merge(a.getPersonaId(), new Arretrato(a.getPersonaId(), resta, a.getScadenza(), a.getId(), 1),
                (prima, nuovo) -> new Arretrato(prima.personaId(), prima.importo().add(resta), prima.primaScadenza(), prima.primoAddebitoId(), prima.voci() + 1));
        });
        return per;
    }

    /** Se quell'abbonamento o tesseramento ha addebiti (quelli registrati prima dei pagamenti non ne hanno). */
    @Transactional(readOnly = true)
    public boolean haAddebiti(Origine origine, UUID riferimentoId) {
        return addebiti.findByOrigineAndRiferimentoId(origine, riferimentoId).stream().anyMatch(a -> a.attivo() && !a.credito());
    }

    /** Quanto è stato pagato (pagamenti non annullati) per gli addebiti di quell'abbonamento o tesseramento. */
    @Transactional(readOnly = true)
    public BigDecimal pagatoPer(Origine origine, UUID riferimentoId) {
        var suoi = addebiti.findByOrigineAndRiferimentoId(origine, riferimentoId).stream().filter(Addebito::attivo).toList();
        Map<UUID, BigDecimal> pagato = pagatoPerAddebito(suoi.stream().map(Addebito::getId).toList());
        return suoi.stream().map(a -> pagato.getOrDefault(a.getId(), ZERO)).reduce(ZERO, BigDecimal::add);
    }

    /**
     * Chiude gli addebiti di un abbonamento o tesseramento (es. recesso): quelli non pagati si annullano, quelli pagati in
     * parte scendono al pagato. Restituisce quanto non è più dovuto.
     */
    @Transactional
    public BigDecimal chiudi(Origine origine, UUID riferimentoId, String motivo, UUID da) {
        var suoi = addebiti.bloccati(addebiti.findByOrigineAndRiferimentoId(origine, riferimentoId).stream()
            .filter(a -> a.attivo() && !a.credito()).map(Addebito::getId).toList());
        Map<UUID, BigDecimal> pagato = pagatoPerAddebito(suoi.stream().map(Addebito::getId).toList());
        BigDecimal tolto = ZERO;
        for (Addebito a : suoi) {
            BigDecimal p = pagato.getOrDefault(a.getId(), ZERO);
            if (p.compareTo(a.getImporto()) >= 0) continue;
            tolto = tolto.add(a.getImporto().subtract(p));
            if (p.signum() == 0) a.annulla(motivo, da, orologio.instant());
            else a.riduciA(p, motivo);
        }
        if (origine == Origine.TESSERAMENTO) pubblicaSaldato(origine, riferimentoId);
        return tolto;
    }

    /** Annulla tutti gli addebiti di un abbonamento o tesseramento registrato per errore; se ci sono pagamenti no. */
    @Transactional
    public void annullaDi(Origine origine, UUID riferimentoId, String motivo, UUID da) {
        var suoi = addebiti.findByOrigineAndRiferimentoId(origine, riferimentoId).stream().filter(Addebito::attivo).toList();
        if (pagatoPerAddebito(suoi.stream().map(Addebito::getId).toList()).values().stream().anyMatch(v -> v.signum() > 0))
            throw new Conflitto("Ci sono pagamenti registrati: annulla prima i pagamenti (dalla scheda, «Pagamenti»).");
        Instant ora = orologio.instant();
        suoi.forEach(a -> a.annulla(motivo, da, ora));
    }

    // ── Conto di una persona ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    Conto conto(UUID personaId) {
        LocalDate oggi = LocalDate.now(orologio);
        if (persone.nomiCompleti(List.of(personaId)).isEmpty()) throw new NonTrovato("Persona non trovata.");
        var tutori = persone.tutoriDi(personaId);
        Map<UUID, Persone.Familiare> famiglia = new LinkedHashMap<>();
        Stream.of(tutori, persone.assistitiDi(personaId), persone.fratelliDi(personaId)).flatMap(List::stream)
            .filter(f -> !f.personaId().equals(personaId)).forEach(f -> famiglia.putIfAbsent(f.personaId(), f));

        Set<UUID> tutti = new LinkedHashSet<>();
        tutti.add(personaId);
        tutti.addAll(famiglia.keySet());
        List<Addebito> suoi = addebiti.findByPersonaIdInOrderByScadenzaAscCreatoIlAsc(tutti);
        List<Pagamento> deiSuoi = pagamenti.diAddebiti(suoi.stream().map(Addebito::getId).toList());
        Map<UUID, List<PagamentoDiAddebito>> perAddebito = perAddebito(deiSuoi);
        Map<UUID, String> nomi = nomi(suoi, deiSuoi);
        Function<Addebito, AddebitoDto> dto = a -> dto(a, perAddebito.getOrDefault(a.getId(), List.of()), nomi, oggi);

        List<AddebitoDto> propri = suoi.stream().filter(a -> a.getPersonaId().equals(personaId)).map(dto).toList();
        var fam = famiglia.values().stream().map(f -> {
            var aperti = suoi.stream().filter(a -> a.getPersonaId().equals(f.personaId())).map(dto)
                .filter(a -> a.stato().equals("DA_PAGARE") || a.stato().equals("SCADUTO")).toList();
            return new Familiare(f.personaId(), (f.nome() + " " + f.cognome()).strip(), f.relazione(), totali(aperti, List.of()), aperti);
        }).toList();

        // i pagamenti dei suoi addebiti e quelli fatti da lui (anche per i figli)
        Map<UUID, Pagamento> visti = new LinkedHashMap<>();
        deiSuoi.stream().filter(p -> p.getVoci().stream().anyMatch(v -> propri.stream().anyMatch(a -> a.id().equals(v.getAddebitoId()))))
            .forEach(p -> visti.put(p.getId(), p));
        pagamenti.findByPaganteId(personaId).forEach(p -> visti.putIfAbsent(p.getId(), p));
        List<PagamentoDto> storico = dtoPagamenti(visti.values()).stream()
            .sorted(Comparator.comparing(PagamentoDto::data).thenComparing(PagamentoDto::registratoIl).reversed()).toList();

        List<Pagante> paganti = new ArrayList<>();
        paganti.add(new Pagante(personaId, nomi.getOrDefault(personaId, persone.nomiCompleti(List.of(personaId)).get(personaId)), null));
        tutori.forEach(t -> paganti.add(new Pagante(t.personaId(), (t.nome() + " " + t.cognome()).strip(), t.relazione())));
        var borsellini = paganti.stream().map(p -> new BorsellinoDi(p.personaId(), movimenti.euro(p.personaId()), movimenti.crediti(p.personaId()))).toList();
        return new Conto(personaId, totali(propri, storico.stream().filter(p -> p.annullatoIl() == null).toList()), propri, storico, fam, paganti, borsellini);
    }

    private static Totali totali(List<AddebitoDto> lista, List<PagamentoDto> pagati) {
        BigDecimal daPagare = ZERO, scaduto = ZERO, credito = ZERO;
        for (AddebitoDto a : lista) {
            if (a.annullatoIl() != null) continue;
            if (a.importo().signum() < 0) credito = credito.add(a.importo().negate());
            else {
                daPagare = daPagare.add(a.residuo());
                if (a.stato().equals("SCADUTO")) scaduto = scaduto.add(a.residuo());
            }
        }
        BigDecimal daIncassare = pagati.stream().flatMap(p -> p.metodi().stream()).filter(m -> m.incassatoIl() == null)
            .map(MetodoUsato::importo).reduce(ZERO, BigDecimal::add);
        return new Totali(daPagare, scaduto, credito, daIncassare);
    }

    private AddebitoDto dto(Addebito a, List<PagamentoDiAddebito> pagati, Map<UUID, String> nomi, LocalDate oggi) {
        BigDecimal pagato = pagati.stream().map(PagamentoDiAddebito::importo).reduce(ZERO, BigDecimal::add);
        BigDecimal residuo = a.credito() || !a.attivo() ? ZERO : a.getImporto().subtract(pagato).max(ZERO);
        String stato = !a.attivo() ? "ANNULLATO" : a.credito() ? "CREDITO" : residuo.signum() == 0 ? "PAGATO"
            : a.getScadenza().isBefore(oggi) ? "SCADUTO" : "DA_PAGARE";
        return new AddebitoDto(a.getId(), a.getPersonaId(), nomi.get(a.getPersonaId()), a.getDescrizione(), a.getImporto(), pagato, residuo, a.getScadenza(),
            stato, a.getOrigine(), a.getRiferimentoId(), a.getRata(), a.getRate(), a.getNote(), a.getAnnullatoIl(), a.getMotivoAnnullamento(), pagati);
    }

    private static Map<UUID, List<PagamentoDiAddebito>> perAddebito(Collection<Pagamento> lista) {
        Map<UUID, List<PagamentoDiAddebito>> m = new HashMap<>();
        lista.stream().filter(Pagamento::attivo).forEach(p -> p.getVoci().forEach(v ->
            m.computeIfAbsent(v.getAddebitoId(), k -> new ArrayList<>()).add(new PagamentoDiAddebito(p.getId(), p.getData(), v.getImporto()))));
        return m;
    }

    private Map<UUID, BigDecimal> pagatoPerAddebito(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> m = new HashMap<>();
        pagamenti.diAddebiti(ids).stream().filter(Pagamento::attivo).forEach(p -> p.getVoci().forEach(v -> m.merge(v.getAddebitoId(), v.getImporto(), BigDecimal::add)));
        return m;
    }

    private Map<UUID, String> nomi(Collection<Addebito> a, Collection<Pagamento> p) {
        Set<UUID> ids = new HashSet<>();
        a.forEach(x -> ids.add(x.getPersonaId()));
        p.forEach(x -> {
            ids.add(x.getPaganteId());
            if (x.getRegistratoDa() != null) ids.add(x.getRegistratoDa());
        });
        return ids.isEmpty() ? Map.of() : persone.nomiCompleti(ids);
    }

    private List<PagamentoDto> dtoPagamenti(Collection<Pagamento> lista) {
        if (lista.isEmpty()) return List.of();
        Map<UUID, Addebito> voci = addebiti.findAllById(lista.stream().flatMap(p -> p.getVoci().stream()).map(Pagamento.Voce::getAddebitoId)
            .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Addebito::getId, x -> x));
        Map<UUID, String> nomi = nomi(voci.values(), lista);
        Map<UUID, MetodoPagamento> met = metodi.findAll().stream().collect(Collectors.toMap(MetodoPagamento::getId, x -> x));
        return lista.stream().map(p -> new PagamentoDto(p.getId(), p.getData(), p.getImporto(), p.getPaganteId(), nomi.get(p.getPaganteId()),
            p.getMetodi().stream().map(m -> {
                MetodoPagamento mp = met.get(m.getMetodoId());
                return new MetodoUsato(m.getId(), m.getMetodoId(), mp == null ? "?" : mp.getNome(), m.getImporto(), mp != null && mp.isDaIncassare(), mp != null && mp.isContanti(),
                    m.getIncassatoIl(), m.getRiferimento());
            }).toList(),
            p.getVoci().stream().map(v -> {
                Addebito a = voci.get(v.getAddebitoId());
                return new VocePagata(v.getAddebitoId(), a == null ? "?" : a.getDescrizione(), a == null ? null : a.getPersonaId(),
                    a == null ? null : nomi.get(a.getPersonaId()), v.getImporto());
            }).toList(),
            p.getNote(), p.getRegistratoDa() == null ? null : nomi.get(p.getRegistratoDa()), p.getRegistratoIl(), p.getAnnullatoIl(),
            p.getMotivoAnnullamento())).toList();
    }

    // ── Addebiti scritti a mano ─────────────────────────────────────────────

    @Transactional
    UUID addebitoManuale(NuovoAddebitoManuale in, UUID da) {
        if (in.personaId() == null || persone.nomiCompleti(List.of(in.personaId())).isEmpty()) throw new NonTrovato("Persona non trovata.");
        if (in.importo() == null || in.importo().signum() <= 0) throw new IllegalArgumentException("L'importo dell'addebito è maggiore di zero.");
        LocalDate scadenza = in.scadenza() != null ? in.scadenza() : LocalDate.now(orologio);
        return addebita(List.of(new NuovoAddebito(in.personaId(), in.descrizione(), in.importo(), scadenza, Origine.ALTRO, null, in.offertaId(), null, null,
            in.note())), da).getFirst();
    }

    @Transactional
    void annullaAddebito(UUID id, Motivo in, UUID da) {
        String motivo = motivo(in);
        Addebito a = addebiti.bloccati(List.of(id)).stream().findFirst().orElseThrow(() -> new NonTrovato("Addebito non trovato."));
        if (pagatoPerAddebito(List.of(id)).getOrDefault(id, ZERO).signum() > 0)
            throw new Conflitto("L'addebito è già stato pagato, almeno in parte: annulla prima il pagamento.");
        a.annulla(motivo, da, orologio.instant());
        if (a.getOrigine() == Origine.TESSERAMENTO && a.getRiferimentoId() != null) pubblicaSaldato(a.getOrigine(), a.getRiferimentoId());
    }

    // ── Pagamenti ───────────────────────────────────────────────────────────

    @Transactional
    List<UUID> registra(NuovoPagamento in, UUID da, boolean flessibile) {
        LocalDate oggi = LocalDate.now(orologio);
        LocalDate data = in.data() != null ? in.data() : oggi;
        if (data.isAfter(oggi)) throw new IllegalArgumentException("Il pagamento non può essere nel futuro.");
        if (in.addebiti() == null || in.addebiti().isEmpty()) throw new IllegalArgumentException("Scegli cosa si paga.");
        if (in.metodi() == null || in.metodi().isEmpty()) throw new IllegalArgumentException("Scegli come si paga.");
        if (in.note() != null && in.note().length() > 300) throw new IllegalArgumentException("Le note sono troppo lunghe (al massimo 300 caratteri).");

        Map<UUID, MetodoPagamento> met = metodi.findAll().stream().collect(Collectors.toMap(MetodoPagamento::getId, x -> x));
        BigDecimal somma = ZERO;
        for (MetodoIn m : in.metodi()) {
            MetodoPagamento mp = met.get(m.metodoId());
            if (mp == null || !mp.isAttivo()) throw new IllegalArgumentException("Metodo di pagamento non valido.");
            if (m.importo() == null || m.importo().signum() <= 0 || m.importo().scale() > 2)
                throw new IllegalArgumentException("Scrivi quanto si paga con " + mp.getNome() + " (in euro, al massimo due decimali).");
            if (m.riferimento() != null && m.riferimento().length() > 120) throw new IllegalArgumentException("Il riferimento è troppo lungo.");
            somma = somma.add(m.importo());
        }
        BigDecimal importo = in.importo() != null ? in.importo() : somma;
        if (importo.compareTo(somma) != 0)
            throw new IllegalArgumentException("I metodi fanno " + somma + " €, ma il pagamento è di " + importo + " €.");

        List<UUID> ordine = in.addebiti().stream().distinct().toList();
        Map<UUID, Addebito> bloccati = addebiti.bloccati(ordine).stream().collect(Collectors.toMap(Addebito::getId, x -> x));
        Map<UUID, BigDecimal> pagato = pagatoPerAddebito(ordine);
        List<Addebito> daPagare = new ArrayList<>();
        for (UUID id : ordine) {
            Addebito a = bloccati.get(id);
            if (a == null) throw new NonTrovato("Addebito non trovato.");
            if (!a.attivo()) throw new IllegalArgumentException("«" + a.getDescrizione() + "» è annullato.");
            if (a.credito()) throw new IllegalArgumentException("«" + a.getDescrizione() + "» è un credito, non si paga.");
            if (a.getImporto().subtract(pagato.getOrDefault(id, ZERO)).signum() <= 0)
                throw new Conflitto("«" + a.getDescrizione() + "» è già pagato.");
            daPagare.add(a);
        }
        BigDecimal residuo = daPagare.stream().map(a -> a.getImporto().subtract(pagato.getOrDefault(a.getId(), ZERO))).reduce(ZERO, BigDecimal::add);
        if (importo.compareTo(residuo) > 0)
            throw new IllegalArgumentException("Il pagamento (" + importo + " €) supera quanto c'è da pagare (" + residuo + " €).");
        if (!flessibile && (in.metodi().size() > 1 || importo.compareTo(residuo) < 0))
            throw new org.springframework.security.access.AccessDeniedException(
                "Acconti e pagamenti divisi tra più metodi richiedono il permesso «Acconti e pagamenti misti»: si paga per intero (" + residuo + " €) con un solo metodo.");

        UUID pagante = in.paganteId() != null ? in.paganteId() : daPagare.getFirst().getPersonaId();
        if (persone.nomiCompleti(List.of(pagante)).isEmpty()) throw new NonTrovato("Chi paga non è tra le persone registrate.");

        // si salda nell'ordine scelto: il primo addebito per intero, poi il successivo, fino a esaurire l'importo
        Map<UUID, BigDecimal> quote = new LinkedHashMap<>();
        BigDecimal resto = importo;
        for (Addebito a : daPagare) {
            if (resto.signum() == 0) break;
            BigDecimal quota = a.getImporto().subtract(pagato.getOrDefault(a.getId(), ZERO)).min(resto);
            quote.put(a.getId(), quota);
            resto = resto.subtract(quota);
        }
        // un pagamento unico, o uno per ogni persona (impostazione della società, o scelta di chi registra)
        Map<UUID, List<Addebito>> perPersona = new LinkedHashMap<>();
        daPagare.stream().filter(a -> quote.containsKey(a.getId())).forEach(a -> perPersona.computeIfAbsent(a.getPersonaId(), k -> new ArrayList<>()).add(a));
        String modo = societa.dati().pagamentiFamiglia();
        boolean separa = perPersona.size() > 1 && ("PER_PERSONA".equals(modo) || ("A_SCELTA".equals(modo) && Boolean.TRUE.equals(in.separati())));
        List<List<Addebito>> gruppi = separa ? new ArrayList<>(perPersona.values())
            : List.of(daPagare.stream().filter(a -> quote.containsKey(a.getId())).toList());

        // i metodi si consumano in ordine: il primo copre il primo pagamento, poi il resto passa al successivo
        List<BigDecimal> restiMetodi = new ArrayList<>(in.metodi().stream().map(MetodoIn::importo).toList());
        List<UUID> ids = new ArrayList<>();
        for (List<Addebito> gruppo : gruppi) {
            BigDecimal totale = gruppo.stream().map(a -> quote.get(a.getId())).reduce(ZERO, BigDecimal::add);
            Pagamento p = new Pagamento(pagante, data, totale, in.note(), da, orologio.instant(), CassaDellaRichiesta.id());
            BigDecimal manca = totale;
            for (int i = 0; i < in.metodi().size() && manca.signum() > 0; i++) {
                BigDecimal parte = restiMetodi.get(i).min(manca);
                if (parte.signum() == 0) continue;
                MetodoIn m = in.metodi().get(i);
                MetodoPagamento mp = met.get(m.metodoId());
                p.aggiungiMetodo(new PagamentoMetodo(mp.getId(), parte, mp.isDaIncassare() ? null : data, m.riferimento()));
                restiMetodi.set(i, restiMetodi.get(i).subtract(parte));
                manca = manca.subtract(parte);
            }
            gruppo.forEach(a -> p.aggiungiVoce(a.getId(), quote.get(a.getId())));
            pagamenti.saveAndFlush(p);
            ids.add(p.getId());
            // con il borsellino si spende il credito in euro di chi paga
            for (PagamentoMetodo pm : p.getMetodi())
                if (met.get(pm.getMetodoId()).isBorsellino())
                    movimenti.togliEuro(pagante, pm.getImporto(), MovimentoBorsellino.Causale.PAGAMENTO,
                        "Pagamento: " + gruppo.getFirst().getDescrizione() + (gruppo.size() > 1 ? " e altro" : ""), p.getId(), da);
        }
        pagamenti.flush();
        daPagare.stream().filter(a -> a.getOrigine() == Origine.TESSERAMENTO && a.getRiferimentoId() != null)
            .map(Addebito::getRiferimentoId).distinct().forEach(r -> pubblicaSaldato(Origine.TESSERAMENTO, r));
        return ids;
    }

    /**
     * Le entrate e le uscite registrate dopo «da» e fino ad «a» compreso (vuoto = fino a adesso), per la cassa: i
     * pagamenti annullati e le uscite annullate non ci sono.
     */
    @Transactional(readOnly = true)
    public List<VoceCassa> vociCassa(Instant da, Instant a) {
        Instant fine = a != null ? a : orologio.instant();
        var pag = pagamenti.findByRegistratoIlGreaterThanAndRegistratoIlLessThanEqualOrderByRegistratoIl(da, fine).stream().filter(Pagamento::attivo).toList();
        var out = uscite.findByRegistratoIlGreaterThanAndRegistratoIlLessThanEqualOrderByRegistratoIl(da, fine).stream().filter(u -> u.getAnnullatoIl() == null).toList();
        Map<UUID, MetodoPagamento> met = metodi.findAll().stream().collect(Collectors.toMap(MetodoPagamento::getId, x -> x));
        Set<UUID> ids = new HashSet<>();
        pag.forEach(p -> ids.add(p.getPaganteId()));
        out.forEach(u -> ids.add(u.getPersonaId()));
        Map<UUID, String> nomi = ids.isEmpty() ? Map.of() : persone.nomiCompleti(ids);
        Map<UUID, Addebito> voci = addebiti.findAllById(pag.stream().flatMap(p -> p.getVoci().stream()).map(Pagamento.Voce::getAddebitoId)
            .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Addebito::getId, x -> x));
        List<VoceCassa> lista = new ArrayList<>();
        for (Pagamento p : pag) {
            String per = p.getVoci().stream().map(v -> voci.containsKey(v.getAddebitoId()) ? voci.get(v.getAddebitoId()).getDescrizione() : "?")
                .distinct().collect(Collectors.joining(" · "));
            for (PagamentoMetodo m : p.getMetodi()) {
                MetodoPagamento mp = met.get(m.getMetodoId());
                lista.add(new VoceCassa(p.getId(), "ENTRATA", p.getRegistratoIl(), p.getCassaId(), m.getMetodoId(), mp == null ? "?" : mp.getNome(),
                    mp != null && mp.isContanti(), mp != null && mp.isDaIncassare(), m.getImporto(), nomi.get(p.getPaganteId()), per));
            }
        }
        for (Uscita u : out) {
            MetodoPagamento mp = met.get(u.getMetodoId());
            lista.add(new VoceCassa(u.getId(), "USCITA", u.getRegistratoIl(), u.getCassaId(), u.getMetodoId(), mp == null ? "?" : mp.getNome(),
                mp != null && mp.isContanti(), false, u.getImporto(), nomi.get(u.getPersonaId()), u.getDescrizione()));
        }
        lista.sort(Comparator.comparing(VoceCassa::istante));
        return lista;
    }

    /** I metodi di pagamento, nell'ordine della società (per i totali della cassa). */
    @Transactional(readOnly = true)
    public List<Metodo> elencoMetodi() {
        return metodi();
    }

    /** Il pagamento visto da una ricevuta. */
    @Transactional(readOnly = true)
    public Optional<PerRicevuta> perRicevuta(UUID pagamentoId) {
        return pagamenti.findById(pagamentoId).map(p -> {
            Map<UUID, String> nomiMetodi = metodi.findAll().stream().collect(Collectors.toMap(MetodoPagamento::getId, MetodoPagamento::getNome));
            Map<UUID, Addebito> suoi = addebiti.findAllById(p.getVoci().stream().map(Pagamento.Voce::getAddebitoId).toList()).stream()
                .collect(Collectors.toMap(Addebito::getId, a -> a));
            List<VoceRicevuta> voci = p.getVoci().stream().map(v -> {
                Addebito a = suoi.get(v.getAddebitoId());
                return new VoceRicevuta(v.getAddebitoId(), a.getPersonaId(), a.getDescrizione(), v.getImporto(), a.getOrigine());
            }).toList();
            return new PerRicevuta(p.getId(), p.getPaganteId(), p.getData(), p.getImporto(), p.attivo(),
                p.getMetodi().stream().map(m -> nomiMetodi.getOrDefault(m.getMetodoId(), "?")).distinct().toList(), voci);
        });
    }

    @Transactional
    void annullaPagamento(UUID id, Motivo in, UUID da) {
        String motivo = motivo(in);
        Pagamento p = pagamenti.findById(id).orElseThrow(() -> new NonTrovato("Pagamento non trovato."));
        p.annulla(motivo, da, orologio.instant());
        pagamenti.flush();
        movimenti.annullaPagamento(id, da);
        eventi.publishEvent(new PagamentoAnnullato(id, motivo, da));
        addebiti.findAllById(p.getVoci().stream().map(Pagamento.Voce::getAddebitoId).toList()).stream()
            .filter(a -> a.getOrigine() == Origine.TESSERAMENTO && a.getRiferimentoId() != null)
            .map(Addebito::getRiferimentoId).distinct().forEach(r -> pubblicaSaldato(Origine.TESSERAMENTO, r));
    }

    /** Il bonifico (o altro metodo «da incassare») è arrivato sul conto. */
    @Transactional
    void incassa(UUID metodoUsatoId, Incasso in, UUID da) {
        Pagamento p = pagamenti.conMetodoUsato(metodoUsatoId).orElseThrow(() -> new NonTrovato("Pagamento non trovato."));
        if (!p.attivo()) throw new IllegalArgumentException("Il pagamento è annullato.");
        LocalDate il = in != null && in.il() != null ? in.il() : LocalDate.now(orologio);
        if (il.isAfter(LocalDate.now(orologio))) throw new IllegalArgumentException("La data dell'incasso non può essere nel futuro.");
        if (il.isBefore(p.getData())) throw new IllegalArgumentException("L'incasso non può essere prima del pagamento.");
        p.getMetodi().stream().filter(m -> m.getId().equals(metodoUsatoId)).findFirst().orElseThrow().incassa(il, da);
    }

    @Transactional(readOnly = true)
    Incassi incassi(LocalDate da, LocalDate a) {
        LocalDate oggi = LocalDate.now(orologio);
        LocalDate inizio = da != null ? da : oggi, fine = a != null ? a : inizio;
        if (fine.isBefore(inizio)) throw new IllegalArgumentException("La data di fine viene prima di quella di inizio.");
        if (ChronoUnit.DAYS.between(inizio, fine) > 92) throw new IllegalArgumentException("Al massimo tre mesi alla volta.");
        var lista = dtoPagamenti(pagamenti.findByDataBetweenOrderByDataDescRegistratoIlDesc(inizio, fine));
        var out = uscite.findByDataBetweenOrderByDataDescRegistratoIlDesc(inizio, fine).stream().filter(u -> u.getAnnullatoIl() == null).toList();
        Map<UUID, MetodoPagamento> met = metodi.findAll().stream().collect(Collectors.toMap(MetodoPagamento::getId, x -> x));
        Set<UUID> ids = new HashSet<>();
        out.forEach(u -> {
            ids.add(u.getPersonaId());
            if (u.getRegistratoDa() != null) ids.add(u.getRegistratoDa());
        });
        Map<UUID, String> nomi = ids.isEmpty() ? Map.of() : persone.nomiCompleti(ids);
        Map<UUID, TotaleMetodo> tot = new LinkedHashMap<>();
        metodi.findAllByOrderByOrdineAscNomeAsc().forEach(m -> tot.put(m.getId(), new TotaleMetodo(m.getId(), m.getNome(), ZERO, ZERO, ZERO)));
        lista.stream().filter(p -> p.annullatoIl() == null).flatMap(p -> p.metodi().stream()).forEach(m -> tot.computeIfPresent(m.metodoId(), (k, t) ->
            new TotaleMetodo(k, t.metodo(), t.importo().add(m.importo()), m.incassatoIl() == null ? t.daIncassare().add(m.importo()) : t.daIncassare(), t.uscite())));
        out.forEach(u -> tot.computeIfPresent(u.getMetodoId(), (k, t) -> new TotaleMetodo(k, t.metodo(), t.importo(), t.daIncassare(), t.uscite().add(u.getImporto()))));
        BigDecimal totale = tot.values().stream().map(TotaleMetodo::importo).reduce(ZERO, BigDecimal::add);
        var usciteDto = out.stream().map(u -> new UscitaDto(u.getId(), u.getData(), u.getPersonaId(), nomi.get(u.getPersonaId()),
            met.containsKey(u.getMetodoId()) ? met.get(u.getMetodoId()).getNome() : "?", met.containsKey(u.getMetodoId()) && met.get(u.getMetodoId()).isContanti(), u.getImporto(), u.getDescrizione(),
            u.getRegistratoDa() == null ? null : nomi.get(u.getRegistratoDa()), u.getRegistratoIl())).toList();
        return new Incassi(inizio, fine, totale, tot.values().stream().filter(t -> t.importo().signum() > 0 || t.uscite().signum() > 0).toList(), lista, usciteDto);
    }

    @Transactional(readOnly = true)
    List<DaIncassare> daIncassare() {
        return dtoPagamenti(pagamenti.conDaIncassare()).stream()
            .flatMap(p -> p.metodi().stream().filter(m -> m.incassatoIl() == null).map(m -> new DaIncassare(m.id(), p.id(), p.data(), p.paganteId(),
                p.pagante(), m.metodo(), m.importo(), m.riferimento(), p.voci().stream().map(v -> v.persona() + " · " + v.descrizione()).toList())))
            .sorted(Comparator.comparing(DaIncassare::data)).toList();
    }

    // ── Metodi di pagamento ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<Metodo> metodi() {
        return metodi.findAllByOrderByOrdineAscNomeAsc().stream().map(MetodoPagamento::dto).toList();
    }

    @Transactional
    Metodo creaMetodo(NuovoMetodo in) {
        int ordine = in.ordine() != null ? in.ordine() : metodi.findAll().stream().mapToInt(MetodoPagamento::getOrdine).max().orElse(0) + 1;
        return metodi.saveAndFlush(new MetodoPagamento(in.nome(), Boolean.TRUE.equals(in.daIncassare()), Boolean.TRUE.equals(in.contanti()), ordine)).dto();
    }

    @Transactional
    Metodo modificaMetodo(UUID id, NuovoMetodo in) {
        MetodoPagamento m = metodi.findById(id).orElseThrow(() -> new NonTrovato("Metodo di pagamento non trovato."));
        m.modifica(in.nome(), in.daIncassare() != null ? in.daIncassare() : m.isDaIncassare(), in.contanti() != null ? in.contanti() : m.isContanti(), in.attivo() != null ? in.attivo() : m.isAttivo(),
            in.ordine() != null ? in.ordine() : m.getOrdine());
        return metodi.saveAndFlush(m).dto();
    }

    // ── Aiuti ───────────────────────────────────────────────────────────────

    private static String motivo(Motivo in) {
        if (in == null || in.motivo() == null || in.motivo().isBlank()) throw new IllegalArgumentException("Scrivi il motivo dell'annullamento.");
        if (in.motivo().strip().length() > 300) throw new IllegalArgumentException("Il motivo è troppo lungo (al massimo 300 caratteri).");
        return in.motivo().strip();
    }

    /** Dice agli altri moduli se gli addebiti di quel riferimento sono tutti pagati, e da quando. */
    private void pubblicaSaldato(Origine origine, UUID riferimentoId) {
        var suoi = addebiti.findByOrigineAndRiferimentoId(origine, riferimentoId).stream().filter(a -> a.attivo() && !a.credito()).toList();
        if (suoi.isEmpty()) {
            eventi.publishEvent(new Saldato(origine, riferimentoId, null));
            return;
        }
        var pag = pagamenti.diAddebiti(suoi.stream().map(Addebito::getId).toList()).stream().filter(Pagamento::attivo).toList();
        Map<UUID, BigDecimal> pagato = new HashMap<>();
        pag.forEach(p -> p.getVoci().forEach(v -> pagato.merge(v.getAddebitoId(), v.getImporto(), BigDecimal::add)));
        boolean tutto = suoi.stream().allMatch(a -> pagato.getOrDefault(a.getId(), ZERO).compareTo(a.getImporto()) >= 0);
        LocalDate il = tutto ? pag.stream().map(Pagamento::getData).max(Comparator.naturalOrder()).orElse(null) : null;
        eventi.publishEvent(new Saldato(origine, riferimentoId, il));
    }
}
