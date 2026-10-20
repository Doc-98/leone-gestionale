package it.leonexiii.gestionale.clienti;

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

/** Scheda cliente: segreteria, coordinatori e admin. La ricerca delle persone è in /api/persone. */
@RestController
@RequestMapping("/api/clienti")
@PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")
class ClientiController {

    private final SchedaClientiService schede;

    ClientiController(SchedaClientiService schede) {
        this.schede = schede;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SchedaClientiService.PersonaDto nuovo(@RequestBody SchedaClientiService.NuovoCliente in) {
        return schede.nuovoCliente(in);
    }

    @GetMapping("/{id}")
    SchedaClientiService.Scheda scheda(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return schede.scheda(u, id);
    }

    @PutMapping("/{id}/contatti")
    it.leonexiii.gestionale.identita.Persone.Contatti contatti(@PathVariable UUID id, @RequestBody SchedaClientiService.NuoviContatti in) {
        return schede.contatti(id, in);
    }

    @PostMapping("/{id}/certificati")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> certificato(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody SchedaClientiService.NuovoCertificato in) {
        return Map.of("id", schede.nuovoCertificato(u, id, in));
    }

    /** Senza date: la stagione in corso e la quota di base. */
    @PostMapping("/{id}/tesseramenti")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> tesseramento(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody SchedaClientiService.NuovoTesseramento in) {
        return Map.of("id", schede.nuovoTesseramento(u, id, in));
    }

    /** {"tipo": "CORSO", "corsoId", "volteASettimana": 2, "dal", "al"} oppure {"tipo": "CARNET", "ingressi": 10, …} */
    @PostMapping("/{id}/abbonamenti")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> abbonamento(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody SchedaClientiService.NuovoAbbonamento in) {
        return Map.of("id", schede.nuovoAbbonamento(u, id, in));
    }

    @PostMapping("/abbonamenti/{id}/termina")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void termina(@PathVariable UUID id, @RequestBody SchedaClientiService.Termine in) {
        schede.terminaAbbonamento(id, in);
    }

    /** {"dal", "al", "motivo"}: l'abbonamento non vale in quei giorni e la scadenza si allunga. */
    @PostMapping("/abbonamenti/{id}/sospensioni")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> sospendi(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody SchedaClientiService.NuovaSospensione in) {
        return Map.of("id", schede.sospendi(u, id, in));
    }

    @PostMapping("/sospensioni/{id}/annulla")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annullaSospensione(@PathVariable UUID id) {
        schede.annullaSospensione(id);
    }

    /** cosa: certificati, tesseramenti, abbonamenti */
    @PostMapping("/{cosa}/{id}/annulla")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annulla(@PathVariable String cosa, @PathVariable UUID id) {
        schede.annulla(cosa, id);
    }
}
