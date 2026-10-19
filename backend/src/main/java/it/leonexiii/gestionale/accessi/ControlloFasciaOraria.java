package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chi ha l'abbonamento a un corso entra solo nella fascia dei turni a cui è iscritto quel giorno: di base da 10 minuti
 * prima dell'inizio fino alla fine. Chi ha un carnet utilizzabile non ha questo vincolo.
 */
@Component
class ControlloFasciaOraria implements ControlloAccesso {

    @Override public String regola() { return "FASCIA_ORARIA"; }
    @Override public String nome() { return "Fascia oraria del corso"; }
    @Override public String descrizione() {
        return "Rosso se si passa fuori dalla fascia di un turno a cui la persona è iscritta quel giorno, o fuori dagli orari "
            + "del corso di un carnet a orario. Non vale per chi ha un carnet a entrata libera.";
    }
    @Override public int ordine() { return 60; }

    @Override
    public List<Parametro> parametri() {
        return List.of(
            new Parametro("minutiPrima", "Si entra prima dell'inizio", "minuti", 10, false, null, 180),
            new Parametro("minutiDopoInizio", "Si entra fino a dopo l'inizio", "minuti", null, true, "fino alla fine del corso", 600));
    }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        if (c.carnetUtilizzabileOra(p, true).isPresent()) return List.of();
        List<Frequenze.TurnoDelGiorno> turni = c.turniOggi();
        if (turni.stream().anyMatch(t -> c.inFinestra(t, p))) return List.of();
        // un carnet a orario valido ma fuori dai suoi orari
        var aOrario = c.carnetValido().filter(Clienti.AbbonamentoBreve::aOrario);
        if (turni.isEmpty() && aOrario.isPresent()) {
            List<Frequenze.TurnoDelGiorno> delCarnet = c.turniCarnetOggi().getOrDefault(aOrario.get().corsoId(), List.of());
            if (delCarnet.isEmpty())
                return List.of(Motivo.blocca("FUORI_ORARIO", regola(), "Il carnet vale negli orari di " + aOrario.get().corso() + ", che oggi non c'è.",
                    "Tornare in un giorno in cui c'è " + aOrario.get().corso() + "."));
            return List.of(fuoriOrario(delCarnet, c, p, "Tornare negli orari del carnet."));
        }
        if (turni.isEmpty()) {
            if (!c.corsoValido()) return List.of(); // senza abbonamento né turni ci pensa il controllo dell'abbonamento
            return List.of(Motivo.blocca("NESSUN_TURNO_OGGI", regola(), "Oggi non ha turni del corso.", "Controllare i turni nella scheda cliente."));
        }
        return List.of(fuoriOrario(turni, c, p, "Tornare nella fascia del proprio turno."));
    }

    /** Il prossimo turno della giornata, altrimenti l'ultimo già passato. */
    private Motivo fuoriOrario(List<Frequenze.TurnoDelGiorno> turni, Contesto c, Parametri p, String rimedio) {
        Frequenze.TurnoDelGiorno t = turni.stream().filter(x -> Contesto.finestra(x, p)[0].isAfter(c.ora())).findFirst().orElse(turni.getLast());
        LocalTime[] f = Contesto.finestra(t, p);
        String quando = t.corso() + " " + Contesto.ora(t.inizio()) + "–" + Contesto.ora(t.fine());
        String msg = f[0].isAfter(c.ora())
            ? "Fuori orario: " + quando + ", si entra dalle " + Contesto.ora(f[0]) + "."
            : "Fuori orario: " + quando + ", si entrava fino alle " + Contesto.ora(f[1]) + ".";
        return Motivo.blocca("FUORI_ORARIO", regola(), msg, rimedio);
    }
}
