package it.leonexiii.gestionale.pagamenti;

import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conto dei clienti (chi vede la scheda), pagamenti e addebiti (permesso «Pagamenti»), annullamenti (permesso
 * «Annullare pagamenti»), metodi di pagamento («Impostazioni della società»).
 */
@RestController
@RequestMapping("/api/pagamenti")
class PagamentiController {

    private final Pagamenti pagamenti;

    PagamentiController(Pagamenti pagamenti) {
        this.pagamenti = pagamenti;
    }

    @GetMapping("/persone/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'PAGAMENTI')")
    Pagamenti.Conto conto(@PathVariable UUID id) {
        return pagamenti.conto(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    Map<String, UUID> registra(@AuthenticationPrincipal Utente u, @RequestBody Pagamenti.NuovoPagamento in) {
        return Map.of("id", pagamenti.registra(in, u.personaId()));
    }

    @PostMapping("/{id}/annulla")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI_ANNULLA')")
    void annulla(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody Pagamenti.Motivo in) {
        pagamenti.annullaPagamento(id, in, u.personaId());
    }

    @PostMapping("/addebiti")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    Map<String, UUID> addebito(@AuthenticationPrincipal Utente u, @RequestBody Pagamenti.NuovoAddebitoManuale in) {
        return Map.of("id", pagamenti.addebitoManuale(in, u.personaId()));
    }

    @PostMapping("/addebiti/{id}/annulla")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI_ANNULLA')")
    void annullaAddebito(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody Pagamenti.Motivo in) {
        pagamenti.annullaAddebito(id, in, u.personaId());
    }

    /** Segna incassata la parte di un pagamento fatta con un metodo «da incassare» (es. il bonifico è arrivato). */
    @PostMapping("/metodi-usati/{id}/incassa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    void incassa(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody(required = false) Pagamenti.Incasso in) {
        pagamenti.incassa(id, in, u.personaId());
    }

    @GetMapping("/incassi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    Pagamenti.Incassi incassi(@RequestParam(required = false) LocalDate da, @RequestParam(required = false) LocalDate a) {
        return pagamenti.incassi(da, a);
    }

    @GetMapping("/da-incassare")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    List<Pagamenti.DaIncassare> daIncassare() {
        return pagamenti.daIncassare();
    }

    @GetMapping("/metodi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'PAGAMENTI', 'IMPOSTAZIONI')")
    List<Pagamenti.Metodo> metodi() {
        return pagamenti.metodi();
    }

    @PostMapping("/metodi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Pagamenti.Metodo creaMetodo(@RequestBody Pagamenti.NuovoMetodo in) {
        return pagamenti.creaMetodo(in);
    }

    @PutMapping("/metodi/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Pagamenti.Metodo modificaMetodo(@PathVariable UUID id, @RequestBody Pagamenti.NuovoMetodo in) {
        return pagamenti.modificaMetodo(id, in);
    }
}
