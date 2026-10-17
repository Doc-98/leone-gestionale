package it.leonexiii.gestionale.identita;

import java.time.LocalDate;
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
    private final AssegnazioneRuoloRepository ruoli;

    UtenteService(AccountRepository account, AssegnazioneRuoloRepository ruoli) {
        this.account = account;
        this.ruoli = ruoli;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        Account a = account.trovaPerEmail(email.trim())
            .orElseThrow(() -> new UsernameNotFoundException("Account non trovato"));
        var validi = ruoli.validi(a.getId(), LocalDate.now(FUSO)).stream()
            .map(r -> new RuoloAssegnato(r.getRuolo(), r.getImpiantoId()))
            .toList();
        Persona p = a.getPersona();
        return new Utente(a.getId(), p.getId(), a.getEmail(), a.getPasswordHash(), p.getNome(), p.getCognome(), a.isAttivo(), validi);
    }
}
