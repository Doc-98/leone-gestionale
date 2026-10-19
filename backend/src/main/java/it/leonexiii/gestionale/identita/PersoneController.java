package it.leonexiii.gestionale.identita;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ricerca di persone per nome (per iscrivere qualcuno già presente nell'anagrafica). */
@RestController
@RequestMapping("/api/persone")
@PreAuthorize("@autorizzazioni.ha(authentication, 'CLIENTI', 'ISCRIZIONI', 'RUOLI')")
class PersoneController {

    private final Persone persone;

    PersoneController(Persone persone) {
        this.persone = persone;
    }

    @GetMapping
    List<Persone.PersonaScheda> cerca(@RequestParam String q) {
        return persone.cerca(q, 20);
    }
}
