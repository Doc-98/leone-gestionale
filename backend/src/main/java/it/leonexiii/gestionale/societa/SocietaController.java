package it.leonexiii.gestionale.societa;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Dati della società e stagioni: li leggono tutti i collegati, li cambia chi ha «Impostazioni della società». */
@RestController
@RequestMapping("/api")
class SocietaController {

    private final Societa societa;

    SocietaController(Societa societa) {
        this.societa = societa;
    }

    @GetMapping("/societa")
    Societa.Dati dati() {
        return societa.dati();
    }

    @PutMapping("/societa")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Societa.Dati modifica(@RequestBody Societa.Dati in) {
        return societa.modifica(in);
    }

    @GetMapping("/stagioni")
    List<Societa.Stagione> stagioni() {
        return societa.stagioni();
    }

    @PostMapping("/stagioni")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Societa.Stagione crea(@RequestBody Societa.NuovaStagione in) {
        return societa.creaStagione(in);
    }

    @PutMapping("/stagioni/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Societa.Stagione modifica(@PathVariable UUID id, @RequestBody Societa.NuovaStagione in) {
        return societa.modificaStagione(id, in);
    }

    @DeleteMapping("/stagioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void elimina(@PathVariable UUID id) {
        societa.eliminaStagione(id);
    }
}
