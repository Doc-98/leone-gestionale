package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.notifiche.Posta;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Liste d'attesa dei turni pieni e cambio di turno (ADR 0015). I permessi si controllano nei servizi (per impianto). */
@RestController
@RequestMapping("/api")
class AttesaController {

    private final AttesaService attesa;
    private final IscrizioniService iscrizioni;

    AttesaController(AttesaService attesa, IscrizioniService iscrizioni) {
        this.attesa = attesa;
        this.iscrizioni = iscrizioni;
    }

    @GetMapping("/attesa")
    AttesaService.PaginaAttesa pagina(@AuthenticationPrincipal Utente u) {
        return attesa.pagina(u);
    }

    @GetMapping("/corsi/{id}/turni")
    List<AttesaService.TurnoPosti> turniDelCorso(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return attesa.turniDelCorso(u, id);
    }

    @GetMapping("/lezioni/{id}/attesa")
    List<AttesaService.VoceAttesa> diTurno(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return attesa.diTurno(u, id);
    }

    /** {"personaId"} oppure {"nuovaPersona": {nome, cognome, dataNascita}}, con "note". */
    @PostMapping("/lezioni/{id}/attesa")
    @ResponseStatus(HttpStatus.CREATED)
    AttesaService.VoceAttesa aggiungi(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody AttesaService.NuovaAttesa in) {
        return attesa.aggiungi(u, id, in);
    }

    @PostMapping("/attesa/{id}/iscrivi")
    @ResponseStatus(HttpStatus.CREATED)
    IscrizioniService.IscrittoDto iscrivi(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody AttesaService.IscriviDaAttesa in) {
        return attesa.iscrivi(u, id, in);
    }

    @PostMapping("/attesa/{id}/rinuncia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rinuncia(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        attesa.rinuncia(u, id);
    }

    @PostMapping("/attesa/{id}/avvisa")
    Posta.Esito avvisa(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody AttesaService.Avviso in) {
        return attesa.avvisa(u, id, in);
    }

    @PostMapping("/iscrizioni/{id}/cambia")
    IscrizioniService.CambioFatto cambia(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody IscrizioniService.Cambio in) {
        return iscrizioni.cambia(u, id, in);
    }
}
