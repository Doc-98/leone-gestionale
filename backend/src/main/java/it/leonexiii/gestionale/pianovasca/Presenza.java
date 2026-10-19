package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Presente o assente a un turno del corso in una data. */
@Entity
@Table(name = "presenza")
class Presenza {

    @Embeddable
    record Chiave(@Column(name = "lezione_id") UUID lezioneId, @Column(name = "data") LocalDate data,
                  @Column(name = "persona_id") UUID personaId) implements Serializable {}

    @EmbeddedId
    private Chiave chiave;

    @Column(nullable = false)
    private boolean presente;

    @Column(name = "segnata_da")
    private UUID segnataDa;

    @Column(name = "segnata_il", nullable = false)
    private Instant segnataIl;

    protected Presenza() {}

    Presenza(Chiave chiave) {
        this.chiave = chiave;
    }

    void segna(boolean presente, UUID da) {
        this.presente = presente;
        this.segnataDa = da;
        this.segnataIl = Instant.now();
    }

    Chiave getChiave() { return chiave; }
    boolean isPresente() { return presente; }
}
