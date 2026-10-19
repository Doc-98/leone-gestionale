package it.leonexiii.gestionale.identita;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * L'utente autenticato, salvato nella sessione. Ruoli e permessi non stanno qui: si leggono dal vivo con
 * {@link Autorizzazioni}, così un cambio di ruolo o di permessi vale subito, senza rifare il login.
 */
public final class Utente implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final UUID personaId;
    private final String email;
    private String passwordHash;
    private final String nome;
    private final String cognome;
    private final boolean attivo;

    Utente(UUID accountId, UUID personaId, String email, String passwordHash, String nome, String cognome,
           boolean attivo) {
        this.accountId = accountId;
        this.personaId = personaId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nome = nome;
        this.cognome = cognome;
        this.attivo = attivo;
    }

    public UUID accountId() { return accountId; }
    public UUID personaId() { return personaId; }
    public String email() { return email; }
    public String nome() { return nome; }
    public String cognome() { return cognome; }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_UTENTE"));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isEnabled() { return attivo; }

    /** Dopo il login l'hash non serve più: Spring Security lo cancella e non resta in sessione. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
