package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api")
class TurniController {

    private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    private final PianificazioneService service;

    TurniController(PianificazioneService service) {
        this.service = service;
    }

    /** I turni di chi è collegato. Senza date: da oggi per i prossimi 30 giorni. */
    @GetMapping("/turni/miei")
    List<TurnoDto> miei(@AuthenticationPrincipal Utente utente,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        LocalDate inizio = da != null ? da : LocalDate.now(FUSO);
        LocalDate fine = a != null ? a : inizio.plusDays(30);
        return service.diPersona(utente.personaId(), inizio, fine);
    }

    /** Tutti i turni del periodo negli impianti gestiti, con chi li fa (persona null = scoperto). */
    @GetMapping("/turni")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    List<TurnoDto> tutti(@AuthenticationPrincipal Utente utente,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return service.tutti(utente, da, a);
    }

    /** I turni dei colleghi negli impianti in cui lavoro (solo pianificati). */
    @GetMapping("/turni/staff")
    List<TurnoDto> staff(@AuthenticationPrincipal Utente utente,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return service.turniStaff(utente, da, a);
    }

    @GetMapping("/staff/colleghi")
    List<PersonaBreve> colleghi(@AuthenticationPrincipal Utente utente) {
        return service.colleghi(utente);
    }

    @PostMapping("/turni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    TurnoDto crea(@AuthenticationPrincipal Utente utente, @RequestBody PianificazioneService.TurnoInput in) {
        return service.crea(utente, in);
    }

    @PutMapping("/turni/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    TurnoDto modifica(@AuthenticationPrincipal Utente utente, @PathVariable UUID id, @RequestBody PianificazioneService.TurnoInput in) {
        return service.modifica(utente, id, in);
    }

    @PostMapping("/turni/{id}/annulla")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    TurnoDto annulla(@AuthenticationPrincipal Utente utente, @PathVariable UUID id) {
        return service.annulla(utente, id);
    }

    @GetMapping("/staff/postazioni")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    List<PianificazioneService.PostazioneDto> postazioni(@AuthenticationPrincipal Utente utente) {
        return service.postazioni(utente);
    }

    /** Lo staff degli impianti gestiti, per assegnare i turni. */
    @GetMapping("/staff/persone")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    List<PersonaBreve> personale(@AuthenticationPrincipal Utente utente) {
        return service.personale(utente);
    }
}
