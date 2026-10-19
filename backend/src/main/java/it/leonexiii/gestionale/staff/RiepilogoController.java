package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Utente;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ore e compensi del mese: per coordinatori (tutto lo staff) e per ognuno (i propri). */
@RestController
@RequestMapping("/api/riepilogo")
class RiepilogoController {

    private final RiepilogoService service;

    RiepilogoController(RiepilogoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ORE_STAFF')")
    RiepilogoService.Riepilogo impianti(@AuthenticationPrincipal Utente utente, @RequestParam(required = false) String mese) {
        return service.diImpianti(utente, mese(mese));
    }

    @GetMapping("/mio")
    RiepilogoService.Riepilogo mio(@AuthenticationPrincipal Utente utente, @RequestParam(required = false) String mese) {
        return service.mio(utente, mese(mese));
    }

    @GetMapping(value = "/csv")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'ORE_STAFF')")
    ResponseEntity<byte[]> csv(@AuthenticationPrincipal Utente utente, @RequestParam(required = false) String mese) {
        YearMonth m = mese(mese);
        byte[] corpo = RiepilogoService.csv(service.diImpianti(utente, m)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"riepilogo-ore-" + m + ".csv\"")
            .body(corpo);
    }

    private static YearMonth mese(String s) {
        if (s == null || s.isBlank()) return YearMonth.now(ZoneId.of("Europe/Rome"));
        try {
            return YearMonth.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Mese non valido: usa il formato AAAA-MM.");
        }
    }
}
