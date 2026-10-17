package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/turni")
class TurniController {

    private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    private final TurniService service;

    TurniController(TurniService service) {
        this.service = service;
    }

    /** I turni di chi è collegato. Senza date: da oggi per i prossimi 30 giorni. */
    @GetMapping("/miei")
    List<TurnoDto> miei(@AuthenticationPrincipal Utente utente,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        LocalDate inizio = da != null ? da : LocalDate.now(FUSO);
        LocalDate fine = a != null ? a : inizio.plusDays(30);
        return service.diPersona(utente.personaId(), inizio, fine);
    }

    /** Tutti i turni del periodo, con chi li fa: per coordinatori e admin. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    List<TurnoDto> tutti(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return service.nelPeriodo(da, a);
    }
}
