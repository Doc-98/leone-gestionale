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

    /** Quanto serve per mostrare una persona in un elenco (es. gli iscritti a un corso, con l'età). */
    public record PersonaScheda(UUID id, String nome, String cognome, LocalDate dataNascita) {
        public String nomeCompleto() {
            return nome + " " + cognome;
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, PersonaScheda> schede(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream()
            .collect(Collectors.toMap(Persona::getId, p -> new PersonaScheda(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita())));
    }

    @Transactional(readOnly = true)
    public List<PersonaScheda> cerca(String testo, int quante) {
        String t = testo == null ? "" : testo.strip().toLowerCase(java.util.Locale.ROOT);
        if (t.length() < 2) return List.of();
        return repo.cerca("%" + t.replace("%", "").replace("_", "") + "%", org.springframework.data.domain.PageRequest.of(0, quante)).stream()
            .map(p -> new PersonaScheda(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita())).toList();
    }

    /** Nuova persona senza account (un cliente): nome e cognome obbligatori, data di nascita facoltativa. */
    @Transactional
    public PersonaScheda crea(String nome, String cognome, LocalDate dataNascita) {
        String n = nome == null ? "" : nome.strip();
        String c = cognome == null ? "" : cognome.strip();
        if (n.isEmpty() || c.isEmpty()) throw new IllegalArgumentException("Indica nome e cognome.");
        if (n.length() > 80 || c.length() > 80) throw new IllegalArgumentException("Nome o cognome troppo lunghi.");
        if (dataNascita != null && dataNascita.isAfter(LocalDate.now(UtenteService.FUSO))) throw new IllegalArgumentException("La data di nascita è nel futuro.");
        Persona p = repo.saveAndFlush(new Persona(n, c, dataNascita));
        return new PersonaScheda(p.getId(), p.getNome(), p.getCognome(), p.getDataNascita());
    }

    @Transactional(readOnly = true)
    public boolean haRuolo(UUID personaId, Ruolo ruolo, UUID impiantoId) {
        return conRuolo(ruolo, impiantoId).stream().anyMatch(p -> p.id().equals(personaId));
    }
}
