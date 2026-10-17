package it.leonexiii.gestionale.staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Un turno come lo vede il frontend. */
public record TurnoDto(
    UUID id,
    LocalDate data,
    LocalTime inizio,
    LocalTime fine,
    BigDecimal ore,
    String mansione,
    String postazione,
    StatoTurno stato,
    String note,
    Persona persona
) {
    /** Chi fa il turno: presente nell'elenco di tutti i turni, assente nei «miei turni». */
    public record Persona(UUID id, String nome) {}

    static TurnoDto da(Turno t, Persona persona) {
        return new TurnoDto(t.getId(), t.getData(), t.getInizio(), t.getFine(), t.ore(),
            t.getPostazione().getMansione().getNome(), t.getPostazione().getNome(), t.getStato(), t.getNote(), persona);
    }
}
