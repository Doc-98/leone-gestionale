package it.leonexiii.gestionale.identita;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account dello staff (permesso ACCOUNT) e la propria password (chiunque). */
@RestController
@RequestMapping("/api")
class AccountController {

    private final AccountService service;

    AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/account")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ACCOUNT')")
    List<AccountService.AccountDto> elenco() {
        return service.elenco();
    }

    /** {"personaId"} oppure {"nome", "cognome"}, con "email" e facoltativi "ruolo", "impiantoId". Risponde con la password temporanea. */
    @PostMapping("/account")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ACCOUNT')")
    AccountService.PasswordTemporanea crea(@AuthenticationPrincipal Utente u, @RequestBody AccountService.NuovoAccount in) {
        return service.crea(u, in);
    }

    @PostMapping("/account/{id}/password")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ACCOUNT')")
    AccountService.PasswordTemporanea reimposta(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return service.reimpostaPassword(u, id);
    }

    @PostMapping("/account/{id}/disattiva")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ACCOUNT')")
    void disattiva(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        service.disattiva(u, id);
    }

    @PostMapping("/account/{id}/riattiva")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ACCOUNT')")
    void riattiva(@PathVariable UUID id) {
        service.riattiva(id);
    }

    @PutMapping("/account/{id}/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ACCOUNT')")
    void email(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody AccountService.NuovaEmail in) {
        service.cambiaEmail(u, id, in);
    }

    /** {"attuale", "nuova"} */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cambiaPropria(@AuthenticationPrincipal Utente u, @RequestBody AccountService.CambioPassword in) {
        service.cambiaPropria(u, in);
    }
}
