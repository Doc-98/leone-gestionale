package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.notifiche.Posta;
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

/** Scadenze e promemoria email: chi ha il permesso «Clienti». */
@RestController
@RequestMapping("/api")
@PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI')")
class ScadenzeController {

    private final ScadenzeService scadenze;
    private final PromemoriaService promemoria;

    ScadenzeController(ScadenzeService scadenze, PromemoriaService promemoria) {
        this.scadenze = scadenze;
        this.promemoria = promemoria;
    }

    @GetMapping("/scadenze")
    ScadenzeService.Scadenze scadenze(@RequestParam(defaultValue = "30") int entro) {
        return scadenze.scadenze(entro);
    }

    /** {"personaId", "tipo", "riferimento"} (da una voce delle scadenze). */
    @PostMapping("/scadenze/promemoria")
    Posta.Esito invia(@AuthenticationPrincipal Utente u, @RequestBody PromemoriaService.InvioManuale in) {
        return promemoria.inviaOra(u, in);
    }

    @GetMapping("/promemoria")
    PromemoriaService.Pagina pagina() {
        return promemoria.pagina();
    }

    @PostMapping("/promemoria")
    @ResponseStatus(HttpStatus.CREATED)
    PromemoriaService.RegolaDto crea(@RequestBody PromemoriaService.NuovaRegola in) {
        return promemoria.crea(in);
    }

    @PutMapping("/promemoria/{id}")
    PromemoriaService.RegolaDto modifica(@PathVariable UUID id, @RequestBody PromemoriaService.ModificaRegola in) {
        return promemoria.modifica(id, in);
    }

    @DeleteMapping("/promemoria/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void elimina(@PathVariable UUID id) {
        promemoria.elimina(id);
    }

    /** Il giro dei promemoria adesso, senza aspettare domattina. */
    @PostMapping("/promemoria/esegui")
    PromemoriaService.Resoconto esegui() {
        return promemoria.esegui();
    }
}
