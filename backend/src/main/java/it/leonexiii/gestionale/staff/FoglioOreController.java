package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Utente;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Il foglio ore nel modello Excel della società: il mio, quello di una persona, o tutti in un .zip. */
@RestController
@RequestMapping("/api/foglio-ore")
class FoglioOreController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FoglioOreService service;

    FoglioOreController(FoglioOreService service) {
        this.service = service;
    }

    /** Le colonne del modello, da associare alle mansioni. */
    @GetMapping("/voci")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    List<String> voci() {
        return service.voci();
    }

    @GetMapping("/mio")
    ResponseEntity<byte[]> mio(@AuthenticationPrincipal Utente u, @RequestParam(required = false) String mese) {
        return scarica(service.mio(u, mese(mese)), XLSX);
    }

    @GetMapping("/persone/{personaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    ResponseEntity<byte[]> diPersona(@AuthenticationPrincipal Utente u, @PathVariable UUID personaId, @RequestParam(required = false) String mese) {
        return scarica(service.diPersona(u, personaId, mese(mese)), XLSX);
    }

    @GetMapping("/tutti")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATORE')")
    ResponseEntity<byte[]> tutti(@AuthenticationPrincipal Utente u, @RequestParam(required = false) String mese) {
        return scarica(service.tutti(u, mese(mese)), MediaType.parseMediaType("application/zip"));
    }

    private static ResponseEntity<byte[]> scarica(FoglioOreService.File f, MediaType tipo) {
        return ResponseEntity.ok()
            .contentType(tipo)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.nome(), StandardCharsets.UTF_8).build().toString())
            .body(f.contenuto());
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
