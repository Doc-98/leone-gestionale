package it.leonexiii.gestionale.ricevute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una ricevuta fatta da un pagamento. Tutto quello che c'è scritto è copiato al momento (intestatario, beneficiari,
 * righe, dicitura), così non cambia se poi cambiano i dati; si annulla, non si cancella.
 */
@Entity
@Table(name = "ricevuta")
class Ricevuta {

    /** Una persona come compare sulla ricevuta. */
    public record Soggetto(UUID id, String nome, String cognome, String codiceFiscale, String indirizzo, LocalDate dataNascita, String email) {
        String nomeCompleto() { return nome + " " + cognome; }
    }

    /** Una riga: cosa, per chi (il beneficiario), quanto; l'addebito serve per rifarla uguale. */
    public record Riga(UUID addebitoId, UUID personaId, String descrizione, String per, BigDecimal importo) {}

    @Id
    private UUID id;
    @Column(name = "serie_id", nullable = false)
    private UUID serieId;
    @Column(nullable = false)
    private String periodo;
    private int numero;
    @Column(name = "numero_testo", nullable = false)
    private String numeroTesto;
    @Column(name = "pagamento_id", nullable = false)
    private UUID pagamentoId;
    @Column(nullable = false)
    private LocalDate data;
    @Column(name = "intestatario_id", nullable = false)
    private UUID intestatarioId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Soggetto intestatario;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Soggetto> beneficiari;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Riga> righe;
    @Column(nullable = false)
    private BigDecimal totale;
    @Column(nullable = false)
    private String metodi;
    private BigDecimal bollo;
    private String dicitura;
    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;
    @Column(name = "creata_da")
    private UUID creataDa;
    @Column(name = "annullata_il")
    private Instant annullataIl;
    @Column(name = "annullata_da")
    private UUID annullataDa;
    @Column(name = "motivo_annullamento")
    private String motivoAnnullamento;
    @Column(name = "sostituita_da")
    private UUID sostituitaDa;
    @Column(name = "stampata_il")
    private Instant stampataIl;
    @Column(name = "inviata_il")
    private Instant inviataIl;
    @Column(name = "inviata_a")
    private String inviataA;

    protected Ricevuta() {}

    Ricevuta(Serie serie, String periodo, int numero, String numeroTesto, UUID pagamentoId, LocalDate data, Soggetto intestatario,
             List<Soggetto> beneficiari, List<Riga> righe, String metodi, BigDecimal bollo, String dicitura, UUID da, Instant ora) {
        this.id = UUID.randomUUID();
        this.serieId = serie.getId();
        this.periodo = periodo;
        this.numero = numero;
        this.numeroTesto = numeroTesto;
        this.pagamentoId = pagamentoId;
        this.data = data;
        this.intestatarioId = intestatario.id();
        this.intestatario = intestatario;
        this.beneficiari = beneficiari;
        this.righe = righe;
        this.totale = righe.stream().map(Riga::importo).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.metodi = metodi;
        this.bollo = bollo;
        this.dicitura = dicitura;
        this.creataDa = da;
        this.creataIl = ora;
    }

    void annulla(String motivo, UUID da, Instant ora) {
        if (annullataIl != null) throw new IllegalArgumentException("La ricevuta è già annullata.");
        this.annullataIl = ora;
        this.annullataDa = da;
        this.motivoAnnullamento = motivo;
    }

    void sostituitaDa(UUID nuova) { this.sostituitaDa = nuova; }

    void stampata(Instant ora) { this.stampataIl = ora; }

    void inviata(String a, Instant ora) {
        this.inviataA = a;
        this.inviataIl = ora;
    }

    boolean attiva() { return annullataIl == null; }

    UUID getId() { return id; }
    UUID getSerieId() { return serieId; }
    String getPeriodo() { return periodo; }
    int getNumero() { return numero; }
    String getNumeroTesto() { return numeroTesto; }
    UUID getPagamentoId() { return pagamentoId; }
    LocalDate getData() { return data; }
    Soggetto getIntestatario() { return intestatario; }
    List<Soggetto> getBeneficiari() { return beneficiari; }
    List<Riga> getRighe() { return righe; }
    BigDecimal getTotale() { return totale; }
    String getMetodi() { return metodi; }
    BigDecimal getBollo() { return bollo; }
    String getDicitura() { return dicitura; }
    Instant getCreataIl() { return creataIl; }
    Instant getAnnullataIl() { return annullataIl; }
    String getMotivoAnnullamento() { return motivoAnnullamento; }
    UUID getSostituitaDa() { return sostituitaDa; }
    Instant getStampataIl() { return stampataIl; }
    Instant getInviataIl() { return inviataIl; }
    String getInviataA() { return inviataA; }
}
