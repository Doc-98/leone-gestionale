package it.leonexiii.gestionale.identita;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ruoli e permessi: solo con il permesso RUOLI (di base, gli admin). */
@RestController
@RequestMapping("/api")
@PreAuthorize("@autorizzazioni.ha(authentication, 'RUOLI')")
class RuoliController {

    private final RuoliService service;

    RuoliController(RuoliService service) {
        this.service = service;
    }

    @GetMapping("/ruoli")
    RuoliService.Pagina pagina() {
        return service.pagina();
    }

    /** {"nome", "descrizione", "copiaDa": "STAFF"} */
    @PostMapping("/ruoli")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> crea(@RequestBody RuoliService.NuovoRuolo in) {
        return Map.of("codice", service.crea(in));
    }

    @PutMapping("/ruoli/{codice}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void modifica(@AuthenticationPrincipal Utente u, @PathVariable String codice, @RequestBody RuoliService.ModificaRuolo in) {
        service.modifica(u, codice, in);
    }

    @DeleteMapping("/ruoli/{codice}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void elimina(@PathVariable String codice) {
        service.elimina(codice);
    }

    /** {"accountId", "impiantoId": null = tutta la società} */
    @PostMapping("/ruoli/{codice}/assegnazioni")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> assegna(@AuthenticationPrincipal Utente u, @PathVariable String codice, @RequestBody RuoliService.NuovaAssegnazione in) {
        return Map.of("id", service.assegna(u, codice, in));
    }

    @DeleteMapping("/assegnazioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void togli(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        service.togli(u, id);
    }
}
