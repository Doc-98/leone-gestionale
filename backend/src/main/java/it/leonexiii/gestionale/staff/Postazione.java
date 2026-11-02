package it.leonexiii.gestionale.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Es. «Spogliatoio M · postazione 2»: le righe dell'attuale foglio turni. */
@Entity
@Table(name = "postazione")
class Postazione {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mansione_id", nullable = false)
    private Mansione mansione;

    @Column(name = "spazio_id")
    private UUID spazioId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private int ordine;

    @Column(nullable = false)
    private boolean attiva;

    /** Riservata a un sesso («M» o «F», es. lo spogliatoio femminile); null = chiunque. */
    @Column(name = "solo_sesso")
    private String soloSesso;

    /** Quante ore a settimana va coperta (null = non indicato): la pianificazione avvisa se ne pianifica meno. */
    @Column(name = "ore_settimanali")
    private BigDecimal oreSettimanali;

    protected Postazione() {}

    Postazione(Mansione mansione, String nome, UUID spazioId, int ordine) {
        this.id = UUID.randomUUID();
        this.mansione = mansione;
        this.ordine = ordine;
        this.attiva = true;
        modifica(nome, spazioId, true);
    }

    void modifica(String nome, UUID spazioId, boolean attiva) {
        this.nome = Testi.obbligatorio(nome, "il nome della postazione", 120);
        this.spazioId = spazioId;
        this.attiva = attiva;
    }

    void riservaA(String sesso) {
        if (sesso != null && sesso.isBlank()) sesso = null;
        if (sesso != null && !sesso.equals("M") && !sesso.equals("F")) throw new IllegalArgumentException("Il sesso va indicato come M o F.");
        this.soloSesso = sesso;
    }

    /** La persona, con il sesso indicato in anagrafica, può lavorare qui? Senza sesso in anagrafica no, se la postazione è riservata. */
    boolean ammette(String sesso) {
        return soloSesso == null || soloSesso.equals(sesso);
    }

    void oreSettimanali(BigDecimal ore) {
        if (ore != null && (ore.signum() <= 0 || ore.compareTo(BigDecimal.valueOf(168)) > 0))
            throw new IllegalArgumentException("Le ore a settimana vanno da più di 0 a 168.");
        if (ore != null && ore.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Le ore a settimana hanno al massimo due decimali.");
        this.oreSettimanali = ore;
    }

    void ordina(int ordine) {
        this.ordine = ordine;
    }

    UUID getId() { return id; }
    Mansione getMansione() { return mansione; }
    UUID getSpazioId() { return spazioId; }
    String getNome() { return nome; }
    int getOrdine() { return ordine; }
    boolean isAttiva() { return attiva; }
    String getSoloSesso() { return soloSesso; }
    BigDecimal getOreSettimanali() { return oreSettimanali; }
}
