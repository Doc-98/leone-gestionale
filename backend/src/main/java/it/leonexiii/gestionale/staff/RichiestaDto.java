package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.PersonaBreve;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

public record RichiestaDto(
    UUID id,
    TipoRichiesta tipo,
    StatoRichiesta stato,
    TurnoDto turno,
    TurnoDto turnoCollega,
    PersonaBreve richiedente,
    PersonaBreve collega,
    LocalTime nuovoInizio,
    LocalTime nuovoFine,
    String messaggio,
    Instant creataIl,
    Instant decisaIl,
    String notaDecisione
) {
    static RichiestaDto da(RichiestaTurno r, Map<UUID, String> nomi) {
        return new RichiestaDto(r.getId(), r.getTipo(), r.getStato(),
            TurnoDto.conPersona(r.getTurno(), nomi),
            r.getTurnoCollega() == null ? null : TurnoDto.conPersona(r.getTurnoCollega(), nomi),
            breve(r.getRichiedenteId(), nomi), breve(r.getCollegaId(), nomi),
            r.getNuovoInizio(), r.getNuovoFine(), r.getMessaggio(), r.getCreataIl(), r.getDecisaIl(), r.getNotaDecisione());
    }

    private static PersonaBreve breve(UUID id, Map<UUID, String> nomi) {
        return id == null ? null : new PersonaBreve(id, nomi.getOrDefault(id, "?"));
    }
}
