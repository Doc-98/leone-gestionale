package it.leonexiii.gestionale.ricevute;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/**
 * Ricevute: fare, stampare e mandare (permesso «Pagamenti»), annullare («Annullare pagamenti»), vederle (chi vede la
 * scheda), impostazioni («Impostazioni della società»).
 */
@RestController
@RequestMapping("/api/ricevute")
class RicevuteController {

    private final RicevuteService ricevute;
    private final Autorizzazioni autorizzazioni;

    RicevuteController(RicevuteService ricevute, Autorizzazioni autorizzazioni) {
        this.ricevute = ricevute;
        this.autorizzazioni = autorizzazioni;
    }

    @GetMapping("/impostazioni")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI', 'IMPOSTAZIONI')")
    RicevuteService.Pagina impostazioni(@AuthenticationPrincipal Utente u) {
        // se una serie si può eliminare (e perché) lo vede solo chi gestisce le impostazioni
        return ricevute.pagina(autorizzazioni.puo(u, Permesso.IMPOSTAZIONI));
    }

    @PutMapping("/impostazioni")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    RicevuteService.ImpostazioniDto modifica(@RequestBody RicevuteService.ImpostazioniDto in) {
        return ricevute.modifica(in);
    }

    @PostMapping("/serie")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    RicevuteService.SerieDto creaSerie(@RequestBody RicevuteService.NuovaSerie in) {
        return ricevute.creaSerie(in);
    }

    /** {"ids": [...]}: tutte le serie, nell'ordine voluto. Risponde con l'elenco aggiornato. */
    @PutMapping("/serie/ordine")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    List<RicevuteService.SerieDto> ordinaSerie(@RequestBody RicevuteService.Ordine in) {
        return ricevute.ordinaSerie(in);
    }

    @PutMapping("/serie/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    RicevuteService.SerieDto modificaSerie(@PathVariable UUID id, @RequestBody RicevuteService.ModificaSerie in) {
        return ricevute.modificaSerie(id, in);
    }

    /** Elimina una serie mai usata; 409 se ha emesso ricevute o è l'ultima serie di ricevute accesa. */
    @DeleteMapping("/serie/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    void eliminaSerie(@PathVariable UUID id) {
        ricevute.eliminaSerie(id);
    }

    /** Le ricevute di un pagamento (le fa se non ci sono ancora). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    List<RicevuteService.RicevutaDto> emetti(@AuthenticationPrincipal Utente u, @RequestBody RicevuteService.Emetti in) {
        return ricevute.emetti(in, u.personaId());
    }

    @GetMapping
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'PAGAMENTI')")
    List<RicevuteService.RicevutaDto> elenco(@RequestParam(required = false) UUID pagamentoId, @RequestParam(required = false) UUID personaId) {
        if (pagamentoId != null) return ricevute.diPagamento(pagamentoId);
        if (personaId != null) return ricevute.diPersona(personaId);
        throw new IllegalArgumentException("Di quale pagamento o di quale persona?");
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'PAGAMENTI')")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean scarica) {
        byte[] pdf = ricevute.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, (scarica ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("ricevuta-" + id + ".pdf").build().toString())
            .body(pdf);
    }

    @PostMapping("/{id}/stampata")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    RicevuteService.RicevutaDto stampata(@PathVariable UUID id) {
        return ricevute.stampata(id);
    }

    @PostMapping("/{id}/invia")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    Map<String, Object> invia(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody(required = false) RicevuteService.Invio in) {
        return ricevute.invia(id, in, u.personaId());
    }

    @PostMapping("/{id}/annulla")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI_ANNULLA')")
    List<RicevuteService.RicevutaDto> annulla(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody RicevuteService.Annullo in) {
        return ricevute.annulla(id, in, u.personaId());
    }
}
