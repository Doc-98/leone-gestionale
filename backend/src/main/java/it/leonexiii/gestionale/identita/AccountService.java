package it.leonexiii.gestionale.identita;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gli account dello staff (ADR 0012): si creano con una password temporanea mostrata una volta sola, da cambiare al
 * primo accesso; si disattivano (l'accesso si chiude subito) e si riattivano; la password si reimposta. Niente si
 * cancella.
 */
@Service
class AccountService {

    record AccountDto(UUID id, UUID personaId, String nome, String cognome, String email, boolean attivo, boolean deveCambiarePassword,
                      Instant ultimoAccesso, Instant creatoIl, List<RuoloAssegnato> ruoli) {}

    record NuovoAccount(UUID personaId, String nome, String cognome, String email, String ruolo, UUID impiantoId) {}

    /** La password temporanea si vede solo qui, una volta. */
    record PasswordTemporanea(UUID accountId, String email, String password) {}

    record CambioPassword(String attuale, String nuova) {}

    record NuovaEmail(String email) {}

    private static final String ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789";
    private final SecureRandom caso = new SecureRandom();

    private final AccountRepository account;
    private final PersonaRepository persone;
    private final AssegnazioneRuoloRepository assegnazioni;
    private final RuoliService ruoli;
    private final Autorizzazioni autorizzazioni;
    private final PasswordEncoder encoder;

    AccountService(AccountRepository account, PersonaRepository persone, AssegnazioneRuoloRepository assegnazioni, RuoliService ruoli,
                   Autorizzazioni autorizzazioni, PasswordEncoder encoder) {
        this.account = account;
        this.persone = persone;
        this.assegnazioni = assegnazioni;
        this.ruoli = ruoli;
        this.autorizzazioni = autorizzazioni;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    List<AccountDto> elenco() {
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        Map<UUID, List<RuoloAssegnato>> perAccount = assegnazioni.tuttiValidi(oggi).stream()
            .collect(Collectors.groupingBy(AssegnazioneRuolo::getAccountId, Collectors.mapping(a -> new RuoloAssegnato(a.getRuolo(), a.getImpiantoId()), Collectors.toList())));
        return account.tuttiConPersona().stream().map(a -> dto(a, perAccount.getOrDefault(a.getId(), List.of()))).toList();
    }

    private static AccountDto dto(Account a, List<RuoloAssegnato> r) {
        Persona p = a.getPersona();
        return new AccountDto(a.getId(), p.getId(), p.getNome(), p.getCognome(), a.getEmail(), a.isAttivo(), a.isDeveCambiarePassword(),
            a.getUltimoAccesso(), a.getCreatoIl(), r);
    }

    /** Per una persona già in anagrafica (personaId) o nuova (nome e cognome); con un ruolo iniziale facoltativo. */
    @Transactional
    PasswordTemporanea crea(Utente u, NuovoAccount in) {
        String email = email(in.email());
        Persona p;
        if (in.personaId() != null) {
            p = persone.findById(in.personaId()).orElseThrow(() -> new NonTrovato("Persona non trovata."));
            if (account.tuttiConPersona().stream().anyMatch(a -> a.getPersona().getId().equals(p.getId())))
                throw new Conflitto("Questa persona ha già un account.");
        } else {
            String n = in.nome() == null ? "" : in.nome().strip();
            String c = in.cognome() == null ? "" : in.cognome().strip();
            if (n.isEmpty() || c.isEmpty()) throw new IllegalArgumentException("Indica nome e cognome.");
            p = persone.saveAndFlush(new Persona(n, c, null));
        }
        String password = generaPassword();
        Account a = account.saveAndFlush(new Account(p, email, encoder.encode(password)));
        if (in.ruolo() != null && !in.ruolo().isBlank()) {
            if (!autorizzazioni.puo(u, Permesso.RUOLI)) throw new AccessDeniedException("Per dare un ruolo serve il permesso «Ruoli e permessi».");
            ruoli.assegna(u, in.ruolo(), new RuoliService.NuovaAssegnazione(a.getId(), in.impiantoId()));
        }
        return new PasswordTemporanea(a.getId(), a.getEmail(), password);
    }

    @Transactional
    PasswordTemporanea reimpostaPassword(Utente u, UUID id) {
        Account a = trova(id);
        if (proteggiAdmin(u, a)) throw new AccessDeniedException("Solo un admin può reimpostare la password di un admin.");
        String password = generaPassword();
        a.impostaPassword(encoder.encode(password), true);
        autorizzazioni.invalida();
        return new PasswordTemporanea(a.getId(), a.getEmail(), password);
    }

    @Transactional
    void disattiva(Utente u, UUID id) {
        Account a = trova(id);
        if (a.getId().equals(u.accountId())) throw new Conflitto("Non puoi disattivare il tuo account.");
        if (proteggiAdmin(u, a)) throw new AccessDeniedException("Solo un admin può disattivare un admin.");
        a.disattiva(Instant.now());
        autorizzazioni.invalida();
    }

    @Transactional
    void riattiva(UUID id) {
        trova(id).riattiva();
        autorizzazioni.invalida();
    }

    @Transactional
    void cambiaEmail(Utente u, UUID id, NuovaEmail in) {
        Account a = trova(id);
        if (proteggiAdmin(u, a)) throw new AccessDeniedException("Solo un admin può cambiare l'email di un admin.");
        a.cambiaEmail(email(in.email()));
        account.flush();
    }

    /** La propria password: serve quella attuale (anche se è la temporanea). */
    @Transactional
    void cambiaPropria(Utente u, CambioPassword in) {
        Account a = trova(u.accountId());
        if (in.attuale() == null || !encoder.matches(in.attuale(), a.getPasswordHash())) throw new IllegalArgumentException("La password attuale non è corretta.");
        String nuova = in.nuova() == null ? "" : in.nuova();
        if (nuova.length() < 10) throw new IllegalArgumentException("La nuova password deve avere almeno 10 caratteri.");
        if (nuova.length() > 200) throw new IllegalArgumentException("La nuova password è troppo lunga.");
        if (encoder.matches(nuova, a.getPasswordHash())) throw new IllegalArgumentException("La nuova password deve essere diversa da quella attuale.");
        if (nuova.strip().equalsIgnoreCase(a.getEmail()) || nuova.toLowerCase().contains(a.getPersona().getCognome().toLowerCase()))
            throw new IllegalArgumentException("Scegli una password che non contenga la tua email o il tuo cognome.");
        a.impostaPassword(encoder.encode(nuova), false);
        autorizzazioni.invalida();
    }

    @Transactional
    void registraAccesso(UUID accountId) {
        account.findById(accountId).ifPresent(a -> a.registraAccesso(Instant.now()));
    }

    private boolean proteggiAdmin(Utente u, Account a) {
        LocalDate oggi = LocalDate.now(UtenteService.FUSO);
        boolean eAdmin = assegnazioni.validi(a.getId(), oggi).stream().anyMatch(r -> RuoloDefinito.ADMIN.equals(r.getRuolo()));
        return eAdmin && !autorizzazioni.admin(u);
    }

    private Account trova(UUID id) {
        return account.findById(id).orElseThrow(() -> new NonTrovato("Account non trovato."));
    }

    private static String email(String e) {
        String s = e == null ? "" : e.strip();
        if (!s.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || s.length() > 200) throw new IllegalArgumentException("Email non valida.");
        return s;
    }

    /** 12 lettere e cifre facili da dettare (niente 0/O, 1/l), in tre gruppi: «kmrt-7qwe-h3pa». */
    String generaPassword() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(ALFABETO.charAt(caso.nextInt(ALFABETO.length())));
        }
        return b.toString();
    }
}
