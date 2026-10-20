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

    @Column(name = "deve_cambiare_password", nullable = false)
    private boolean deveCambiarePassword;

    @Column(name = "ultimo_accesso")
    private Instant ultimoAccesso;

    @Column(name = "disattivato_il")
    private Instant disattivatoIl;

    protected Account() {}

    /** Nuovo account con una password temporanea, da cambiare al primo accesso. */
    Account(Persona persona, String email, String hashTemporaneo) {
        this.id = UUID.randomUUID();
        this.persona = persona;
        this.email = email;
        this.passwordHash = hashTemporaneo;
        this.attivo = true;
        this.deveCambiarePassword = true;
    }

    void impostaPassword(String hash, boolean temporanea) {
        this.passwordHash = hash;
        this.deveCambiarePassword = temporanea;
    }

    void disattiva(Instant ora) {
        this.attivo = false;
        this.disattivatoIl = ora;
    }

    void riattiva() {
        this.attivo = true;
        this.disattivatoIl = null;
    }

    void cambiaEmail(String email) {
        this.email = email;
    }

    void registraAccesso(Instant ora) {
        this.ultimoAccesso = ora;
    }

    boolean isDeveCambiarePassword() { return deveCambiarePassword; }
    Instant getUltimoAccesso() { return ultimoAccesso; }
    Instant getCreatoIl() { return creatoIl; }

    UUID getId() { return id; }
    Persona getPersona() { return persona; }
    String getEmail() { return email; }
    String getPasswordHash() { return passwordHash; }
    boolean isAttivo() { return attivo; }
}
