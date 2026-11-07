package it.leonexiii.gestionale.ricevute;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.notifiche.Posta;
import it.leonexiii.gestionale.pagamenti.Pagamenti;
import it.leonexiii.gestionale.societa.Societa;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Le ricevute dei pagamenti: numerazione, emissione, annullo, PDF ed email; e le loro impostazioni. */
@Service
class RicevuteService {

    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    record ImpostazioniDto(String intestata, boolean codiciFiscali, boolean bollo, BigDecimal bolloSoglia, BigDecimal bolloImporto, String dicitura,
                           boolean firma, String dopo, boolean email, String famiglia) {}

    /** Una serie con il suo periodo in corso: quante ricevute ha e quale sarà la prossima. */
    record SerieDto(UUID id, String nome, Serie.Tipo tipo, boolean attiva, String formato, int cifre, Serie.Riparte riparte, Integer prossimoNumero,
                    String periodo, long emesse, String prossima) {}

    record ModificaSerie(String nome, Boolean attiva, String formato, Integer cifre, Serie.Riparte riparte, Integer prossimoNumero) {}

    record NuovaSerie(String nome, Serie.Tipo tipo) {}

    /** La pagina delle impostazioni: le scelte, le serie e cosa manca ai dati della società per l'intestazione. */
    record Pagina(ImpostazioniDto impostazioni, List<SerieDto> serie, List<String> mancaIntestazione) {}

    /** modo: FAMIGLIA (una ricevuta) o PERSONA (una per beneficiario); vuoto = come nelle impostazioni. */
    record Emetti(UUID pagamentoId, String modo, UUID serieId) {}

    record Annullo(String motivo, Boolean rifai) {}

    record Invio(String email) {}

    record RicevutaDto(UUID id, String numero, UUID serieId, String serie, Serie.Tipo tipo, LocalDate data, UUID pagamentoId, Ricevuta.Soggetto intestatario,
                       List<Ricevuta.Soggetto> beneficiari, List<Ricevuta.Riga> righe, BigDecimal totale, String metodi, BigDecimal bollo,
                       String dicitura, Instant creataIl, Instant annullataIl, String motivoAnnullamento, UUID sostituitaDa, String sostituitaDaNumero,
                       Instant stampataIl, Instant inviataIl, String inviataA) {}

    private final SerieRepository serie;
    private final ImpostazioniRepository impostazioni;
    private final RicevutaRepository ricevute;
    private final Pagamenti pagamenti;
    private final Persone persone;
    private final Societa societa;
    private final Posta posta;
    private final Clock orologio;

    RicevuteService(SerieRepository serie, ImpostazioniRepository impostazioni, RicevutaRepository ricevute, Pagamenti pagamenti, Persone persone,
                    Societa societa, Posta posta, Clock orologio) {
        this.serie = serie;
        this.impostazioni = impostazioni;
        this.ricevute = ricevute;
        this.pagamenti = pagamenti;
        this.persone = persone;
        this.societa = societa;
        this.posta = posta;
        this.orologio = orologio;
    }

    // ── Impostazioni ────────────────────────────────────────────────────────

    private Impostazioni imp() {
        return impostazioni.findById((short) 1).orElseThrow();
    }

    @Transactional(readOnly = true)
    Pagina pagina() {
        LocalDate oggi = LocalDate.now(orologio);
        return new Pagina(dto(imp()), serie.findAllByOrderByOrdineAscNomeAsc().stream().map(s -> dto(s, oggi)).toList(), mancaIntestazione());
    }

    @Transactional
    ImpostazioniDto modifica(ImpostazioniDto in) {
        Impostazioni i = imp();
        i.modifica(in);
        return dto(i);
    }

    @Transactional
    SerieDto modificaSerie(UUID id, ModificaSerie in) {
        Serie s = serie.bloccata(id).orElseThrow(() -> new NonTrovato("Serie non trovata."));
        LocalDate oggi = LocalDate.now(orologio);
        s.modifica(in.nome(), !Boolean.FALSE.equals(in.attiva()), in.formato(), in.cifre() == null ? 4 : in.cifre(), in.riparte());
        // il numero di partenza si sceglie solo finché il periodo non ha ricevute: dopo, la numerazione continua da sé
        if (!Objects.equals(in.prossimoNumero(), s.getProssimoNumero())) {
            if (ricevute.countBySerieIdAndPeriodo(s.getId(), periodo(s, oggi)) > 0)
                throw new Conflitto("Il numero di partenza non si cambia: in questo periodo ci sono già ricevute.");
            s.prossimoNumero(in.prossimoNumero());
        }
        return dto(s, oggi);
    }

    @Transactional
    SerieDto creaSerie(NuovaSerie in) {
        if (in.tipo() == null) throw new IllegalArgumentException("Scegli se è una serie di ricevute o di fatture.");
        int ordine = serie.findAll().stream().mapToInt(Serie::getOrdine).max().orElse(0) + 1;
        Serie s = new Serie(in.tipo(), (short) ordine);
        s.modifica(in.nome(), false, in.tipo() == Serie.Tipo.FATTURA ? "F{anno}/{numero}" : "{stagione}-{numero}", 4,
            in.tipo() == Serie.Tipo.FATTURA ? Serie.Riparte.ANNO : Serie.Riparte.STAGIONE);
        serie.save(s);
        return dto(s, LocalDate.now(orologio));
    }

    /** Cosa manca ai dati della società perché l'intestazione sia completa. */
    private List<String> mancaIntestazione() {
        Societa.Dati d = societa.dati();
        List<String> manca = new ArrayList<>();
        if (vuoto(d.ragioneSociale()) && vuoto(d.nome())) manca.add("ragione sociale");
        if (vuoto(d.partitaIva()) && vuoto(d.codiceFiscale())) manca.add("partita IVA o codice fiscale");
        if (vuoto(d.indirizzo())) manca.add("indirizzo");
        return manca;
    }

    // ── Numerazione ─────────────────────────────────────────────────────────

    /** Il periodo in cui il numero è unico: la stagione (o l'anno, fuori stagione), l'anno, o sempre lo stesso. */
    String periodo(Serie s, LocalDate data) {
        return switch (s.getRiparte()) {
            case STAGIONE -> societa.stagioneIl(data).map(Societa.Stagione::nome).orElse(String.valueOf(data.getYear()));
            case ANNO -> String.valueOf(data.getYear());
            case MAI -> "";
        };
    }

    static String testo(Serie s, String stagione, LocalDate data, int numero) {
        return s.getFormato().replace("{stagione}", stagione).replace("{anno}", String.valueOf(data.getYear()))
            .replace("{numero}", String.format("%0" + s.getCifre() + "d", numero));
    }

    private int prossimo(Serie s, String periodo) {
        int ultimo = ricevute.ultimoNumero(s.getId(), periodo);
        return ultimo == 0 && s.getProssimoNumero() != null ? s.getProssimoNumero() : ultimo + 1;
    }

    private String stagioneDi(LocalDate data) {
        return societa.stagioneIl(data).map(Societa.Stagione::nome).orElse(String.valueOf(data.getYear()));
    }

    // ── Emissione ───────────────────────────────────────────────────────────

    /**
     * Le ricevute di un pagamento: se ce ne sono già (non annullate) restano quelle; altrimenti una per famiglia o una
     * per persona. Intestate a chi frequenta, sono sempre una per persona.
     */
    @Transactional
    List<RicevutaDto> emetti(Emetti in, UUID da) {
        if (in.pagamentoId() == null) throw new IllegalArgumentException("Di quale pagamento?");
        if (in.modo() != null && !Set.of("FAMIGLIA", "PERSONA").contains(in.modo())) throw new IllegalArgumentException("Scegli una ricevuta per famiglia o una per persona.");
        Pagamenti.PerRicevuta p = pagamenti.perRicevuta(in.pagamentoId()).orElseThrow(() -> new NonTrovato("Pagamento non trovato."));
        if (!p.attivo()) throw new Conflitto("Il pagamento è annullato: non si fanno ricevute.");
        List<Ricevuta> gia = ricevute.findByPagamentoIdOrderByNumeroTestoAsc(p.id()).stream().filter(Ricevuta::attiva).toList();
        if (!gia.isEmpty()) return dto(gia);

        Serie s = serieDaUsare(in.serieId());
        Impostazioni i = imp();
        String modo = in.modo() != null ? in.modo() : "PERSONA".equals(i.getFamiglia()) ? "PERSONA" : "FAMIGLIA";
        Map<UUID, List<Pagamenti.VoceRicevuta>> perPersona = new LinkedHashMap<>();
        p.voci().forEach(v -> perPersona.computeIfAbsent(v.personaId(), k -> new ArrayList<>()).add(v));
        boolean separa = perPersona.size() > 1 && ("PERSONA".equals(modo) || "BENEFICIARIO".equals(i.getIntestata()));
        List<List<Pagamenti.VoceRicevuta>> gruppi = separa ? new ArrayList<>(perPersona.values()) : List.of(p.voci());
        List<Ricevuta> fatte = new ArrayList<>();
        for (var gruppo : gruppi) fatte.add(crea(s, p, gruppo, i, da));
        return dto(fatte);
    }

    private Serie serieDaUsare(UUID id) {
        Serie s = id != null ? serie.bloccata(id).orElseThrow(() -> new NonTrovato("Serie non trovata."))
            : serie.findAllByOrderByOrdineAscNomeAsc().stream().filter(x -> x.isAttiva() && x.getTipo() == Serie.Tipo.RICEVUTA).findFirst()
                .flatMap(x -> serie.bloccata(x.getId()))
                .orElseThrow(() -> new Conflitto("Non c'è una serie di ricevute accesa: accendila nelle impostazioni delle ricevute."));
        if (!s.isAttiva()) throw new Conflitto("La serie «" + s.getNome() + "» è spenta.");
        return s;
    }

    private Ricevuta crea(Serie s, Pagamenti.PerRicevuta p, List<Pagamenti.VoceRicevuta> voci, Impostazioni i, UUID da) {
        List<UUID> idBeneficiari = voci.stream().map(Pagamenti.VoceRicevuta::personaId).distinct().toList();
        Map<UUID, Ricevuta.Soggetto> soggetti = soggetti(Stream.concat(idBeneficiari.stream(), Stream.of(p.paganteId())).collect(Collectors.toSet()),
            i.isCodiciFiscali());
        List<Ricevuta.Soggetto> beneficiari = idBeneficiari.stream().map(soggetti::get).toList();
        Ricevuta.Soggetto intestatario = "BENEFICIARIO".equals(i.getIntestata()) && beneficiari.size() == 1 ? beneficiari.getFirst() : soggetti.get(p.paganteId());
        List<Ricevuta.Riga> righe = voci.stream()
            .map(v -> new Ricevuta.Riga(v.addebitoId(), v.personaId(), v.descrizione(), soggetti.get(v.personaId()).nomeCompleto(), v.importo())).toList();
        BigDecimal totale = righe.stream().map(Ricevuta.Riga::importo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bollo = i.isBollo() && totale.compareTo(i.getBolloSoglia()) > 0 ? i.getBolloImporto() : null;
        String dicitura = i.getDicitura() == null ? null : i.getDicitura()
            .replace("{beneficiario}", beneficiari.stream().map(Ricevuta.Soggetto::nomeCompleto).collect(Collectors.joining(" e ")))
            .replace("{nascita}", beneficiari.stream().map(Ricevuta.Soggetto::dataNascita).filter(Objects::nonNull).map(DATA::format).collect(Collectors.joining(" e ")));

        String periodo = periodo(s, p.data());
        int numero = prossimo(s, periodo);
        Ricevuta r = new Ricevuta(s, periodo, numero, testo(s, stagioneDi(p.data()), p.data(), numero), p.id(), p.data(), intestatario, beneficiari, righe,
            String.join(", ", p.metodi()), bollo, dicitura, da, orologio.instant());
        return ricevute.saveAndFlush(r);
    }

    private Map<UUID, Ricevuta.Soggetto> soggetti(Collection<UUID> ids, boolean conCodiceFiscale) {
        Map<UUID, Ricevuta.Soggetto> out = new LinkedHashMap<>();
        for (UUID id : ids) {
            Persone.Anagrafica a = persone.anagrafica(id);
            String indirizzo = Stream.of(a.indirizzo(), Stream.of(a.cap(), a.citta(), vuoto(a.provincia()) ? null : "(" + a.provincia() + ")")
                    .filter(x -> !vuoto(x)).collect(Collectors.joining(" ")))
                .filter(x -> !vuoto(x)).collect(Collectors.joining(" · "));
            out.put(id, new Ricevuta.Soggetto(id, a.nome(), a.cognome(), conCodiceFiscale && !vuoto(a.codiceFiscale()) ? a.codiceFiscale() : null,
                indirizzo.isEmpty() ? null : indirizzo, a.dataNascita(), vuoto(a.email()) ? null : a.email()));
        }
        return out;
    }

    // ── Annullo ─────────────────────────────────────────────────────────────

    /** Annulla una ricevuta (il numero resta usato); con «rifai» ne fa una nuova per le stesse righe, con i dati di adesso. */
    @Transactional
    List<RicevutaDto> annulla(UUID id, Annullo in, UUID da) {
        if (in == null || vuoto(in.motivo())) throw new IllegalArgumentException("Scrivi perché la ricevuta si annulla.");
        if (in.motivo().strip().length() > 300) throw new IllegalArgumentException("Il motivo è troppo lungo (al massimo 300 caratteri).");
        Ricevuta r = ricevute.findById(id).orElseThrow(() -> new NonTrovato("Ricevuta non trovata."));
        r.annulla(in.motivo().strip(), da, orologio.instant());
        if (!Boolean.TRUE.equals(in.rifai())) return List.of();
        Pagamenti.PerRicevuta p = pagamenti.perRicevuta(r.getPagamentoId()).orElseThrow();
        if (!p.attivo()) throw new Conflitto("Il pagamento è annullato: la ricevuta non si rifà.");
        Set<UUID> suoi = r.getRighe().stream().map(Ricevuta.Riga::addebitoId).collect(Collectors.toSet());
        Serie s = serie.bloccata(r.getSerieId()).orElseThrow();
        if (!s.isAttiva()) throw new Conflitto("La serie «" + s.getNome() + "» è spenta: accendila per rifare la ricevuta.");
        Ricevuta nuova = crea(s, p, p.voci().stream().filter(v -> suoi.contains(v.addebitoId())).toList(), imp(), da);
        r.sostituitaDa(nuova.getId());
        return dto(List.of(nuova));
    }

    /** Annullato il pagamento, si annullano le sue ricevute. */
    @EventListener
    void pagamentoAnnullato(Pagamenti.PagamentoAnnullato e) {
        ricevute.findByPagamentoIdOrderByNumeroTestoAsc(e.pagamentoId()).stream().filter(Ricevuta::attiva)
            .forEach(r -> r.annulla("Pagamento annullato" + (vuoto(e.motivo()) ? "" : ": " + e.motivo()), e.da(), orologio.instant()));
    }

    // ── Consegna ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    byte[] pdf(UUID id) {
        Ricevuta r = ricevute.findById(id).orElseThrow(() -> new NonTrovato("Ricevuta non trovata."));
        Serie s = serie.findById(r.getSerieId()).orElseThrow();
        return PdfRicevuta.crea(r, s.getTipo(), societa.dati(), imp().isFirma());
    }

    @Transactional
    RicevutaDto stampata(UUID id) {
        Ricevuta r = ricevute.findById(id).orElseThrow(() -> new NonTrovato("Ricevuta non trovata."));
        r.stampata(orologio.instant());
        return dto(List.of(r)).getFirst();
    }

    /** Manda il PDF per email (di base a chi è intestata). */
    @Transactional
    Map<String, Object> invia(UUID id, Invio in, UUID da) {
        Ricevuta r = ricevute.findById(id).orElseThrow(() -> new NonTrovato("Ricevuta non trovata."));
        if (!r.attiva()) throw new Conflitto("La ricevuta è annullata.");
        String a = in != null && !vuoto(in.email()) ? in.email().strip() : r.getIntestatario().email();
        if (a != null && (a.length() > 320 || !a.contains("@"))) throw new IllegalArgumentException("L'indirizzo email non è valido.");
        Serie s = serie.findById(r.getSerieId()).orElseThrow();
        String chi = s.getTipo() == Serie.Tipo.FATTURA ? "Fattura" : "Ricevuta";
        String nomeSocieta = societa.nome();
        Posta.Esito esito = posta.invia(r.getIntestatario().id(), a, chi + " n. " + r.getNumeroTesto() + " · " + nomeSocieta,
            "Buongiorno,\n\nin allegato la " + chi.toLowerCase() + " n. " + r.getNumeroTesto() + " del " + DATA.format(r.getData()) + " ("
                + PdfRicevuta.euro(r.getTotale()) + ").\n\nGrazie,\n" + nomeSocieta, da,
            new Posta.Allegato(chi.toLowerCase() + "-" + r.getNumeroTesto().replaceAll("[^A-Za-z0-9-]", "-") + ".pdf", "application/pdf",
                PdfRicevuta.crea(r, s.getTipo(), societa.dati(), imp().isFirma())));
        if (esito.stato() == Posta.Stato.INVIATA) r.inviata(a, orologio.instant());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stato", esito.stato());
        out.put("errore", esito.errore());
        out.put("ricevuta", dto(List.of(r)).getFirst());
        return out;
    }

    // ── Elenchi ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<RicevutaDto> diPagamento(UUID pagamentoId) {
        return dto(ricevute.findByPagamentoIdOrderByNumeroTestoAsc(pagamentoId));
    }

    @Transactional(readOnly = true)
    List<RicevutaDto> diPersona(UUID personaId) {
        return dto(ricevute.diPersona(personaId, "[{\"id\":\"" + personaId + "\"}]", 200));
    }

    // ── Conversioni ─────────────────────────────────────────────────────────

    private List<RicevutaDto> dto(List<Ricevuta> rr) {
        Map<UUID, Serie> perId = serie.findAll().stream().collect(Collectors.toMap(Serie::getId, x -> x));
        Map<UUID, String> numeri = ricevute.findAllById(rr.stream().map(Ricevuta::getSostituitaDa).filter(Objects::nonNull).toList()).stream()
            .collect(Collectors.toMap(Ricevuta::getId, Ricevuta::getNumeroTesto));
        return rr.stream().map(r -> {
            Serie s = perId.get(r.getSerieId());
            return new RicevutaDto(r.getId(), r.getNumeroTesto(), s.getId(), s.getNome(), s.getTipo(), r.getData(), r.getPagamentoId(), r.getIntestatario(),
                r.getBeneficiari(), r.getRighe(), r.getTotale(), r.getMetodi(), r.getBollo(), r.getDicitura(), r.getCreataIl(), r.getAnnullataIl(), r.getMotivoAnnullamento(),
                r.getSostituitaDa(), numeri.get(r.getSostituitaDa()), r.getStampataIl(), r.getInviataIl(), r.getInviataA());
        }).toList();
    }

    private static ImpostazioniDto dto(Impostazioni i) {
        return new ImpostazioniDto(i.getIntestata(), i.isCodiciFiscali(), i.isBollo(), i.getBolloSoglia(), i.getBolloImporto(), i.getDicitura(), i.isFirma(),
            i.getDopo(), i.isEmail(), i.getFamiglia());
    }

    private SerieDto dto(Serie s, LocalDate oggi) {
        String periodo = periodo(s, oggi);
        return new SerieDto(s.getId(), s.getNome(), s.getTipo(), s.isAttiva(), s.getFormato(), s.getCifre(), s.getRiparte(), s.getProssimoNumero(), periodo,
            ricevute.countBySerieIdAndPeriodo(s.getId(), periodo), testo(s, stagioneDi(oggi), oggi, prossimo(s, periodo)));
    }

    private static boolean vuoto(String s) {
        return s == null || s.isBlank();
    }
}
