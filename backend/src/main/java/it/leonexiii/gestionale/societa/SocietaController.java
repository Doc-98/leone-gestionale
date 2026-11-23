package it.leonexiii.gestionale.societa;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Dati della società e stagioni: li leggono tutti i collegati, li cambia chi ha «Impostazioni della società». */
@RestController
@RequestMapping("/api")
class SocietaController {

    /**
     * Una stagione con, per chi gestisce le impostazioni, se si può eliminare e (se no) perché: cosa la usa e cosa fare, su
     * due righe. Per gli altri «eliminabile» e «motivoBlocco» sono vuoti.
     */
    record StagioneDto(UUID id, String nome, LocalDate dal, LocalDate al, BigDecimal quota, boolean quotaRimborsabile,
                       Boolean eliminabile, String motivoBlocco) {}

    private final Societa societa;
    private final Autorizzazioni autorizzazioni;

    SocietaController(Societa societa, Autorizzazioni autorizzazioni) {
        this.societa = societa;
        this.autorizzazioni = autorizzazioni;
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
    List<StagioneDto> stagioni(@AuthenticationPrincipal Utente u) {
        boolean conBlocchi = autorizzazioni.puo(u, Permesso.IMPOSTAZIONI);
        return societa.stagioni().stream().map(s -> {
            if (!conBlocchi) return new StagioneDto(s.id(), s.nome(), s.dal(), s.al(), s.quota(), s.quotaRimborsabile(), null, null);
            Optional<String> blocco = societa.motivoBlocco(s);
            return new StagioneDto(s.id(), s.nome(), s.dal(), s.al(), s.quota(), s.quotaRimborsabile(), blocco.isEmpty(), blocco.orElse(null));
        }).toList();
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

    /** Elimina una stagione che nessuno usa; 409 se qualcosa la usa (listino, vendite, tesseramenti, esiti dei livelli). */
    @DeleteMapping("/stagioni/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void elimina(@PathVariable UUID id) {
        societa.eliminaStagione(id);
    }
}
