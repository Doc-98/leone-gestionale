package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.PersonaBreve;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Un turno come lo vede il frontend. «persona» è valorizzata negli elenchi con più persone
 * (null = turno scoperto); nei «miei turni» resta null.
 */
public record TurnoDto(
    UUID id,
    LocalDate data,
    LocalTime inizio,
    LocalTime fine,
    BigDecimal ore,
    UUID impiantoId,
    UUID postazioneId,
    String mansione,
    String postazione,
    StatoTurno stato,
    String note,
    PersonaBreve persona
) {
    static TurnoDto da(Turno t, PersonaBreve persona) {
        return new TurnoDto(t.getId(), t.getData(), t.getInizio(), t.getFine(), t.ore(), t.impiantoId(),
            t.getPostazione().getId(), t.getPostazione().getMansione().getNome(), t.getPostazione().getNome(),
            t.getStato(), t.getNote(), persona);
    }

    static TurnoDto conPersona(Turno t, Map<UUID, String> nomi) {
        UUID p = t.getPersonaId();
        return da(t, p == null ? null : new PersonaBreve(p, nomi.getOrDefault(p, "?")));
    }
}
