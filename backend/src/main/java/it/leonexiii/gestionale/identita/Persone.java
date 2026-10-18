package it.leonexiii.gestionale.identita;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pubblica del modulo per gli altri moduli: dati essenziali delle persone. */
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

    /** Chi ha il ruolo nell'impianto oggi (es. lo staff della piscina, per assegnare i turni). */
    @Transactional(readOnly = true)
    public List<PersonaBreve> conRuolo(Ruolo ruolo, UUID impiantoId) {
        return repo.conRuolo(ruolo, impiantoId, LocalDate.now(UtenteService.FUSO)).stream()
            .map(p -> new PersonaBreve(p.getId(), p.nomeCompleto()))
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean haRuolo(UUID personaId, Ruolo ruolo, UUID impiantoId) {
        return conRuolo(ruolo, impiantoId).stream().anyMatch(p -> p.id().equals(personaId));
    }
}
