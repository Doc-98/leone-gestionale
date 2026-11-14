package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.Utente;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Esiti di fine stagione e passaggi di livello (ADR 0026); i controlli di accesso sono nel servizio, per turno. */
@RestController
@RequestMapping("/api/livelli")
class LivelliController {

    private final LivelliService livelli;

    LivelliController(LivelliService livelli) {
        this.livelli = livelli;
    }

    @GetMapping
    LivelliService.PaginaLivelli pagina(@AuthenticationPrincipal Utente u) {
        return livelli.pagina(u);
    }

    @GetMapping("/turni/{lezioneId}")
    LivelliService.DettaglioTurno turno(@AuthenticationPrincipal Utente u, @PathVariable UUID lezioneId) {
        return livelli.turno(u, lezioneId);
    }

    @PutMapping("/turni/{lezioneId}/esiti")
    LivelliService.DettaglioTurno esiti(@AuthenticationPrincipal Utente u, @PathVariable UUID lezioneId, @RequestBody LivelliService.Esiti in) {
        return livelli.esiti(u, lezioneId, in);
    }

    @PostMapping("/passaggi")
    LivelliService.DettaglioTurno passa(@AuthenticationPrincipal Utente u, @RequestBody LivelliService.NuovoPassaggio in) {
        return livelli.passa(u, in);
    }

    @PostMapping("/passaggi/{id}/annulla")
    LivelliService.DettaglioTurno annulla(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return livelli.annulla(u, id);
    }
}
