package it.leonexiii.gestionale.servizi;

import it.leonexiii.gestionale.condiviso.Conflitto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una ricarica scritta sul braccialetto, in due tempi (ADR 0025): preparata quando il server ha calcolato la memoria
 * nuova, confermata quando il PC l'ha scritta e riletta (solo allora si registra il pagamento), o annullata.
 */
@Entity
@Table(name = "scrittura_braccialetto")
class ScritturaBraccialetto {

    enum Stato { PREPARATA, CONFERMATA, ANNULLATA }

    @Id
    private UUID id;
    @Column(name = "braccialetto_id", nullable = false)
    private UUID braccialettoId;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(name = "crediti_prima", nullable = false)
    private int creditiPrima;
    @Column(name = "crediti_dopo", nullable = false)
    private int creditiDopo;
    @Column(nullable = false)
    private BigDecimal importo;
    @Column(nullable = false)
    private String stato;
    @Column(name = "pagamento_id")
    private UUID pagamentoId;
    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;
    @Column(name = "creata_da")
    private UUID creataDa;
    @Column(name = "chiusa_il")
    private Instant chiusaIl;

    protected ScritturaBraccialetto() {}

    ScritturaBraccialetto(UUID braccialettoId, UUID personaId, int creditiPrima, int creditiDopo, BigDecimal importo, Instant ora, UUID da) {
        this.id = UUID.randomUUID();
        this.braccialettoId = braccialettoId;
        this.personaId = personaId;
        this.creditiPrima = creditiPrima;
        this.creditiDopo = creditiDopo;
        this.importo = importo;
        this.stato = Stato.PREPARATA.name();
        this.creataIl = ora;
        this.creataDa = da;
    }

    boolean aperta() {
        return Stato.PREPARATA.name().equals(stato);
    }

    void conferma(UUID pagamentoId, Instant ora) {
        if (!aperta()) throw new Conflitto("Questa ricarica è già " + (Stato.CONFERMATA.name().equals(stato) ? "registrata." : "annullata."));
        this.stato = Stato.CONFERMATA.name();
        this.pagamentoId = pagamentoId;
        this.chiusaIl = ora;
    }

    void annulla(Instant ora) {
        if (!aperta()) throw new Conflitto("Questa ricarica è già " + (Stato.CONFERMATA.name().equals(stato) ? "registrata." : "annullata."));
        this.stato = Stato.ANNULLATA.name();
        this.chiusaIl = ora;
    }

    UUID getId() { return id; }
    UUID getBraccialettoId() { return braccialettoId; }
    UUID getPersonaId() { return personaId; }
    int getCreditiPrima() { return creditiPrima; }
    int getCreditiDopo() { return creditiDopo; }
    BigDecimal getImporto() { return importo; }
    Instant getCreataIl() { return creataIl; }
}
