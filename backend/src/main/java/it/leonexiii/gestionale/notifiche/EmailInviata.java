package it.leonexiii.gestionale.notifiche;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un'email: inviata, non inviata (posta non configurata), senza indirizzo, o con errore. */
@Entity
@Table(name = "email_inviata")
class EmailInviata {

    @Id
    private UUID id;
    @Column(name = "persona_id")
    private UUID personaId;
    private String destinatario;
    @Column(nullable = false)
    private String oggetto;
    @Column(nullable = false)
    private String testo;
    @Column(nullable = false)
    private String stato;
    private String errore;
    @Column(name = "inviata_da")
    private UUID inviataDa;
    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;

    protected EmailInviata() {}

    EmailInviata(UUID personaId, String destinatario, String oggetto, String testo, String stato, String errore, UUID inviataDa, Instant ora) {
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.destinatario = destinatario;
        this.oggetto = oggetto.length() > 200 ? oggetto.substring(0, 200) : oggetto;
        this.testo = testo;
        this.stato = stato;
        this.errore = errore == null ? null : errore.length() > 500 ? errore.substring(0, 500) : errore;
        this.inviataDa = inviataDa;
        this.creataIl = ora;
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getDestinatario() { return destinatario; }
    String getOggetto() { return oggetto; }
    String getTesto() { return testo; }
    String getStato() { return stato; }
    String getErrore() { return errore; }
    Instant getCreataIl() { return creataIl; }
}
