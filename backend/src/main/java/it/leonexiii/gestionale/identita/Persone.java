package it.leonexiii.gestionale.identita;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pubblica del modulo per gli altri moduli: dati essenziali delle persone, dato l'id. */
@Service
public class Persone {

    private final PersonaRepository repo;

    Persone(PersonaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> nomiCompleti(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream().collect(Collectors.toMap(Persona::getId, Persona::nomeCompleto));
    }
}
