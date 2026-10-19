package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Utente;
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

/** Impostazioni dello staff: impianti e spazi (lettura), mansioni, postazioni, tariffe. */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("@autorizzazioni.ha(authentication, 'PIANIFICAZIONE', 'ANAGRAFICHE_STAFF')")
class AnagraficheController {

    record Ordine(List<UUID> ids) {}

    private final AnagraficheService service;

    AnagraficheController(AnagraficheService service) {
        this.service = service;
    }

    @GetMapping("/impianti")
    List<AnagraficheService.ImpiantoDto> impianti(@AuthenticationPrincipal Utente u) {
        return service.impianti(u);
    }

    @GetMapping("/spazi")
    List<AnagraficheService.SpazioDto> spazi(@AuthenticationPrincipal Utente u) {
        return service.spazi(u);
    }

    @GetMapping("/mansioni")
    List<AnagraficheService.MansioneDto> mansioni(@AuthenticationPrincipal Utente u) {
        return service.mansioni(u);
    }

    @PostMapping("/mansioni")
    @ResponseStatus(HttpStatus.CREATED)
    AnagraficheService.MansioneDto creaMansione(@AuthenticationPrincipal Utente u, @RequestBody AnagraficheService.MansioneInput in) {
        return service.creaMansione(u, in);
    }

    @PutMapping("/mansioni/{id}")
    AnagraficheService.MansioneDto rinominaMansione(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody AnagraficheService.MansioneInput in) {
        return service.rinominaMansione(u, id, in);
    }

    @PostMapping("/mansioni/{id}/postazioni")
    @ResponseStatus(HttpStatus.CREATED)
    AnagraficheService.PostazioneGestioneDto creaPostazione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                            @RequestBody AnagraficheService.PostazioneInput in) {
        return service.creaPostazione(u, id, in);
    }

    @PutMapping("/postazioni/{id}")
    AnagraficheService.EsitoPostazione modificaPostazione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                         @RequestBody AnagraficheService.PostazioneInput in) {
        return service.modificaPostazione(u, id, in);
    }

    /** {"ids": [...]} nell'ordine voluto. */
    @PutMapping("/postazioni/ordine")
    List<AnagraficheService.MansioneDto> ordina(@AuthenticationPrincipal Utente u, @RequestBody Ordine in) {
        return service.ordinaPostazioni(u, in.ids());
    }

    @GetMapping("/tariffe")
    List<AnagraficheService.TariffaDto> tariffe(@AuthenticationPrincipal Utente u) {
        return service.tariffe(u);
    }

    @PostMapping("/tariffe")
    @ResponseStatus(HttpStatus.CREATED)
    AnagraficheService.TariffaDto nuovaTariffa(@AuthenticationPrincipal Utente u, @RequestBody AnagraficheService.NuovaTariffa in) {
        return service.nuovaTariffa(u, in);
    }

    @PutMapping("/tariffe/{id}")
    AnagraficheService.TariffaDto correggiTariffa(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                 @RequestBody AnagraficheService.CorrezioneTariffa in) {
        return service.correggiTariffa(u, id, in);
    }
}
