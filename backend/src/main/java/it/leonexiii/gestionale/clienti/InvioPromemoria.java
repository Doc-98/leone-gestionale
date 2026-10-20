package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Un promemoria mandato (o tentato) a una persona: serve a non mandarlo due volte. */
@Entity
@Table(name = "invio_promemoria")
class InvioPromemoria {

    @Id
    private UUID id;
    @Column(name = "promemoria_id")
    private UUID promemoriaId;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String riferimento;
    private LocalDate scadenza;
    @Column(name = "email_id")
    private UUID emailId;
    @Column(nullable = false)
    private boolean manuale;
    @Column(name = "creato_il", nullable = false)
    private Instant creatoIl;

    protected InvioPromemoria() {}

    InvioPromemoria(UUID promemoriaId, String tipo, UUID personaId, String riferimento, LocalDate scadenza, UUID emailId, boolean manuale, Instant ora) {
        this.id = UUID.randomUUID();
        this.promemoriaId = promemoriaId;
        this.tipo = tipo;
        this.personaId = personaId;
        this.riferimento = riferimento;
        this.scadenza = scadenza;
        this.emailId = emailId;
        this.manuale = manuale;
        this.creatoIl = ora;
    }

    UUID getPromemoriaId() { return promemoriaId; }
    String getTipo() { return tipo; }
    UUID getPersonaId() { return personaId; }
    String getRiferimento() { return riferimento; }
    Instant getCreatoIl() { return creatoIl; }
}
