package it.leonexiii.gestionale.accessi;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tornello (chiunque abbia un ruolo: esito con nome e motivi, livello 1 dell'ADR 0003); braccialetti e ban, golden
 * pass, regole: ognuno con il suo permesso (ADR 0010).
 */
@RestController
@RequestMapping("/api/accessi")
class AccessiController {

    private final VerificaAccessi verifica;
    private final GestioneAccessiService gestione;

    AccessiController(VerificaAccessi verifica, GestioneAccessiService gestione) {
        this.verifica = verifica;
        this.gestione = gestione;
    }

    record Lettura(String codice) {}

    /** Un braccialetto passato al tornello: {"codice": "04:A1:B2:C3"}. Si registra sempre. */
    @PostMapping("/passaggi")
    VerificaAccessi.Esito passa(@AuthenticationPrincipal Utente u, @RequestBody Lettura in) {
        return verifica.passa(u, in.codice());
    }

    @GetMapping("/passaggi")
    List<GestioneAccessiService.PassaggioDto> passaggi(@RequestParam(defaultValue = "20") int quanti) {
        return gestione.ultimiPassaggi(quanti);
    }

    @GetMapping("/persone/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'BRACCIALETTI')")
    GestioneAccessiService.SchedaAccessi scheda(@PathVariable UUID id) {
        return gestione.scheda(id);
    }

    @PostMapping("/persone/{id}/braccialetti")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BRACCIALETTI')")
    GestioneAccessiService.BraccialettoDto assegna(@PathVariable UUID id, @RequestBody GestioneAccessiService.NuovoBraccialetto in) {
        return gestione.assegna(id, in);
    }

    /** {"stato": "SMARRITO"} · "ATTIVO" (ritrovato) · "DISATTIVATO" */
    @PutMapping("/braccialetti/{id}/stato")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BRACCIALETTI')")
    void stato(@PathVariable UUID id, @RequestBody GestioneAccessiService.NuovoStato in) {
        gestione.cambiaStato(id, in);
    }

    /** {"personaId"} oppure {"braccialettoId"}, con "motivo" e facoltativi "dal", "al". */
    @PostMapping("/ban")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BRACCIALETTI')")
    Map<String, UUID> banna(@AuthenticationPrincipal Utente u, @RequestBody GestioneAccessiService.NuovoBlocco in) {
        return Map.of("id", gestione.banna(u, in));
    }

    @PostMapping("/ban/{id}/revoca")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'BRACCIALETTI')")
    void revocaBan(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gestione.revocaBan(u, id);
    }

    @PostMapping("/persone/{id}/golden-pass")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'GOLDEN_PASS')")
    Map<String, UUID> goldenPass(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody GestioneAccessiService.NuovoGoldenPass in) {
        return Map.of("id", gestione.goldenPass(u, id, in));
    }

    @PostMapping("/golden-pass/{id}/revoca")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'GOLDEN_PASS')")
    void revocaGoldenPass(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gestione.revocaGoldenPass(u, id);
    }

    @GetMapping("/regole")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'REGOLE_TORNELLO', 'BRACCIALETTI')")
    List<GestioneAccessiService.RegolaDto> regole() {
        return gestione.regole();
    }

    /** {"attiva": true, "parametri": {"minutiPrima": 15, "minutiDopoInizio": null}} */
    @PutMapping("/regole/{codice}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'REGOLE_TORNELLO')")
    void modificaRegola(@AuthenticationPrincipal Utente u, @PathVariable String codice, @RequestBody GestioneAccessiService.ModificaRegola in) {
        gestione.modificaRegola(u, codice, in);
    }
}
