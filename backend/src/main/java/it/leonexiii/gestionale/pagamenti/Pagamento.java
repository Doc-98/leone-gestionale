package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un pagamento: chi paga, quando, con quali metodi e quali addebiti salda (anche di più familiari). */
@Entity
@Table(name = "pagamento")
class Pagamento {

    /** Quanto di un addebito paga questo pagamento. */
    @Embeddable
    static class Voce {
        @Column(name = "addebito_id", nullable = false)
        private UUID addebitoId;
        @Column(nullable = false)
        private BigDecimal importo;

        protected Voce() {}

        Voce(UUID addebitoId, BigDecimal importo) {
            this.addebitoId = addebitoId;
            this.importo = importo;
        }

        UUID getAddebitoId() { return addebitoId; }
        BigDecimal getImporto() { return importo; }
    }

    @Id
    private UUID id;
    @Column(name = "pagante_id", nullable = false)
    private UUID paganteId;
    @Column(nullable = false)
    private LocalDate data;
    @Column(nullable = false)
    private BigDecimal importo;
    private String note;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;
    @Column(name = "annullato_da")
    private UUID annullatoDa;
    @Column(name = "motivo_annullamento")
    private String motivoAnnullamento;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private List<PagamentoMetodo> metodi = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pagamento_addebito", joinColumns = @JoinColumn(name = "pagamento_id"))
    private List<Voce> voci = new ArrayList<>();

    protected Pagamento() {}

    Pagamento(UUID paganteId, LocalDate data, BigDecimal importo, String note, UUID registratoDa, Instant ora) {
        this.id = UUID.randomUUID();
        this.paganteId = paganteId;
        this.data = data;
        this.importo = importo;
        this.note = note == null || note.isBlank() ? null : note.strip();
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    void aggiungiMetodo(PagamentoMetodo m) { metodi.add(m); }

    void aggiungiVoce(UUID addebitoId, BigDecimal importo) { voci.add(new Voce(addebitoId, importo)); }

    void annulla(String motivo, UUID da, Instant ora) {
        if (annullatoIl != null) throw new IllegalArgumentException("Il pagamento è già annullato.");
        this.annullatoIl = ora;
        this.annullatoDa = da;
        this.motivoAnnullamento = motivo;
    }

    boolean attivo() { return annullatoIl == null; }

    UUID getId() { return id; }
    UUID getPaganteId() { return paganteId; }
    LocalDate getData() { return data; }
    BigDecimal getImporto() { return importo; }
    String getNote() { return note; }
    UUID getRegistratoDa() { return registratoDa; }
    Instant getRegistratoIl() { return registratoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
    String getMotivoAnnullamento() { return motivoAnnullamento; }
    List<PagamentoMetodo> getMetodi() { return metodi; }
    List<Voce> getVoci() { return voci; }
}
