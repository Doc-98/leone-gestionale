package it.leonexiii.gestionale.listino;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.societa.Societa;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il listino e le regole di prezzo di ogni stagione (ADR 0018, parti 3 e 4). Calcola l'importo da proporre a chi
 * vende (prezzo per frequenza e gruppo, riduzione per iscrizione a stagione iniziata, rate) e il rimborso in caso di
 * recesso. Non decide: propone, e chi ha il permesso cambia l'importo scrivendo il motivo.
 */
@Service
public class Listino {

    public static final List<String> REGOLE = List.of("RIDUZIONE_TARDIVA", "RATE", "RECESSO", "SOSPENSIONE");

    public record Prezzo(Integer volte, UUID gruppoId, BigDecimal importo) {}

    public record OffertaDto(UUID id, UUID stagioneId, String nome, String tipo, String descrizione, Integer ingressi, Integer validitaMesi,
                             Integer validitaGiorni, UUID corsoId, List<UUID> corsi, boolean riduzioneTardiva, boolean attiva, int ordine,
                             List<Prezzo> prezzi) {}

    public record NuovaOfferta(UUID stagioneId, String nome, String tipo, String descrizione, Integer ingressi, Integer validitaMesi,
                               Integer validitaGiorni, UUID corsoId, List<UUID> corsi, Boolean riduzioneTardiva, Boolean attiva, Integer ordine,
                               List<Prezzo> prezzi) {}

    public record Regola(String tipo, boolean attiva, Map<String, Object> parametri) {}

    public record PaginaListino(Societa.Stagione stagione, List<OffertaDto> offerte, List<Regola> regole) {}

    /** Una rata: la prima (numero 1) si paga all'iscrizione; le altre alla scadenza (null = da concordare). */
    public record Rata(int numero, LocalDate scadenza, BigDecimal importo) {}

    /**
     * L'importo proposto: il prezzo del listino (per il gruppo, se più basso di quello di base), meno la riduzione per
     * l'iscrizione a stagione iniziata; diviso in rate se la regola è accesa. «al» è la fine naturale (stagione o validità).
     */
    public record Proposta(UUID offertaId, String offerta, String tipo, Integer volte, BigDecimal listino, BigDecimal prezzoBase, UUID gruppoId,
                           int riduzionePercentuale, BigDecimal riduzione, BigDecimal proposto, List<Rata> rate, LocalDate al) {}

    /** Il rimborso secondo la regola di recesso: percentuale di quanto pagato, e se va nel borsellino. */
    public record Rimborso(BigDecimal base, int percentuale, BigDecimal importo, boolean nelBorsellino, LocalDate finoAl) {}

    private final OffertaRepository offerte;
    private final RegolaPrezzoRepository regole;
    private final Societa societa;

    Listino(OffertaRepository offerte, RegolaPrezzoRepository regole, Societa societa) {
        this.offerte = offerte;
        this.regole = regole;
        this.societa = societa;
    }

    // ── Lettura e proposta ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaginaListino pagina(UUID stagioneId) {
        Societa.Stagione s = societa.stagione(stagioneId).orElseThrow(() -> new NonTrovato("Stagione non trovata."));
        Map<String, RegolaPrezzo> perTipo = new HashMap<>();
        regole.findByChiaveStagioneId(stagioneId).forEach(r -> perTipo.put(r.getChiave().tipo(), r));
        List<Regola> rr = REGOLE.stream().map(t -> perTipo.containsKey(t)
            ? new Regola(t, perTipo.get(t).isAttiva(), perTipo.get(t).getParametri()) : new Regola(t, false, Map.of())).toList();
        return new PaginaListino(s, offerte.findByStagioneIdOrderByOrdineAscNomeAsc(stagioneId).stream().map(Offerta::dto).toList(), rr);
    }

    @Transactional(readOnly = true)
    public OffertaDto offerta(UUID id) {
        return trova(id).dto();
    }

    @Transactional(readOnly = true)
    public Proposta proponi(UUID offertaId, Integer volte, Set<UUID> gruppi, LocalDate dal) {
        Offerta o = trova(offertaId);
        if (!o.isAttiva()) throw new IllegalArgumentException("L'offerta «" + o.getNome() + "» non è più attiva.");
        Societa.Stagione s = societa.stagione(o.getStagioneId()).orElseThrow(() -> new NonTrovato("Stagione non trovata."));
        Offerta.Scelta scelta = o.prezzoPer(Offerta.CORSO.equals(o.getTipo()) ? volte : null, gruppi == null ? Set.of() : gruppi)
            .orElseThrow(() -> new IllegalArgumentException("Nel listino non c'è un prezzo per questa frequenza."));
        BigDecimal listino = scelta.prezzo().importo();
        int perc = 0;
        if (o.isRiduzioneTardiva()) perc = regola(o.getStagioneId(), "RIDUZIONE_TARDIVA").map(r -> percentualeRiduzione(r, dal)).orElse(0);
        BigDecimal riduzione = listino.multiply(BigDecimal.valueOf(perc)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal proposto = listino.subtract(riduzione);
        List<Rata> rate = regola(o.getStagioneId(), "RATE").map(r -> rate(r, proposto, dal)).orElse(List.of());
        LocalDate al = o.getValiditaMesi() != null ? dal.plusMonths(o.getValiditaMesi()).minusDays(1)
            : o.getValiditaGiorni() != null ? dal.plusDays(o.getValiditaGiorni() - 1L)
            : Offerta.CARNET.equals(o.getTipo()) ? null : s.al();
        return new Proposta(o.getId(), o.getNome(), o.getTipo(), scelta.prezzo().volte(), listino, scelta.base(), scelta.prezzo().gruppoId(), perc,
            riduzione, proposto, rate, al);
    }

    /** Le rate di un importo diverso da quello proposto (es. scontato), con la regola della stagione dell'offerta. */
    @Transactional(readOnly = true)
    public List<Rata> rate(UUID offertaId, BigDecimal totale, LocalDate dal) {
        Offerta o = trova(offertaId);
        return regola(o.getStagioneId(), "RATE").map(r -> rate(r, totale, dal)).orElse(List.of());
    }

    /** Quanto si rimborsa recedendo in quella data (null se la regola di recesso della stagione è spenta). */
    @Transactional(readOnly = true)
    public Optional<Rimborso> recesso(UUID stagioneId, BigDecimal pagato, LocalDate data) {
        return regola(stagioneId, "RECESSO").map(r -> {
            List<Map<String, Object>> sc = scaglioni(r.getParametri(), "finoAl");
            int perc = 0;
            LocalDate fino = null;
            for (Map<String, Object> x : sc) {
                LocalDate f = data(x.get("finoAl"));
                if (!data.isAfter(f)) {
                    perc = intero(x.get("percentuale"));
                    fino = f;
                    break;
                }
            }
            BigDecimal base = pagato == null ? BigDecimal.ZERO : pagato;
            BigDecimal importo = base.multiply(BigDecimal.valueOf(perc)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return new Rimborso(base, perc, importo, Boolean.TRUE.equals(r.getParametri().get("nelBorsellino")), fino);
        });
    }

    /** La stagione a cui appartiene un'offerta. */
    @Transactional(readOnly = true)
    public UUID stagioneDi(UUID offertaId) {
        return trova(offertaId).getStagioneId();
    }

    // ── Gestione (permesso «Impostazioni della società») ──────────────────

    @Transactional
    OffertaDto crea(NuovaOfferta in) {
        if (in.stagioneId() == null || societa.stagione(in.stagioneId()).isEmpty()) throw new IllegalArgumentException("Scegli la stagione.");
        return offerte.saveAndFlush(new Offerta(in.stagioneId(), in)).dto();
    }

    @Transactional
    OffertaDto modifica(UUID id, NuovaOfferta in) {
        Offerta o = trova(id);
        o.modifica(in);
        offerte.flush();
        return o.dto();
    }

    @Transactional
    void elimina(UUID id) {
        offerte.delete(trova(id));
        offerte.flush();
    }

    @Transactional
    Regola modificaRegola(UUID stagioneId, String tipo, boolean attiva, Map<String, Object> parametri) {
        if (!REGOLE.contains(tipo)) throw new IllegalArgumentException("Regola sconosciuta: " + tipo);
        societa.stagione(stagioneId).orElseThrow(() -> new NonTrovato("Stagione non trovata."));
        Map<String, Object> puliti = valida(tipo, parametri == null ? Map.of() : parametri);
        RegolaPrezzo r = regole.findById(new RegolaPrezzo.Chiave(stagioneId, tipo)).orElseGet(() -> new RegolaPrezzo(stagioneId, tipo));
        r.modifica(attiva, puliti);
        regole.saveAndFlush(r);
        return new Regola(tipo, attiva, puliti);
    }

    /**
     * Copia offerte e regole da una stagione all'altra: i prezzi aumentano della percentuale (arrotondati al centesimo),
     * le date delle regole si spostano della distanza tra le due stagioni. Le offerte già presenti nella stagione nuova restano.
     */
    @Transactional
    int copia(UUID da, UUID a, BigDecimal aumentoPercentuale) {
        Societa.Stagione sDa = societa.stagione(da).orElseThrow(() -> new NonTrovato("Stagione di partenza non trovata."));
        Societa.Stagione sA = societa.stagione(a).orElseThrow(() -> new NonTrovato("Stagione di arrivo non trovata."));
        if (da.equals(a)) throw new IllegalArgumentException("Scegli due stagioni diverse.");
        BigDecimal fattore = BigDecimal.ONE.add((aumentoPercentuale == null ? BigDecimal.ZERO : aumentoPercentuale).divide(BigDecimal.valueOf(100)));
        if (fattore.signum() <= 0) throw new IllegalArgumentException("Aumento non valido.");
        Period salto = Period.between(sDa.dal(), sA.dal());
        int n = 0;
        for (Offerta o : offerte.findByStagioneIdOrderByOrdineAscNomeAsc(da)) {
            OffertaDto d = o.dto();
            List<Prezzo> prezzi = d.prezzi().stream().map(p -> new Prezzo(p.volte(), p.gruppoId(), p.importo().multiply(fattore).setScale(2, RoundingMode.HALF_UP))).toList();
            offerte.save(new Offerta(a, new NuovaOfferta(a, d.nome(), d.tipo(), d.descrizione(), d.ingressi(), d.validitaMesi(), d.validitaGiorni(),
                d.corsoId(), d.corsi(), d.riduzioneTardiva(), d.attiva(), d.ordine(), prezzi)));
            n++;
        }
        for (RegolaPrezzo r : regole.findByChiaveStagioneId(da)) {
            Map<String, Object> p = sposta(r.getParametri(), salto);
            RegolaPrezzo nuova = regole.findById(new RegolaPrezzo.Chiave(a, r.getChiave().tipo())).orElseGet(() -> new RegolaPrezzo(a, r.getChiave().tipo()));
            nuova.modifica(r.isAttiva(), p);
            regole.save(nuova);
        }
        offerte.flush();
        return n;
    }

    // ── Regole ────────────────────────────────────────────────────────────

    private Optional<RegolaPrezzo> regola(UUID stagioneId, String tipo) {
        return regole.findById(new RegolaPrezzo.Chiave(stagioneId, tipo)).filter(RegolaPrezzo::isAttiva);
    }

    /** La percentuale dell'ultimo scaglione iniziato entro la data d'inizio. */
    static int percentualeRiduzione(RegolaPrezzo r, LocalDate dal) {
        int perc = 0;
        for (Map<String, Object> x : scaglioni(r.getParametri(), "dal"))
            if (!dal.isBefore(data(x.get("dal")))) perc = intero(x.get("percentuale"));
        return perc;
    }

    /**
     * La prima rata è la percentuale indicata (all'iscrizione); il resto in parti uguali, l'ultima prende i centesimi. Le
     * rate con la scadenza già passata alla data d'inizio si pagano subito, con la prima; se restano tutte lì, niente rate.
     */
    static List<Rata> rate(RegolaPrezzo r, BigDecimal totale, LocalDate dal) {
        int numero = intero(r.getParametri().get("numero"));
        if (numero < 2) return List.of();
        int primaPerc = intero(r.getParametri().get("primaPercentuale"));
        @SuppressWarnings("unchecked")
        List<Object> scad = (List<Object>) r.getParametri().getOrDefault("scadenze", List.of());
        BigDecimal prima = totale.multiply(BigDecimal.valueOf(primaPerc)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal resto = totale.subtract(prima);
        BigDecimal parte = resto.divide(BigDecimal.valueOf(numero - 1), 2, RoundingMode.DOWN);
        List<Rata> out = new ArrayList<>();
        out.add(new Rata(1, null, prima));
        for (int i = 2; i <= numero; i++) {
            BigDecimal imp = i == numero ? resto.subtract(parte.multiply(BigDecimal.valueOf(numero - 2))) : parte;
            LocalDate s = scad.size() >= i - 1 && scad.get(i - 2) != null ? data(scad.get(i - 2)) : null;
            out.add(new Rata(i, s, imp));
        }
        BigDecimal subito = out.stream().filter(x -> x.numero() == 1 || (x.scadenza() != null && x.scadenza().isBefore(dal))).map(Rata::importo)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Rata> dopo = out.stream().filter(x -> x.numero() > 1 && (x.scadenza() == null || !x.scadenza().isBefore(dal))).toList();
        if (dopo.isEmpty()) return List.of();
        List<Rata> finali = new ArrayList<>();
        finali.add(new Rata(1, null, subito));
        for (int i = 0; i < dopo.size(); i++) finali.add(new Rata(i + 2, dopo.get(i).scadenza(), dopo.get(i).importo()));
        return finali;
    }

    /** Controlla i parametri di una regola e li rimette in ordine (scaglioni per data). */
    static Map<String, Object> valida(String tipo, Map<String, Object> p) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (tipo) {
            case "RIDUZIONE_TARDIVA" -> out.put("scaglioni", pulisciScaglioni(p, "dal"));
            case "RECESSO" -> {
                out.put("scaglioni", pulisciScaglioni(p, "finoAl"));
                out.put("nelBorsellino", Boolean.TRUE.equals(p.get("nelBorsellino")));
            }
            case "RATE" -> {
                int numero = intero(p.getOrDefault("numero", 2));
                int prima = intero(p.getOrDefault("primaPercentuale", 50));
                if (numero < 2 || numero > 12) throw new IllegalArgumentException("Le rate vanno da 2 a 12.");
                if (prima < 1 || prima > 99) throw new IllegalArgumentException("La prima rata è una percentuale tra 1 e 99.");
                @SuppressWarnings("unchecked")
                List<Object> scad = (List<Object>) p.getOrDefault("scadenze", List.of());
                if (scad.size() > numero - 1) throw new IllegalArgumentException("Ci sono più scadenze che rate.");
                List<String> date = scad.stream().map(x -> x == null || String.valueOf(x).isBlank() ? null : data(x).toString()).toList();
                out.put("numero", numero);
                out.put("primaPercentuale", prima);
                out.put("scadenze", date);
            }
            case "SOSPENSIONE" -> {
                int min = intero(p.getOrDefault("settimaneMinime", 6));
                int cert = intero(p.getOrDefault("settimaneCertificato", 4));
                if (min < 0 || min > 52 || cert < 0 || cert > 52) throw new IllegalArgumentException("Le settimane vanno da 0 a 52.");
                out.put("settimaneMinime", min);
                out.put("settimaneCertificato", cert);
            }
            default -> throw new IllegalArgumentException("Regola sconosciuta: " + tipo);
        }
        return out;
    }

    private static List<Map<String, Object>> pulisciScaglioni(Map<String, Object> p, String campo) {
        List<Map<String, Object>> sc = scaglioni(p, campo);
        if (sc.size() > 24) throw new IllegalArgumentException("Al massimo 24 scaglioni.");
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate prima = null;
        for (Map<String, Object> x : sc) {
            LocalDate d = data(x.get(campo));
            int perc = intero(x.get("percentuale"));
            if (perc < 0 || perc > 100) throw new IllegalArgumentException("Le percentuali vanno da 0 a 100.");
            if (d.equals(prima)) throw new IllegalArgumentException("Due scaglioni con la stessa data.");
            prima = d;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(campo, d.toString());
            m.put("percentuale", perc);
            out.add(m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> scaglioni(Map<String, Object> p, String campo) {
        Object v = p.get("scaglioni");
        if (v == null) return List.of();
        if (!(v instanceof List<?> l)) throw new IllegalArgumentException("Gli scaglioni sono un elenco.");
        return l.stream().map(x -> {
                if (!(x instanceof Map<?, ?> m)) throw new IllegalArgumentException("Scaglione non valido.");
                return (Map<String, Object>) m;
            })
            .sorted(Comparator.comparing(m -> data(m.get(campo)))).toList();
    }

    private static Map<String, Object> sposta(Map<String, Object> p, Period salto) {
        Map<String, Object> out = new LinkedHashMap<>();
        p.forEach((k, v) -> {
            if (v instanceof List<?> l) out.put(k, l.stream().map(x -> {
                if (x instanceof Map<?, ?> m) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    m.forEach((kk, vv) -> mm.put(String.valueOf(kk), ("dal".equals(kk) || "finoAl".equals(kk)) && vv != null ? data(vv).plus(salto).toString() : vv));
                    return mm;
                }
                return x == null ? null : data(x).plus(salto).toString();
            }).toList());
            else out.put(k, v);
        });
        return out;
    }

    private static LocalDate data(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data non valida: " + v + " (formato 2026-11-01).");
        }
    }

    private static int intero(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numero non valido: " + v);
        }
    }

    private Offerta trova(UUID id) {
        return offerte.findById(id).orElseThrow(() -> new NonTrovato("Offerta non trovata."));
    }
}
