package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Una persona in lista d'attesa per un turno del corso pieno: chi arriva prima, passa prima. */
@Entity
@Table(name = "attesa")
class Attesa {

    enum Stato { IN_ATTESA, ISCRITTA, RINUNCIA }

    @Id
    private UUID id;

    @Column(name = "lezione_id", nullable = false)
    private UUID lezioneId;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "aggiunta_il", nullable = false)
    private Instant aggiuntaIl;

    @Column(name = "aggiunta_da")
    private UUID aggiuntaDa;

    private String note;

    @Column(name = "avvisata_il")
    private Instant avvisataIl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stato stato;

    @Column(name = "chiusa_il")
    private Instant chiusaIl;

    protected Attesa() {}

    Attesa(UUID lezioneId, UUID personaId, String note, UUID aggiuntaDa, Instant adesso) {
        this.id = UUID.randomUUID();
        this.lezioneId = lezioneId;
        this.personaId = personaId;
        this.note = note == null || note.isBlank() ? null : note.strip();
        if (this.note != null && this.note.length() > 300) throw new IllegalArgumentException("La nota è troppo lunga (al massimo 300 caratteri).");
        this.aggiuntaDa = aggiuntaDa;
        this.aggiuntaIl = adesso;
        this.stato = Stato.IN_ATTESA;
    }

    void chiudi(Stato come, Instant adesso) {
        if (stato != Stato.IN_ATTESA) throw new IllegalStateException("Non è più in lista d'attesa.");
        this.stato = come;
        this.chiusaIl = adesso;
    }

    void avvisata(Instant adesso) {
        this.avvisataIl = adesso;
    }

    UUID getId() { return id; }
    UUID getLezioneId() { return lezioneId; }
    UUID getPersonaId() { return personaId; }
    Instant getAggiuntaIl() { return aggiuntaIl; }
    String getNote() { return note; }
    Instant getAvvisataIl() { return avvisataIl; }
    Stato getStato() { return stato; }
    boolean inAttesa() { return stato == Stato.IN_ATTESA; }
}
