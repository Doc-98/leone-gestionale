package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un ingresso scalato da un carnet. */
@Entity
@Table(name = "ingresso_carnet")
class IngressoCarnet {

    @Id
    private UUID id;
    @Column(name = "abbonamento_id", nullable = false)
    private UUID abbonamentoId;
    @Column(nullable = false)
    private Instant istante;
    @Column(name = "passaggio_id")
    private UUID passaggioId;

    protected IngressoCarnet() {}

    IngressoCarnet(UUID abbonamentoId, Instant istante, UUID passaggioId) {
        this.id = UUID.randomUUID();
        this.abbonamentoId = abbonamentoId;
        this.istante = istante;
        this.passaggioId = passaggioId;
    }

    UUID getAbbonamentoId() { return abbonamentoId; }
    Instant getIstante() { return istante; }
}
