package it.leonexiii.gestionale.pianovasca;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pubblica del modulo per gli altri moduli: a quali turni dei corsi è iscritta una persona e quando si tengono
 * (es. per la fascia oraria del tornello e per la scheda cliente), e l'elenco dei corsi.
 */
@Service
public class Frequenze {

    /** Un'iscrizione a un turno del corso (corso + giorno + orario). */
    public record TurnoFrequentato(UUID iscrizioneId, UUID lezioneId, UUID corsoId, String corso, int giornoSettimana,
                                   LocalTime inizio, LocalTime fine, LocalDate dal, LocalDate al) {}

    /** Un turno che si tiene in una data, con l'orario effettivo (anche se spostato). */
    public record TurnoDelGiorno(UUID lezioneId, UUID corsoId, String corso, LocalTime inizio, LocalTime fine) {}

    public record CorsoBreve(UUID id, String nome, UUID impiantoId, boolean attivo) {}

    /** Un turno in cui la persona è in lista d'attesa, con la posizione e i posti liberi oggi (null se senza limite). */
    public record InAttesa(UUID attesaId, UUID lezioneId, UUID corsoId, String corso, int giornoSettimana, LocalTime inizio, LocalTime fine,
                           java.time.Instant aggiuntaIl, int posizione, Integer liberi) {}

    private final IscrizioneRepository iscrizioni;
    private final LezioneRepository lezioni;
    private final CorsoRepository corsi;

    private final AttesaService attesa;

    Frequenze(IscrizioneRepository iscrizioni, LezioneRepository lezioni, CorsoRepository corsi, AttesaService attesa) {
        this.attesa = attesa;
        this.iscrizioni = iscrizioni;
        this.lezioni = lezioni;
        this.corsi = corsi;
    }

    /** Le liste d'attesa in cui è la persona. */
    @Transactional(readOnly = true)
    public List<InAttesa> attesaDi(UUID personaId) {
        return attesa.attesaDi(personaId).stream().map(a -> new InAttesa(a.id(), a.lezioneId(), a.corsoId(), a.corso(), a.giornoSettimana(), a.inizio(),
            a.fine(), a.aggiuntaIl(), a.posizione(), a.liberi())).toList();
    }

    /** Le iscrizioni ancora attive alla data o che iniziano dopo. */
    @Transactional(readOnly = true)
    public List<TurnoFrequentato> turniDi(UUID personaId, LocalDate da) {
        List<Iscrizione> sue = iscrizioni.findByPersonaIdOrderByDal(personaId).stream().filter(i -> i.getAl() == null || !i.getAl().isBefore(da)).toList();
        Map<UUID, Lezione> perId = lezioniDi(sue);
        return sue.stream().filter(i -> perId.containsKey(i.getLezioneId())).map(i -> {
                Lezione l = perId.get(i.getLezioneId());
                return new TurnoFrequentato(i.getId(), l.getId(), l.getCorso().getId(), l.getCorso().getNome(), l.getGiornoSettimana(),
                    l.getInizio(), l.getFine(), i.getDal(), i.getAl());
            })
            .sorted(Comparator.comparingInt(TurnoFrequentato::giornoSettimana).thenComparing(TurnoFrequentato::inizio)).toList();
    }

    /** I turni a cui la persona è iscritta in quella data e che quel giorno si tengono, con l'orario effettivo. */
    @Transactional(readOnly = true)
    public List<TurnoDelGiorno> turniIl(UUID personaId, LocalDate data) {
        List<Iscrizione> attive = iscrizioni.findByPersonaIdOrderByDal(personaId).stream().filter(i -> i.attivaIl(data)).toList();
        List<TurnoDelGiorno> out = new ArrayList<>();
        for (Lezione l : lezioniDi(attive).values()) {
            for (Eccezione e : l.getEccezioni())
                if (e.getTipo() == TipoEccezione.SPOSTATA && data.equals(e.getNuovaData()))
                    out.add(new TurnoDelGiorno(l.getId(), l.getCorso().getId(), l.getCorso().getNome(), e.getNuovoInizio(), e.getNuovoFine()));
            if (l.cadeIl(data) && l.eccezioneIl(data).isEmpty())
                out.add(new TurnoDelGiorno(l.getId(), l.getCorso().getId(), l.getCorso().getNome(), l.getInizio(), l.getFine()));
        }
        out.sort(Comparator.comparing(TurnoDelGiorno::inizio));
        return out;
    }

    /** Tutti i turni di un corso che si tengono in quella data (es. gli orari del nuoto libero), con l'orario effettivo. */
    @Transactional(readOnly = true)
    public List<TurnoDelGiorno> turniDelCorsoIl(UUID corsoId, LocalDate data) {
        List<TurnoDelGiorno> out = new ArrayList<>();
        for (Lezione l : lezioni.deiCorsi(List.of(corsoId))) {
            Lezione c = lezioni.completa(l.getId()).orElse(l);
            for (Eccezione e : c.getEccezioni())
                if (e.getTipo() == TipoEccezione.SPOSTATA && data.equals(e.getNuovaData()))
                    out.add(new TurnoDelGiorno(c.getId(), c.getCorso().getId(), c.getCorso().getNome(), e.getNuovoInizio(), e.getNuovoFine()));
            if (c.cadeIl(data) && c.eccezioneIl(data).isEmpty())
                out.add(new TurnoDelGiorno(c.getId(), c.getCorso().getId(), c.getCorso().getNome(), c.getInizio(), c.getFine()));
        }
        out.sort(Comparator.comparing(TurnoDelGiorno::inizio));
        return out;
    }

    @Transactional(readOnly = true)
    public List<CorsoBreve> corsi() {
        return corsi.findAll().stream().map(c -> new CorsoBreve(c.getId(), c.getNome(), c.getImpiantoId(), c.isAttivo()))
            .sorted(Comparator.comparing(CorsoBreve::nome)).toList();
    }

    private Map<UUID, Lezione> lezioniDi(List<Iscrizione> lista) {
        return lista.stream().map(Iscrizione::getLezioneId).distinct()
            .map(id -> lezioni.completa(id).orElse(null)).filter(java.util.Objects::nonNull)
            .collect(Collectors.toMap(Lezione::getId, Function.identity()));
    }
}
