package it.leonexiii.gestionale.listino;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Una riga del listino di una stagione: abbonamento a uno o più corsi, carnet, o altro importo (ADR 0018). */
@Entity
@Table(name = "offerta")
class Offerta {

    static final String CORSO = "CORSO";
    static final String CARNET = "CARNET";
    static final String ALTRO = "ALTRO";

    /** Un prezzo: per frequenza (1–7, 0 = open, null = unico) e facoltativamente per gruppo di clienti. */
    @Embeddable
    static class Prezzo {
        private Short volte;
        @Column(name = "gruppo_id")
        private UUID gruppoId;
        @Column(nullable = false)
        private BigDecimal importo;

        protected Prezzo() {}

        Prezzo(Integer volte, UUID gruppoId, BigDecimal importo) {
            if (volte != null && (volte < 0 || volte > 7)) throw new IllegalArgumentException("La frequenza va da 1 a 7 volte a settimana (0 = open).");
            if (importo == null || importo.signum() < 0) throw new IllegalArgumentException("Ogni prezzo ha un importo, non negativo.");
            this.volte = volte == null ? null : volte.shortValue();
            this.gruppoId = gruppoId;
            this.importo = importo;
        }

        Integer volte() { return volte == null ? null : volte.intValue(); }
        UUID gruppoId() { return gruppoId; }
        BigDecimal importo() { return importo; }
    }

    @Id
    private UUID id;
    @Column(name = "stagione_id", nullable = false)
    private UUID stagioneId;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String tipo;
    private String descrizione;
    private Short ingressi;
    @Column(name = "validita_mesi")
    private Short validitaMesi;
    @Column(name = "validita_giorni")
    private Short validitaGiorni;
    @Column(name = "corso_id")
    private UUID corsoId;
    @Column(name = "riduzione_tardiva", nullable = false)
    private boolean riduzioneTardiva;
    @Column(nullable = false)
    private boolean attiva;
    @Column(nullable = false)
    private int ordine;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "offerta_corso", joinColumns = @JoinColumn(name = "offerta_id"))
    @Column(name = "corso_id")
    private Set<UUID> corsi = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "offerta_prezzo", joinColumns = @JoinColumn(name = "offerta_id"))
    private List<Prezzo> prezzi = new ArrayList<>();

    protected Offerta() {}

    Offerta(UUID stagioneId, Listino.NuovaOfferta in) {
        this.id = UUID.randomUUID();
        this.stagioneId = stagioneId;
        modifica(in);
    }

    void modifica(Listino.NuovaOfferta in) {
        if (in.nome() == null || in.nome().isBlank() || in.nome().strip().length() > 120) throw new IllegalArgumentException("Dai un nome all'offerta.");
        String t = in.tipo() == null ? "" : in.tipo();
        if (!List.of(CORSO, CARNET, ALTRO).contains(t)) throw new IllegalArgumentException("Tipo di offerta: CORSO, CARNET o ALTRO.");
        if (in.validitaMesi() != null && in.validitaGiorni() != null) throw new IllegalArgumentException("La validità si dà in mesi oppure in giorni, non tutte e due.");
        List<Prezzo> nuovi = (in.prezzi() == null ? List.<Listino.Prezzo>of() : in.prezzi()).stream()
            .map(p -> new Prezzo(CORSO.equals(t) ? p.volte() : null, p.gruppoId(), p.importo())).toList();
        if (nuovi.isEmpty()) throw new IllegalArgumentException("Dai almeno un prezzo.");
        Set<String> chiavi = new HashSet<>();
        for (Prezzo p : nuovi)
            if (!chiavi.add(p.volte() + "|" + p.gruppoId())) throw new IllegalArgumentException("Due prezzi per la stessa frequenza (e lo stesso gruppo).");
        Set<UUID> corsiNuovi = in.corsi() == null ? Set.of() : new LinkedHashSet<>(in.corsi());
        switch (t) {
            case CORSO -> {
                if (corsiNuovi.isEmpty()) throw new IllegalArgumentException("Scegli almeno un corso a cui vale l'abbonamento.");
                if (in.ingressi() != null) throw new IllegalArgumentException("Gli ingressi sono solo per i carnet.");
            }
            case CARNET -> {
                if (in.ingressi() == null || in.ingressi() < 1 || in.ingressi() > 500) throw new IllegalArgumentException("Quanti ingressi ha il carnet (da 1 a 500)?");
                corsiNuovi = Set.of();
            }
            default -> {
                if (in.ingressi() != null) throw new IllegalArgumentException("Gli ingressi sono solo per i carnet.");
                corsiNuovi = Set.of();
            }
        }
        this.nome = in.nome().strip();
        this.tipo = t;
        this.descrizione = in.descrizione() == null || in.descrizione().isBlank() ? null : in.descrizione().strip();
        this.ingressi = CARNET.equals(t) ? in.ingressi().shortValue() : null;
        this.validitaMesi = in.validitaMesi() == null ? null : in.validitaMesi().shortValue();
        this.validitaGiorni = in.validitaGiorni() == null ? null : in.validitaGiorni().shortValue();
        this.corsoId = CARNET.equals(t) ? in.corsoId() : null;
        this.riduzioneTardiva = Boolean.TRUE.equals(in.riduzioneTardiva()) && !ALTRO.equals(t);
        this.attiva = in.attiva() == null || in.attiva();
        this.ordine = in.ordine() == null ? 0 : in.ordine();
        this.corsi.clear();
        this.corsi.addAll(corsiNuovi);
        this.prezzi.clear();
        this.prezzi.addAll(nuovi);
    }

    /** Il prezzo per la frequenza: il più basso tra quelli dei gruppi della persona, altrimenti quello di base. */
    Optional<Scelta> prezzoPer(Integer volte, Set<UUID> gruppi) {
        List<Prezzo> perFrequenza = prezzi.stream().filter(p -> Objects.equals(p.volte(), volte)).toList();
        if (perFrequenza.isEmpty()) perFrequenza = prezzi.stream().filter(p -> p.volte() == null).toList();
        var diGruppo = perFrequenza.stream().filter(p -> p.gruppoId() != null && gruppi.contains(p.gruppoId()))
            .min(java.util.Comparator.comparing(Prezzo::importo));
        var base = perFrequenza.stream().filter(p -> p.gruppoId() == null).findFirst();
        if (diGruppo.isPresent() && (base.isEmpty() || diGruppo.get().importo().compareTo(base.get().importo()) < 0))
            return Optional.of(new Scelta(diGruppo.get(), base.map(Prezzo::importo).orElse(null)));
        return base.map(b -> new Scelta(b, null));
    }

    record Scelta(Prezzo prezzo, BigDecimal base) {}

    Listino.OffertaDto dto() {
        return new Listino.OffertaDto(id, stagioneId, nome, tipo, descrizione, ingressi == null ? null : ingressi.intValue(),
            validitaMesi == null ? null : validitaMesi.intValue(), validitaGiorni == null ? null : validitaGiorni.intValue(), corsoId,
            corsi.stream().sorted().toList(), riduzioneTardiva, attiva, ordine,
            prezzi.stream().map(p -> new Listino.Prezzo(p.volte(), p.gruppoId(), p.importo()))
                .sorted(java.util.Comparator.comparing((Listino.Prezzo p) -> p.gruppoId() == null ? "" : p.gruppoId().toString())
                    .thenComparing(p -> p.volte() == null ? -1 : p.volte() == 0 ? 99 : p.volte()))
                .toList());
    }

    UUID getId() { return id; }
    UUID getStagioneId() { return stagioneId; }
    String getTipo() { return tipo; }
    String getNome() { return nome; }
    boolean isAttiva() { return attiva; }
    boolean isRiduzioneTardiva() { return riduzioneTardiva; }
    Integer getValiditaMesi() { return validitaMesi == null ? null : validitaMesi.intValue(); }
    Integer getValiditaGiorni() { return validitaGiorni == null ? null : validitaGiorni.intValue(); }
}
