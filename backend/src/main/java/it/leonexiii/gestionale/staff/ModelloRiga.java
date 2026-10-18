package it.leonexiii.gestionale.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** Una postazione in un giorno della settimana (1 = lunedì … 7 = domenica), con l'orario e chi di solito la copre. */
@Entity
@Table(name = "modello_riga")
class ModelloRiga {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modello_id", nullable = false)
    private ModelloSettimanale modello;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postazione_id", nullable = false)
    private Postazione postazione;

    @Column(name = "giorno_settimana", nullable = false)
    private short giornoSettimana;

    @Column(nullable = false)
    private LocalTime inizio;

    @Column(nullable = false)
    private LocalTime fine;

    @Column(name = "persona_id")
    private UUID personaId;

    protected ModelloRiga() {}

    ModelloRiga(ModelloSettimanale modello, Postazione postazione, int giornoSettimana, LocalTime inizio, LocalTime fine, UUID personaId) {
        if (giornoSettimana < 1 || giornoSettimana > 7) throw new IllegalArgumentException("Giorno della settimana tra 1 (lunedì) e 7 (domenica).");
        if (!fine.isAfter(inizio)) throw new IllegalArgumentException("La fine deve essere dopo l'inizio.");
        this.id = UUID.randomUUID();
        this.modello = modello;
        this.postazione = postazione;
        this.giornoSettimana = (short) giornoSettimana;
        this.inizio = inizio;
        this.fine = fine;
        this.personaId = personaId;
    }

    UUID getId() { return id; }
    Postazione getPostazione() { return postazione; }
    int getGiornoSettimana() { return giornoSettimana; }
    LocalTime getInizio() { return inizio; }
    LocalTime getFine() { return fine; }
    UUID getPersonaId() { return personaId; }
}
