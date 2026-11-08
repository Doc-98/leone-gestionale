package it.leonexiii.gestionale.cassa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Soldi tolti o messi a mano nel cassetto: versamento (banca, cassaforte), piccola spesa, aggiunta (es. monete). */
@Entity
@Table(name = "movimento_cassa")
class MovimentoCassa {

    enum Tipo { VERSAMENTO, SPESA, AGGIUNTA }

    @Id
    private UUID id;
    @Column(name = "sessione_id", nullable = false)
    private UUID sessioneId;
    @Column(nullable = false)
    private String tipo;
    @Column(nullable = false)
    private BigDecimal importo;
    @Column(nullable = false)
    private String descrizione;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;

    protected MovimentoCassa() {}

    MovimentoCassa(UUID sessioneId, Tipo tipo, BigDecimal importo, String descrizione, UUID da, Instant ora) {
        this.id = UUID.randomUUID();
        this.sessioneId = sessioneId;
        this.tipo = tipo.name();
        this.importo = importo;
        this.descrizione = descrizione;
        this.registratoDa = da;
        this.registratoIl = ora;
    }

    void annulla(Instant ora) {
        if (annullatoIl != null) throw new IllegalArgumentException("Il movimento è già annullato.");
        this.annullatoIl = ora;
    }

    /** Di quanto cambia il cassetto: i versamenti e le spese tolgono, le aggiunte mettono. */
    BigDecimal effetto() {
        return Tipo.valueOf(tipo) == Tipo.AGGIUNTA ? importo : importo.negate();
    }

    UUID getId() { return id; }
    UUID getSessioneId() { return sessioneId; }
    Tipo getTipo() { return Tipo.valueOf(tipo); }
    BigDecimal getImporto() { return importo; }
    String getDescrizione() { return descrizione; }
    UUID getRegistratoDa() { return registratoDa; }
    Instant getRegistratoIl() { return registratoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
}
