package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Piano vasca: lettura per tutti, modifiche per coordinatori e admin (con il controllo per impianto nei servizi). */
@RestController
@RequestMapping("/api")
class PianoController {

    private final PianoService piano;
    private final GestionePianoService gestione;

    PianoController(PianoService piano, GestionePianoService gestione) {
        this.piano = piano;
        this.gestione = gestione;
    }

    /** Spazi e occorrenze (lezioni ed eventi) del periodo, al massimo due mesi. */
    @GetMapping("/piano")
    PianoService.Piano piano(@AuthenticationPrincipal Utente u,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return piano.piano(u, da, a);
    }

    @GetMapping("/corsi")
    List<GestionePianoService.CorsoDto> corsi(@AuthenticationPrincipal Utente u) {
        return gestione.corsi(u);
    }

    @PostMapping("/corsi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.CorsoDto creaCorso(@AuthenticationPrincipal Utente u, @RequestBody GestionePianoService.CorsoInput in) {
        return gestione.creaCorso(u, in);
    }

    @PutMapping("/corsi/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.CorsoDto modificaCorso(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody GestionePianoService.CorsoInput in) {
        return gestione.modificaCorso(u, id, in);
    }

    @PostMapping("/corsi/{id}/lezioni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.LezioneDto> aggiungiLezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                                               @RequestBody GestionePianoService.LezioneInput in) {
        return gestione.aggiungiLezione(u, id, in);
    }

    @PutMapping("/lezioni/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.LezioneDto> modificaLezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                                               @RequestBody GestionePianoService.LezioneInput in) {
        return gestione.modificaLezione(u, id, in);
    }

    @DeleteMapping("/lezioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    void togliLezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gestione.togliLezione(u, id);
    }

    /** {"data", "tipo": "SALTATA" | "SPOSTATA", "nuovaData", "nuovoInizio", "nuovoFine", "motivo"} */
    @PostMapping("/lezioni/{id}/eccezioni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<UUID> aggiungiEccezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                      @RequestBody GestionePianoService.EccezioneInput in) {
        return gestione.aggiungiEccezione(u, id, in);
    }

    @DeleteMapping("/eccezioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    void togliEccezione(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gestione.togliEccezione(u, id);
    }

    @GetMapping("/eventi/{id}")
    GestionePianoService.EventoDto evento(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return gestione.evento(u, id, true);
    }

    @PostMapping("/eventi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.EventoDto> creaEvento(@AuthenticationPrincipal Utente u, @RequestBody GestionePianoService.EventoInput in) {
        return gestione.creaEvento(u, in);
    }

    @PutMapping("/eventi/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.Esito<GestionePianoService.EventoDto> modificaEvento(@AuthenticationPrincipal Utente u, @PathVariable UUID id,
                                                                             @RequestBody GestionePianoService.EventoInput in) {
        return gestione.modificaEvento(u, id, in);
    }

    @PostMapping("/eventi/{id}/annulla")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    GestionePianoService.EventoDto annullaEvento(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return gestione.annullaEvento(u, id);
    }
}
