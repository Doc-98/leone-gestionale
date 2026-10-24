package it.leonexiii.gestionale.servizi;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'API delle macchine (phon, docce, lettore del saldo): niente accesso con utente e password, ma la chiave del
 * dispositivo nell'intestazione «X-Chiave-Dispositivo». Vedi il README per il formato.
 */
@RestController
@RequestMapping("/api/macchine/{id}")
class MacchineController {

    static final String INTESTAZIONE = "X-Chiave-Dispositivo";

    private final ServiziService servizi;

    MacchineController(ServiziService servizi) {
        this.servizi = servizi;
    }

    /** {"codice": "04A1B2C3", "postazione": "3"}: risponde se la macchina può partire, e scala i crediti. */
    @PostMapping("/utilizzi")
    ResponseEntity<ServiziService.Risposta> usa(@PathVariable UUID id, @RequestHeader(value = INTESTAZIONE, required = false) String chiave,
                                                @RequestBody ServiziService.Lettura in) {
        Dispositivo d = servizi.autentica(id, chiave);
        if (d == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (in == null || in.codice() == null || in.codice().isBlank()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(servizi.usa(d, in, false));
    }

    @GetMapping("/saldo")
    ResponseEntity<ServiziService.Risposta> saldo(@PathVariable UUID id, @RequestHeader(value = INTESTAZIONE, required = false) String chiave,
                                                  @RequestParam String codice) {
        Dispositivo d = servizi.autentica(id, chiave);
        if (d == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(servizi.saldo(d, codice));
    }
}
