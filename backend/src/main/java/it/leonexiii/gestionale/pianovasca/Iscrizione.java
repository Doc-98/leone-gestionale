package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Una persona iscritta a un turno del corso (corso + giorno + orario), da una data e fino a un'altra. */
@Entity
@Table(name = "iscrizione")
class Iscrizione {

    @Id
    private UUID id;

    @Column(name = "lezione_id", nullable = false)
    private UUID lezioneId;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(nullable = false)
    private LocalDate dal;

    private LocalDate al;

    protected Iscrizione() {}

    Iscrizione(UUID lezioneId, UUID personaId, LocalDate dal) {
        if (dal == null) throw new IllegalArgumentException("Indica da quando vale l'iscrizione.");
        this.id = UUID.randomUUID();
        this.lezioneId = lezioneId;
        this.personaId = personaId;
        this.dal = dal;
    }

    /** Ultimo giorno di frequenza; prima dell'inizio non ha senso. */
    void termina(LocalDate al) {
        if (al == null || al.isBefore(dal)) throw new IllegalArgumentException("L'ultimo giorno è prima dell'inizio dell'iscrizione.");
        this.al = al;
    }

    boolean attivaIl(LocalDate g) {
        return !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    UUID getId() { return id; }
    UUID getLezioneId() { return lezioneId; }
    UUID getPersonaId() { return personaId; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
}
