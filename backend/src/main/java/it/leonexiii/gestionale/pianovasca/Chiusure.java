package it.leonexiii.gestionale.pianovasca;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pubblica del modulo: quando l'impianto è chiuso (chiusure di tutto l'impianto, cioè senza spazi indicati).
 * Il calendario della stagione le segna una volta; gli altri moduli le rispettano (es. la generazione dei turni).
 */
@Service
public class Chiusure {

    /** In quel giorno l'impianto è chiuso dalle {@code inizio} alle {@code fine} (00:00–23:59 = tutto il giorno). */
    public record Fascia(LocalTime inizio, LocalTime fine) {
        public boolean tuttoIlGiorno() {
            return !inizio.isAfter(LocalTime.of(0, 1)) && !fine.isBefore(LocalTime.of(23, 59));
        }
    }

    private final EventoRepository eventi;

    Chiusure(EventoRepository eventi) {
        this.eventi = eventi;
    }

    /** Giorno per giorno, le fasce in cui l'impianto è chiuso nel periodo. */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<Fascia>> diImpianto(UUID impiantoId, LocalDate da, LocalDate a) {
        Map<LocalDate, List<Fascia>> out = new TreeMap<>();
        for (Evento e : eventi.nelPeriodo(List.of(impiantoId), da, a)) {
            if (e.getTipo() != TipoEvento.CHIUSURA || e.isAnnullato() || !e.getSpazi().isEmpty()) continue;
            for (LocalDate g = e.getData().isBefore(da) ? da : e.getData(); !g.isAfter(a) && !g.isAfter(e.ultimoGiorno()); g = g.plusDays(1))
                out.computeIfAbsent(g, x -> new ArrayList<>()).add(new Fascia(e.getInizio(), e.getFine()));
        }
        return out;
    }
}
