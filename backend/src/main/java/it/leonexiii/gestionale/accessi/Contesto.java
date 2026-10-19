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
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Tutto quello che serve ai controlli per un passaggio, caricato una volta sola: il braccialetto letto (null se si
 * valuta la persona dalla scheda), la persona, i suoi ban, il golden pass, la situazione del cliente e i turni del giorno.
 */
record Contesto(Braccialetto braccialetto, List<Braccialetto> braccialettiPersona, Persone.PersonaScheda persona,
                Instant istante, LocalDate oggi, LocalTime ora, List<Blocco> blocchiAttivi, boolean goldenPass,
                Clienti.Situazione cliente, List<Frequenze.TurnoDelGiorno> turniOggi, int minutiRientro,
                Map<UUID, List<Frequenze.TurnoDelGiorno>> turniCarnetOggi) {

    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm");

    long braccialettiAttivi() {
        return braccialettiPersona.stream().filter(Braccialetto::attivo).count();
    }

    boolean corsoValido() {
        return cliente.abbonamenti().stream().anyMatch(a -> !a.carnet() && a.validoIl(oggi));
    }

    /** Un carnet valido oggi, con ingressi residui o appena usato (rientro), a qualsiasi ora. */
    Optional<Clienti.AbbonamentoBreve> carnetValido() {
        return carnetValidi().stream().filter(a -> a.rientroAlle(istante, minutiRientro) || a.residui() > 0).findFirst();
    }

    private List<Clienti.AbbonamentoBreve> carnetValidi() {
        return cliente.abbonamenti().stream().filter(a -> a.carnet() && a.validoIl(oggi)).toList();
    }

    /**
     * Il carnet da usare adesso: prima quello in rientro, poi il più vecchio con ingressi residui che vale a
     * quest'ora (a entrata libera, oppure a orario e nella fascia di un turno del suo corso). Se la fascia oraria
     * non si controlla, l'orario del carnet non conta.
     */
    Optional<Clienti.AbbonamentoBreve> carnetUtilizzabileOra(Parametri fascia, boolean fasciaControllata) {
        List<Clienti.AbbonamentoBreve> validi = carnetValidi();
        return validi.stream().filter(a -> a.rientroAlle(istante, minutiRientro)).findFirst()
            .or(() -> validi.stream().filter(a -> a.residui() > 0 && (!a.aOrario() || !fasciaControllata || inOrarioDelCarnet(a, fascia)))
                .min(Comparator.comparing(Clienti.AbbonamentoBreve::dal)));
    }

    boolean inOrarioDelCarnet(Clienti.AbbonamentoBreve a, Parametri fascia) {
        return turniCarnetOggi.getOrDefault(a.corsoId(), List.of()).stream().anyMatch(t -> inFinestra(t, fascia));
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
