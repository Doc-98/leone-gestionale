package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Utente;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** La settimana tipo e la generazione dei turni del mese. */
@RestController
@RequestMapping("/api/modelli")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
class ModelliController {

    record Generazione(String mese) {}

    private final PianificazioneService service;

    ModelliController(PianificazioneService service) {
        this.service = service;
    }

    @GetMapping
    List<PianificazioneService.ModelloDto> elenco(@AuthenticationPrincipal Utente utente) {
        return service.modelli(utente);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PianificazioneService.ModelloDto crea(@AuthenticationPrincipal Utente utente, @RequestBody PianificazioneService.ModelloInput in) {
        return service.creaModello(utente, in);
    }

    @PutMapping("/{id}")
    PianificazioneService.ModelloDto modifica(@AuthenticationPrincipal Utente utente, @PathVariable UUID id,
                                              @RequestBody PianificazioneService.ModelloInput in) {
        return service.modificaModello(utente, id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void elimina(@AuthenticationPrincipal Utente utente, @PathVariable UUID id) {
        service.eliminaModello(utente, id);
    }

    @PutMapping("/{id}/righe/{rigaId}")
    PianificazioneService.ModelloDto modificaRiga(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @PathVariable UUID rigaId,
                                                  @RequestBody PianificazioneService.RigaInput in) {
        return service.modificaRiga(utente, id, rigaId, in);
    }

    @PostMapping("/{id}/righe")
    PianificazioneService.ModelloDto aggiungiRiga(@AuthenticationPrincipal Utente utente, @PathVariable UUID id,
                                                  @RequestBody PianificazioneService.RigaInput in) {
        return service.aggiungiRiga(utente, id, in);
    }

    @DeleteMapping("/{id}/righe/{rigaId}")
    PianificazioneService.ModelloDto togliRiga(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @PathVariable UUID rigaId) {
        return service.togliRiga(utente, id, rigaId);
    }

    /** {"mese": "2026-11"}: crea i turni di quel mese dalla settimana tipo (rilanciabile, senza doppioni). */
    @PostMapping("/{id}/genera")
    PianificazioneService.EsitoGenerazione genera(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @RequestBody Generazione in) {
        YearMonth mese;
        try {
            mese = YearMonth.parse(in.mese());
        } catch (Exception e) {
            throw new IllegalArgumentException("Mese non valido: usa il formato AAAA-MM.");
        }
        return service.genera(utente, id, mese);
    }
}
