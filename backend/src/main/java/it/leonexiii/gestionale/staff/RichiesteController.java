package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Utente;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/richieste")
class RichiesteController {

    record Decisione(String nota) {}

    private final RichiesteService service;

    RichiesteController(RichiesteService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RichiestaDto crea(@AuthenticationPrincipal Utente utente, @RequestBody RichiesteService.NuovaRichiesta in) {
        return service.crea(utente, in);
    }

    /** Le mie richieste e quelle rivolte a me. */
    @GetMapping("/mie")
    List<RichiestaDto> mie(@AuthenticationPrincipal Utente utente) {
        return service.mie(utente);
    }

    /** Sostituzioni aperte a chiunque dello staff. */
    @GetMapping("/aperte")
    List<RichiestaDto> aperte(@AuthenticationPrincipal Utente utente) {
        return service.aperte(utente);
    }

    /** Per coordinatori: richieste negli stati indicati (default: in attesa di approvazione). */
    @GetMapping
    @PreAuthorize("@autorizzazioni.ha(authentication, 'APPROVAZIONI')")
    List<RichiestaDto> perStato(@AuthenticationPrincipal Utente utente,
                                @RequestParam(defaultValue = "IN_ATTESA_APPROVAZIONE") List<StatoRichiesta> stato) {
        return service.perStato(utente, stato);
    }

    @PostMapping("/{id}/accetta")
    RichiestaDto accetta(@AuthenticationPrincipal Utente utente, @PathVariable UUID id) {
        return service.accetta(utente, id);
    }

    @PostMapping("/{id}/rifiuta")
    RichiestaDto rifiuta(@AuthenticationPrincipal Utente utente, @PathVariable UUID id) {
        return service.rifiuta(utente, id);
    }

    @PostMapping("/{id}/annulla")
    RichiestaDto annulla(@AuthenticationPrincipal Utente utente, @PathVariable UUID id) {
        return service.annulla(utente, id);
    }

    @PostMapping("/{id}/approva")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'APPROVAZIONI')")
    RichiestaDto approva(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @RequestBody(required = false) Decisione d) {
        return service.approva(utente, id, d == null ? null : d.nota());
    }

    record Proposta(UUID collegaId) {}

    /** Il coordinatore propone una sostituzione a un collega preciso. */
    @PostMapping("/{id}/proponi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'APPROVAZIONI')")
    RichiestaDto proponi(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @RequestBody Proposta in) {
        return service.proponi(utente, id, in.collegaId());
    }

    /** A chi proporre la sostituzione: lo staff dell'impianto, liberi prima. */
    @GetMapping("/{id}/candidati")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'APPROVAZIONI')")
    List<RichiesteService.Candidato> candidati(@AuthenticationPrincipal Utente utente, @PathVariable UUID id) {
        return service.candidati(utente, id);
    }

    /** Nessuno può sostituire: il turno resta da assegnare. */
    @PostMapping("/{id}/senza-sostituto")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'APPROVAZIONI')")
    RichiestaDto senzaSostituto(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @RequestBody(required = false) Map<String, String> in) {
        return service.senzaSostituto(utente, id, in == null ? null : in.get("nota"));
    }

    @PostMapping("/{id}/respingi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'APPROVAZIONI')")
    RichiestaDto respingi(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @RequestBody(required = false) Decisione d) {
        return service.respingi(utente, id, d == null ? null : d.nota());
    }
}
