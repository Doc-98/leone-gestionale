package it.leonexiii.gestionale.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    protected Postazione() {}

    UUID getId() { return id; }
    Mansione getMansione() { return mansione; }
    UUID getSpazioId() { return spazioId; }
    String getNome() { return nome; }
    int getOrdine() { return ordine; }
    boolean isAttiva() { return attiva; }
}
