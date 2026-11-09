package it.leonexiii.gestionale.cassa;

import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/**
 * Cassa: aprire, muovere e chiudere, lo storico e il PDF (permesso «Pagamenti»); riaprire una chiusura («Annullare
 * pagamenti»); le casse e le scelte («ImpostazioniCassa della società»).
 */
@RestController
@RequestMapping("/api/cassa")
class CassaController {

    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(CassaService.FUSO);

    private final CassaService cassa;
    private final CassaforteService cassaforte;

    CassaController(CassaService cassa, CassaforteService cassaforte) {
        this.cassa = cassa;
        this.cassaforte = cassaforte;
    }

    @GetMapping("/avvisi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.Avvisi avvisi() {
        return cassa.avvisi();
    }

    @PostMapping("/casse/{id}/confronta")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.EsitoConfronto confronta(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody CassaService.Confronto in) {
        return cassa.confronta(id, in, u.personaId());
    }

    @PostMapping("/sessioni/{id}/controlla")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI_ANNULLA')")
    CassaService.SessioneDto controlla(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody(required = false) CassaService.Controllo in) {
        return cassa.controlla(id, in, u.personaId());
    }

    @GetMapping("/cassaforte")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaforteService.Cassaforte cassaforte() {
        return cassaforte.cassaforte();
    }

    @PostMapping("/versamenti")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaforteService.VersamentoDto versa(@AuthenticationPrincipal Utente u, @RequestBody CassaforteService.NuovoVersamento in) {
        return cassaforte.versa(in, u.personaId());
    }

    @PostMapping("/versamenti/{id}/annulla")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI_ANNULLA')")
    CassaforteService.VersamentoDto annullaVersamento(@AuthenticationPrincipal Utente u, @PathVariable UUID id) {
        return cassaforte.annulla(id, u.personaId());
    }

    @GetMapping
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI', 'IMPOSTAZIONI')")
    CassaService.Pagina pagina() {
        return cassa.pagina();
    }

    @PutMapping("/impostazioni")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    CassaService.ImpostazioniDto modifica(@RequestBody CassaService.ImpostazioniDto in) {
        return cassa.modifica(in);
    }

    @PostMapping("/casse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    CassaService.CassaDto creaCassa(@RequestBody CassaService.NuovaCassa in) {
        return cassa.creaCassa(in);
    }

    @PutMapping("/casse/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    CassaService.CassaDto modificaCassa(@PathVariable UUID id, @RequestBody CassaService.ModificaCassa in) {
        return cassa.modificaCassa(id, in);
    }

    @PostMapping("/casse/{id}/apri")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.SessioneDto apri(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody(required = false) CassaService.Apertura in) {
        return cassa.apri(id, in, u.personaId());
    }

    @PostMapping("/casse/{id}/movimenti")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.SessioneDto movimento(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody CassaService.NuovoMovimento in) {
        return cassa.movimento(id, in, u.personaId());
    }

    @PostMapping("/movimenti/{id}/annulla")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.SessioneDto annullaMovimento(@PathVariable UUID id) {
        return cassa.annullaMovimento(id);
    }

    @PostMapping("/casse/{id}/chiudi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.SessioneDto chiudi(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody CassaService.Chiusura in) {
        return cassa.chiudi(id, in, u.personaId());
    }

    @PostMapping("/sessioni/{id}/riapri")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI_ANNULLA')")
    CassaService.SessioneDto riapri(@AuthenticationPrincipal Utente u, @PathVariable UUID id, @RequestBody CassaService.Motivo in) {
        return cassa.riapri(id, in, u.personaId());
    }

    @GetMapping("/sessioni")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    List<CassaService.SessioneDto> storico(@RequestParam(required = false) LocalDate da, @RequestParam(required = false) LocalDate a,
                                           @RequestParam(required = false) UUID cassaId) {
        return cassa.storico(da, a, cassaId);
    }

    @GetMapping("/sessioni/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    CassaService.SessioneDto dettaglio(@PathVariable UUID id) {
        return cassa.dettaglio(id);
    }

    @GetMapping("/sessioni/{id}/pdf")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'PAGAMENTI')")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean scarica) {
        var s = cassa.dettaglio(id);
        byte[] pdf = cassa.pdf(id);
        String nome = "chiusura-" + s.cassa().toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + FILE.format(s.chiusaIl()) + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, (scarica ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(nome).build().toString())
            .body(pdf);
    }
}
