package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** La parte di un pagamento fatta con un metodo (es. 50 € in contanti); «incassatoIl» vuoto = ancora da incassare. */
@Entity
@Table(name = "pagamento_metodo")
class PagamentoMetodo {

    @Id
    private UUID id;
    /** Il metodo, finché esiste: se viene eliminato si svuota e resta la copia. */
    @Column(name = "metodo_id")
    private UUID metodoId;
    /** Il metodo com'era quando si è registrato il pagamento: lo storico legge questa. */
    @jakarta.persistence.Embedded
    private CopiaMetodo copia;
    @Column(nullable = false)
    private BigDecimal importo;
    @Column(name = "incassato_il")
    private LocalDate incassatoIl;
    @Column(name = "incassato_da")
    private UUID incassatoDa;
    private String riferimento;

    protected PagamentoMetodo() {}

    PagamentoMetodo(MetodoPagamento metodo, BigDecimal importo, LocalDate incassatoIl, String riferimento) {
        this.id = UUID.randomUUID();
        this.metodoId = metodo.getId();
        this.copia = new CopiaMetodo(metodo);
        this.importo = importo;
        this.incassatoIl = incassatoIl;
        this.riferimento = riferimento == null || riferimento.isBlank() ? null : riferimento.strip();
    }

    void incassa(LocalDate il, UUID da) {
        if (incassatoIl != null) throw new IllegalArgumentException("È già segnato incassato.");
        this.incassatoIl = il;
        this.incassatoDa = da;
    }

    UUID getId() { return id; }
    UUID getMetodoId() { return metodoId; }
    CopiaMetodo getCopia() { return copia; }
    BigDecimal getImporto() { return importo; }
    LocalDate getIncassatoIl() { return incassatoIl; }
    String getRiferimento() { return riferimento; }
}
