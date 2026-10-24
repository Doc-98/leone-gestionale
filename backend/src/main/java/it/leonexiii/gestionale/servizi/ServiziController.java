package it.leonexiii.gestionale.servizi;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    ServiziController(ServiziService servizi) {
        this.servizi = servizi;
    }

    @GetMapping("/servizi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO', 'CLIENTI')")
    List<ServiziService.ServizioDto> servizi() {
        return servizi.servizi();
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

    @GetMapping("/dispositivi")
    @PreAuthorize("@autorizzazioni.ha(authentication, 'IMPOSTAZIONI', 'BORSELLINO')")
    List<ServiziService.DispositivoDto> dispositivi() {
        return servizi.dispositivi();
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
