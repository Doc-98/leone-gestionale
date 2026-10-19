package it.leonexiii.gestionale.accessi;

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
        return "Rosso se si passa fuori dalla fascia di un turno a cui la persona è iscritta quel giorno. "
            + "Non vale per chi ha un carnet utilizzabile.";
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
        if (c.carnetUtilizzabile().isPresent()) return List.of();
        List<Frequenze.TurnoDelGiorno> turni = c.turniOggi();
        if (turni.isEmpty()) {
            if (!c.corsoValido()) return List.of(); // senza abbonamento né turni ci pensa il controllo dell'abbonamento
            return List.of(Motivo.blocca("NESSUN_TURNO_OGGI", regola(), "Oggi non ha turni del corso.", "Controllare i turni nella scheda cliente."));
        }
        if (turni.stream().anyMatch(t -> c.inFinestra(t, p))) return List.of();
        // il prossimo turno della giornata, altrimenti l'ultimo già passato
        Frequenze.TurnoDelGiorno t = turni.stream().filter(x -> Contesto.finestra(x, p)[0].isAfter(c.ora())).findFirst().orElse(turni.getLast());
        LocalTime[] f = Contesto.finestra(t, p);
        String quando = t.corso() + " " + Contesto.ora(t.inizio()) + "–" + Contesto.ora(t.fine());
        String msg = f[0].isAfter(c.ora())
            ? "Fuori orario: " + quando + ", si entra dalle " + Contesto.ora(f[0]) + "."
            : "Fuori orario: " + quando + ", si entrava fino alle " + Contesto.ora(f[1]) + ".";
        return List.of(Motivo.blocca("FUORI_ORARIO", regola(), msg, "Tornare nella fascia del proprio turno."));
    }
}
