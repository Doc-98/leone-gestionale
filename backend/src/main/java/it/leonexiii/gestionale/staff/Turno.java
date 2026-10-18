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

/**
 * Una postazione coperta in un giorno, da un'ora a un'altra (ora locale, fuso Europe/Rome).
 * Senza persona il turno è «scoperto»: c'è da assegnarlo.
 */
@Entity
@Table(name = "turno")
class Turno {

    @Id
    private UUID id;

    @Column(name = "persona_id")
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

    @Column(name = "modello_riga_id")
    private UUID modelloRigaId;

    @Column(name = "creato_il", nullable = false, insertable = false, updatable = false)
    private Instant creatoIl;

    @Column(name = "aggiornato_il")
    private Instant aggiornatoIl;

    protected Turno() {}

    Turno(Postazione postazione, LocalDate data, LocalTime inizio, LocalTime fine, UUID personaId, String note, UUID modelloRigaId) {
        this.id = UUID.randomUUID();
        this.postazione = postazione;
        this.stato = StatoTurno.PIANIFICATO;
        this.modelloRigaId = modelloRigaId;
        modifica(postazione, data, inizio, fine, personaId, note);
    }

    void modifica(Postazione postazione, LocalDate data, LocalTime inizio, LocalTime fine, UUID personaId, String note) {
        if (!fine.isAfter(inizio)) throw new IllegalArgumentException("La fine del turno deve essere dopo l'inizio.");
        this.postazione = postazione;
        this.data = data;
        this.inizio = inizio;
        this.fine = fine;
        this.personaId = personaId;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.aggiornatoIl = Instant.now();
    }

    void cambiaOrario(LocalTime inizio, LocalTime fine) {
        modifica(postazione, data, inizio, fine, personaId, note);
    }

    void assegna(UUID personaId) {
        this.personaId = personaId;
        this.aggiornatoIl = Instant.now();
    }

    void annulla() {
        this.stato = StatoTurno.ANNULLATO;
        this.aggiornatoIl = Instant.now();
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    Postazione getPostazione() { return postazione; }
    UUID impiantoId() { return postazione.getMansione().getImpiantoId(); }
    LocalDate getData() { return data; }
    LocalTime getInizio() { return inizio; }
    LocalTime getFine() { return fine; }
    StatoTurno getStato() { return stato; }
    boolean pianificato() { return stato == StatoTurno.PIANIFICATO; }
    String getNote() { return note; }

    /** Ore del turno con due decimali (15:00–19:15 → 4,25). */
    BigDecimal ore() {
        return BigDecimal.valueOf(Duration.between(inizio, fine).toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
