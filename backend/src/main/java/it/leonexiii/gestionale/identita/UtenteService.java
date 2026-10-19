package it.leonexiii.gestionale.identita;

import java.time.ZoneId;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UtenteService implements UserDetailsService {

    static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    private final AccountRepository account;

    UtenteService(AccountRepository account) {
        this.account = account;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        Account a = account.trovaPerEmail(email.trim())
            .orElseThrow(() -> new UsernameNotFoundException("Account non trovato"));
        Persona p = a.getPersona();
        return new Utente(a.getId(), p.getId(), a.getEmail(), a.getPasswordHash(), p.getNome(), p.getCognome(), a.isAttivo());
    }
}
