package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tutto quello che serve ai controlli per un passaggio, caricato una volta sola: il braccialetto letto (null se si
 * valuta la persona dalla scheda), la persona, i suoi ban, il golden pass, la situazione del cliente e i turni del giorno.
 */
record Contesto(Braccialetto braccialetto, List<Braccialetto> braccialettiPersona, Persone.PersonaScheda persona,
                Instant istante, LocalDate oggi, LocalTime ora, List<Blocco> blocchiAttivi, boolean goldenPass,
                Clienti.Situazione cliente, List<Frequenze.TurnoDelGiorno> turniOggi, int minutiRientro) {

    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm");

    long braccialettiAttivi() {
        return braccialettiPersona.stream().filter(Braccialetto::attivo).count();
    }

    boolean corsoValido() {
        return cliente.abbonamenti().stream().anyMatch(a -> !a.carnet() && a.validoIl(oggi));
    }

    /** Il carnet da usare: valido oggi e con ingressi residui, o appena usato (rientro). Prima quello in rientro, poi il più vecchio. */
    Optional<Clienti.AbbonamentoBreve> carnetUtilizzabile() {
        List<Clienti.AbbonamentoBreve> validi = cliente.abbonamenti().stream().filter(a -> a.carnet() && a.validoIl(oggi)).toList();
        return validi.stream().filter(a -> a.rientroAlle(istante, minutiRientro)).findFirst()
            .or(() -> validi.stream().filter(a -> a.residui() > 0).min(Comparator.comparing(Clienti.AbbonamentoBreve::dal)));
    }

    /** Da quando a quando si entra per un turno: N minuti prima dell'inizio, fino alla fine o fino a M minuti dopo l'inizio. */
    static LocalTime[] finestra(Frequenze.TurnoDelGiorno t, Parametri p) {
        int prima = p.intero("minutiPrima", 10);
        Integer dopoInizio = p.intero("minutiDopoInizio");
        LocalTime da = t.inizio().minusMinutes(prima);
        if (da.isAfter(t.inizio())) da = LocalTime.MIN; // a cavallo della mezzanotte
        LocalTime a = dopoInizio == null ? t.fine() : t.inizio().plusMinutes(dopoInizio);
        if (a.isBefore(t.inizio())) a = LocalTime.MAX;
        return new LocalTime[] {da, a};
    }

    boolean inFinestra(Frequenze.TurnoDelGiorno t, Parametri p) {
        LocalTime[] f = finestra(t, p);
        return !ora.isBefore(f[0]) && !ora.isAfter(f[1]);
    }

    static String data(LocalDate d) {
        return d.format(DATA);
    }

    static String ora(LocalTime t) {
        return t.format(ORA);
    }
}
