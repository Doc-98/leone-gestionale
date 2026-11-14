package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.identita.Utente;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gruppi di clienti: li definisce chi ha «Impostazioni della società», ci mette le persone chi ha «Clienti». */
@RestController
@RequestMapping("/api")
class GruppiController {

    record Aggiunta(UUID gruppoId) {}

    private final GruppiService gruppi;

    GruppiController(GruppiService gruppi) {
        this.gruppi = gruppi;
    }

    @GetMapping("/gruppi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'IMPOSTAZIONI')")
    List<GruppiService.GruppoDto> elenco() {
        return gruppi.elenco();
    }

    @PostMapping("/gruppi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    Map<String, UUID> crea(@RequestBody GruppiService.NuovoGruppo in) {
        return Map.of("id", gruppi.crea(in));
    }

    @PutMapping("/gruppi/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void modifica(@PathVariable UUID id, @RequestBody GruppiService.NuovoGruppo in) {
        gruppi.modifica(id, in);
    }

    @PutMapping("/gruppi/precedenze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void ordinaPrecedenze(@RequestBody GruppiService.OrdinePrecedenze in) {
        gruppi.ordinaPrecedenze(in);
    }

    /** Le appartenenze dichiarate dalle famiglie che aspettano la verifica della segreteria (ADR 0026). */
    @GetMapping("/gruppi/da-verificare")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")
    List<GruppiService.DaVerificare> daVerificare() {
        return gruppi.daVerificare();
    }

    @PostMapping("/clienti/appartenenze/{id}/verifica")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")
    void verifica(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        gruppi.verifica(id, u.personaId());
    }

    @GetMapping("/gruppi/{id}/persone")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'IMPOSTAZIONI')")
    List<GruppiService.Membro> membri(@PathVariable UUID id) {
        return gruppi.membri(id);
    }

    @PostMapping("/clienti/{id}/gruppi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")
    Map<String, UUID> aggiungi(@PathVariable UUID id, @RequestBody Aggiunta in) {
        return Map.of("id", gruppi.aggiungi(id, in.gruppoId()));
    }

    @PostMapping("/clienti/appartenenze/{id}/chiudi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")
    void togli(@PathVariable UUID id) {
        gruppi.togli(id);
    }
}
