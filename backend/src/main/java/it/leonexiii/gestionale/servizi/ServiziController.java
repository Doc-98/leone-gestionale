package it.leonexiii.gestionale.servizi;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Servizi e dispositivi (modifica: «Impostazioni»), utilizzi e simulatore (anche «Borsellino e ricariche»). */
@RestController
@RequestMapping("/api")
class ServiziController {

    private final ServiziService servizi;
    private final Autorizzazioni autorizzazioni;

    ServiziController(ServiziService servizi, Autorizzazioni autorizzazioni) {
        this.servizi = servizi;
        this.autorizzazioni = autorizzazioni;
    }

    @GetMapping("/servizi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO', 'CLIENTI')")
    List<ServiziService.ServizioDto> servizi(@AuthenticationPrincipal Utente u) {
        return servizi.servizi(autorizzazioni.puo(u, Permesso.IMPOSTAZIONI));
    }

    @PostMapping("/servizi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.ServizioDto creaServizio(@RequestBody ServiziService.NuovoServizio in) {
        return servizi.creaServizio(in);
    }

    @PutMapping("/servizi/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.ServizioDto modificaServizio(@PathVariable UUID id, @RequestBody ServiziService.NuovoServizio in) {
        return servizi.modificaServizio(id, in);
    }

    /** {"ids": [...]}: tutti i servizi, nell'ordine voluto. Risponde con l'elenco aggiornato. */
    @PutMapping("/servizi/ordine")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    List<ServiziService.ServizioDto> ordinaServizi(@RequestBody ServiziService.Ordine in) {
        return servizi.ordinaServizi(in);
    }

    /** Si elimina sempre: 200 con {utilizzi, macchine} di quel momento; gli utilizzi restano nello storico con il nome di allora, le macchine senza servizio. */
    @DeleteMapping("/servizi/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.Eliminato eliminaServizio(@PathVariable UUID id) {
        return servizi.eliminaServizio(id);
    }

    @GetMapping("/dispositivi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO')")
    List<ServiziService.DispositivoDto> dispositivi(@AuthenticationPrincipal Utente u) {
        return servizi.dispositivi(autorizzazioni.puo(u, Permesso.IMPOSTAZIONI));
    }

    @PostMapping("/dispositivi")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.DispositivoCreato creaDispositivo(@RequestBody ServiziService.NuovoDispositivo in) {
        return servizi.creaDispositivo(in);
    }

    @PutMapping("/dispositivi/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.DispositivoDto modificaDispositivo(@PathVariable UUID id, @RequestBody ServiziService.NuovoDispositivo in) {
        return servizi.modificaDispositivo(id, in);
    }

    /** Si elimina sempre: 200 con {utilizzi} di quel momento; gli utilizzi restano nello storico con il nome della macchina di allora. */
    @DeleteMapping("/dispositivi/{id}")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.Eliminato eliminaDispositivo(@PathVariable UUID id) {
        return servizi.eliminaDispositivo(id);
    }

    @PostMapping("/dispositivi/{id}/chiave")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI')")
    ServiziService.DispositivoCreato nuovaChiave(@PathVariable UUID id) {
        return servizi.nuovaChiave(id);
    }

    /** Il simulatore: {"codice", "azione": "USA" | "SALDO", "postazione"}. Gli utilizzi restano segnati come simulati. */
    @PostMapping("/dispositivi/{id}/prova")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO')")
    ServiziService.Risposta prova(@PathVariable UUID id, @RequestBody ServiziService.Prova in) {
        return servizi.prova(id, in);
    }

    @GetMapping("/utilizzi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO', 'CLIENTI')")
    List<ServiziService.UtilizzoDto> utilizzi(@RequestParam(required = false) UUID personaId, @RequestParam(required = false) UUID dispositivoId,
                                              @RequestParam(defaultValue = "50") int quanti) {
        return servizi.utilizzi(personaId, dispositivoId, quanti);
    }
}
