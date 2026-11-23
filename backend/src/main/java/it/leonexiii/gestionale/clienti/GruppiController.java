package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gruppi di clienti: li definisce chi ha «Impostazioni della società», ci mette le persone chi ha «Clienti». */
@RestController
@RequestMapping("/api")
class GruppiController {

    record Aggiunta(UUID gruppoId) {}

    private final GruppiService gruppi;
    private final Autorizzazioni autorizzazioni;

    GruppiController(GruppiService gruppi, Autorizzazioni autorizzazioni) {
        this.gruppi = gruppi;
        this.autorizzazioni = autorizzazioni;
    }

    /** Nell'ordine dell'elenco, che è anche l'ordine delle precedenze. */
    @GetMapping("/gruppi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'IMPOSTAZIONI')")
    List<GruppiService.GruppoDto> elenco(@AuthenticationPrincipal Utente u) {
        // se un gruppo si elimina con la finestra delle conseguenze lo vede solo chi gestisce le impostazioni
        return gruppi.elenco(autorizzazioni.puo(u, Permesso.IMPOSTAZIONI));
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

    /** {"ids": [...]}: tutti i gruppi, nell'ordine voluto (e quindi delle precedenze). Risponde con l'elenco aggiornato. */
    @PutMapping("/gruppi/ordine")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    List<GruppiService.GruppoDto> ordina(@RequestBody GruppiService.Ordine in) {
        return gruppi.ordina(in);
    }

    /** Cosa succede eliminando il gruppo, con i numeri veri: la finestra delle conseguenze li mostra prima di confermare. */
    @GetMapping("/gruppi/{id}/eliminazione")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    GruppiService.Eliminazione eliminazione(@PathVariable UUID id) {
        return gruppi.eliminazione(id);
    }

    /** Senza conseguenze elimina e basta; con persone o prezzi nel listino serve {@code ?pesante=true}, altrimenti 409. */
    @DeleteMapping("/gruppi/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void elimina(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean pesante) {
        gruppi.elimina(id, pesante);
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
