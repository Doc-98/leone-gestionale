package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.identita.Utente;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Il telefono come lettore NFC del PC (ADR 0017). Abbinamento e letture dal PC richiedono l'accesso; le chiamate del
 * telefono (sotto /telefono e /codice) no: le autorizza il token dell'abbinamento, e permettono solo di mandare codici.
 */
@RestController
@RequestMapping("/api/lettori")
class LettoriController {

    record CodiceLetto(String codice) {}

    private final LettoriTelefono lettori;

    LettoriController(LettoriTelefono lettori) {
        this.lettori = lettori;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LettoriTelefono.Abbinamento abbina(@AuthenticationPrincipal Utente u) {
        return lettori.abbina(u);
    }

    @GetMapping("/{id}/letture")
    LettoriTelefono.Letture letture(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestParam(defaultValue = "0") long dopo) {
        return lettori.letture(u, id, dopo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void scollega(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        lettori.scollega(u, id);
    }

    @PostMapping("/telefono/{token}/presente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void presente(@PathVariable String token) {
        lettori.telefonoPresente(token);
    }

    @PostMapping("/telefono/{token}/letture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leggi(@PathVariable String token, @RequestBody CodiceLetto in) {
        lettori.leggi(token, in.codice());
    }

    @GetMapping("/codice/{codice}")
    Map<String, String> daCodice(@PathVariable String codice) {
        return Map.of("token", lettori.tokenDaCodice(codice));
    }

    @ExceptionHandler(LettoriTelefono.TroppiTentativi.class)
    ProblemDetail troppi(LettoriTelefono.TroppiTentativi e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
}
