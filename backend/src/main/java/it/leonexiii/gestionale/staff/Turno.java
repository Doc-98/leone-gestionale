package it.leonexiii.gestionale.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Una persona su una postazione, in un giorno, da un'ora a un'altra (ora locale, fuso Europe/Rome). */
@Entity
@Table(name = "turno")
class Turno {

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postazione_id", nullable = false)
    private Postazione postazione;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime inizio;

    @Column(nullable = false)
    private LocalTime fine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoTurno stato;

    private String note;

    @Column(name = "creato_il", nullable = false, insertable = false, updatable = false)
    private Instant creatoIl;

    protected Turno() {}

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    Postazione getPostazione() { return postazione; }
    LocalDate getData() { return data; }
    LocalTime getInizio() { return inizio; }
    LocalTime getFine() { return fine; }
    StatoTurno getStato() { return stato; }
    String getNote() { return note; }

    /** Ore del turno con due decimali (15:00–19:15 → 4,25). */
    BigDecimal ore() {
        return BigDecimal.valueOf(Duration.between(inizio, fine).toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
