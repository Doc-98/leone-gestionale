package it.leonexiii.gestionale.accessi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un braccialetto NFC assegnato a una persona: attivo, smarrito (resta suo, se si ritrova) o disattivato. */
@Entity
@Table(name = "braccialetto")
class Braccialetto {

    enum Stato {
        ATTIVO, SMARRITO, DISATTIVATO, RESTITUITO;

        /** Disattivato o riconsegnato: il codice si può dare a un'altra persona. */
        boolean libero() { return this == DISATTIVATO || this == RESTITUITO; }
    }

    @Id
    private UUID id;
    @Column(nullable = false)
    private String codice;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String stato;
    @Column(name = "stato_dal", nullable = false)
    private Instant statoDal;
    @Column(name = "assegnato_il", nullable = false)
    private Instant assegnatoIl;
    private String note;

    protected Braccialetto() {}

    Braccialetto(String codice, UUID personaId, String note, Instant ora) {
        this.id = UUID.randomUUID();
        this.codice = codice;
        this.personaId = personaId;
        this.stato = Stato.ATTIVO.name();
        this.statoDal = ora;
        this.assegnatoIl = ora;
        this.note = note;
    }

    void cambiaStato(Stato nuovo, Instant ora) {
        if (stato().equals(nuovo)) return;
        this.stato = nuovo.name();
        this.statoDal = ora;
    }

    Stato stato() { return Stato.valueOf(stato); }
    boolean attivo() { return stato() == Stato.ATTIVO; }

    UUID getId() { return id; }
    String getCodice() { return codice; }
    UUID getPersonaId() { return personaId; }
    Instant getStatoDal() { return statoDal; }
    Instant getAssegnatoIl() { return assegnatoIl; }
    String getNote() { return note; }
}
