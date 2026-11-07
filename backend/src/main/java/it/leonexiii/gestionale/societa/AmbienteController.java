package it.leonexiii.gestionale.societa;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ciò che la pagina di accesso mostra prima di entrare: il nome della società e se è la demo (dove compaiono gli
 * account di prova). Si legge senza accesso: non contiene niente di riservato.
 */
@RestController
class AmbienteController {

    record Ambiente(String societa, boolean demo) {}

    private final Societa societa;
    private final Environment ambiente;

    AmbienteController(Societa societa, Environment ambiente) {
        this.societa = societa;
        this.ambiente = ambiente;
    }

    @GetMapping("/api/ambiente")
    Ambiente ambiente() {
        return new Ambiente(societa.nome(), ambiente.acceptsProfiles(Profiles.of("demo")));
    }
}
