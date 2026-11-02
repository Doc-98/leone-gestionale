package it.leonexiii.gestionale.servizi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * L'ultimo saldo visto su un braccialetto (ADR 0025). Con le macchine non collegate i crediti veri sono nella memoria
 * del braccialetto e possono solo essere scesi da allora: questo è un massimo, non il saldo di adesso.
 */
@Entity
@Table(name = "saldo_braccialetto")
class SaldoBraccialetto {

    enum Fonte { LETTURA, SCRITTURA, A_MANO }

    @Id
    @Column(name = "braccialetto_id")
    private UUID braccialettoId;
    @Column(nullable = false)
    private int crediti;
    @Column(nullable = false)
    private String fonte;
    @Column(name = "aggiornato_il", nullable = false)
    private Instant aggiornatoIl;
    @Column(name = "aggiornato_da")
    private UUID aggiornatoDa;

    protected SaldoBraccialetto() {}

    SaldoBraccialetto(UUID braccialettoId) {
        this.braccialettoId = braccialettoId;
    }

    void aggiorna(int crediti, Fonte fonte, Instant ora, UUID da) {
        if (crediti < 0) throw new IllegalArgumentException("I crediti non possono essere negativi.");
        this.crediti = crediti;
        this.fonte = fonte.name();
        this.aggiornatoIl = ora;
        this.aggiornatoDa = da;
    }

    UUID getBraccialettoId() { return braccialettoId; }
    int getCrediti() { return crediti; }
    String getFonte() { return fonte; }
    Instant getAggiornatoIl() { return aggiornatoIl; }
}
