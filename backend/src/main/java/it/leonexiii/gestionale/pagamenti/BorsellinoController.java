package it.leonexiii.gestionale.pagamenti;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Borsellino dalla scheda: lo vede chi vede la scheda; ricariche e trasferimenti con «Borsellino e ricariche»;
 * restituzioni in denaro e correzioni con «Rimborsi e correzioni del borsellino»; regole con «Impostazioni».
 */
@RestController
@RequestMapping("/api/borsellino")
class BorsellinoController {

    private final Borsellino borsellino;
    private final Autorizzazioni autorizzazioni;

    BorsellinoController(Borsellino borsellino, Autorizzazioni autorizzazioni) {
        this.borsellino = borsellino;
        this.autorizzazioni = autorizzazioni;
    }

    @GetMapping("/persone/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'PAGAMENTI', 'BORSELLINO')")
    Borsellino.Scheda scheda(@PathVariable UUID id) {
        return borsellino.scheda(id);
    }

    @PostMapping("/persone/{id}/ricariche")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO')")
    Map<String, UUID> ricarica(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody Borsellino.NuovaRicarica in) {
        return Map.of("pagamentoId", borsellino.ricarica(id, in, u.personaId(), autorizzazioni.puo(u, Permesso.PAGAMENTI_FLESSIBILI)));
    }

    @PostMapping("/persone/{id}/trasferimenti")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO')")
    void trasferisci(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody Borsellino.Trasferimento in) {
        borsellino.trasferisci(id, in, u.personaId());
    }

    @PostMapping("/persone/{id}/restituzioni")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO_RIMBORSI')")
    void restituisci(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody Borsellino.Restituzione in) {
        borsellino.restituisci(id, in, u.personaId());
    }

    @PostMapping("/persone/{id}/correzioni")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BORSELLINO_RIMBORSI')")
    void correggi(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody Borsellino.Correzione in) {
        borsellino.correggi(id, in, u.personaId());
    }

    @GetMapping("/regole")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'BORSELLINO', 'BRACCIALETTI', 'IMPOSTAZIONI')")
    Borsellino.Regole regole() {
        return borsellino.regole();
    }

    @PutMapping("/regole")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Borsellino.Regole modificaRegole(@RequestBody Borsellino.Regole in) {
        return borsellino.modificaRegole(in);
    }
}
