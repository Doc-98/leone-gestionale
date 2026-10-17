package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Persone;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TurniService {

    /** Oltre questo intervallo la richiesta è quasi certamente un errore (e pesante). */
    static final long GIORNI_MASSIMI = 93;

    private final TurnoRepository turni;
    private final Persone persone;

    TurniService(TurnoRepository turni, Persone persone) {
        this.turni = turni;
        this.persone = persone;
    }

    @Transactional(readOnly = true)
    List<TurnoDto> diPersona(UUID personaId, LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);
        return turni.diPersona(personaId, da, a).stream().map(t -> TurnoDto.da(t, null)).toList();
    }

    @Transactional(readOnly = true)
    List<TurnoDto> nelPeriodo(LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);
        List<Turno> elenco = turni.nelPeriodo(da, a);
        var nomi = persone.nomiCompleti(elenco.stream().map(Turno::getPersonaId).distinct().toList());
        return elenco.stream()
            .map(t -> TurnoDto.da(t, new TurnoDto.Persona(t.getPersonaId(), nomi.getOrDefault(t.getPersonaId(), "?"))))
            .toList();
    }

    static void controllaIntervallo(LocalDate da, LocalDate a) {
        if (a.isBefore(da)) throw new IllegalArgumentException("La data finale è prima di quella iniziale.");
        if (ChronoUnit.DAYS.between(da, a) > GIORNI_MASSIMI)
            throw new IllegalArgumentException("Intervallo troppo lungo: al massimo " + GIORNI_MASSIMI + " giorni.");
    }
}
