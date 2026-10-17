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
 * L'utente autenticato, salvato nella sessione. Le authority sono i ruoli («ROLE_STAFF»…);
 * l'ambito di ogni ruolo resta in {@link #ruoli()} per i controlli per impianto.
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
    private final List<RuoloAssegnato> ruoli;

    Utente(UUID accountId, UUID personaId, String email, String passwordHash, String nome, String cognome,
           boolean attivo, List<RuoloAssegnato> ruoli) {
        this.accountId = accountId;
        this.personaId = personaId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nome = nome;
        this.cognome = cognome;
        this.attivo = attivo;
        this.ruoli = List.copyOf(ruoli);
    }

    public UUID accountId() { return accountId; }
    public UUID personaId() { return personaId; }
    public String email() { return email; }
    public String nome() { return nome; }
    public String cognome() { return cognome; }
    public List<RuoloAssegnato> ruoli() { return ruoli; }

    public boolean ha(Ruolo ruolo) {
        return ruoli.stream().anyMatch(r -> r.ruolo() == ruolo);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return ruoli.stream().map(r -> "ROLE_" + r.ruolo().name()).distinct().map(SimpleGrantedAuthority::new).toList();
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
