package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Credenziali di accesso di una persona. Non tutte le persone hanno un account. */
@Entity
@Table(name = "account")
class Account {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean attivo;

    @Column(name = "creato_il", nullable = false, insertable = false, updatable = false)
    private Instant creatoIl;

    protected Account() {}

    UUID getId() { return id; }
    Persona getPersona() { return persona; }
    String getEmail() { return email; }
    String getPasswordHash() { return passwordHash; }
    boolean isAttivo() { return attivo; }
}
