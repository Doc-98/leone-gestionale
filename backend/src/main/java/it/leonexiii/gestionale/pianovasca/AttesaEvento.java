package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Una riga dello storico delle liste d'attesa: cosa è successo a una voce, quando e chi l'ha fatto. */
@Entity
@Table(name = "attesa_evento")
class AttesaEvento {

    enum Tipo { AGGIUNTA, AVVISATA, AVVISATA_A_VOCE, SENZA_RISPOSTA, CONFERMA_CHIESTA, CONFERMATA, ISCRITTA, RINUNCIA }

    @Id
    private UUID id;

    @Column(name = "attesa_id", nullable = false)
    private UUID attesaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private Instant quando;

    private UUID da;

    protected AttesaEvento() {}

    AttesaEvento(UUID attesaId, Tipo tipo, Instant quando, UUID da) {
        this.id = UUID.randomUUID();
        this.attesaId = attesaId;
        this.tipo = tipo;
        this.quando = quando;
        this.da = da;
    }

    UUID getAttesaId() { return attesaId; }
    Tipo getTipo() { return tipo; }
    Instant getQuando() { return quando; }
    UUID getDa() { return da; }
}
