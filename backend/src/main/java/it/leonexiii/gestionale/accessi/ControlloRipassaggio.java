package it.leonexiii.gestionale.accessi;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Cooldown dopo un passaggio: per qualche minuto lo stesso braccialetto non riapre il tornello, così non si fa passare
 * un amico subito dopo. Chi ha il permesso di accompagnatore può ripassare. Spento di base.
 */
@Component
class ControlloRipassaggio implements ControlloAccesso {

    @Override public String regola() { return "RIPASSAGGIO"; }
    @Override public String nome() { return "Cooldown dopo un passaggio"; }
    @Override public String descrizione() {
        return "Rosso se lo stesso braccialetto è già entrato da pochi minuti (contro il passaggio del braccialetto a un amico). "
            + "Non vale per chi ha il permesso di accompagnatore.";
    }
    @Override public int ordine() { return 15; }

    @Override
    public List<Parametro> parametri() {
        return List.of(new Parametro("minuti", "Il braccialetto resta fermo per", "minuti", 5, false, null, 240));
    }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        if (c.ultimoVerdeInEntrata() == null || c.haAutorizzazione(Autorizzazione.Tipo.ACCOMPAGNATORE)) return List.of();
        int minuti = p.intero("minuti", 5);
        Duration trascorso = Duration.between(c.ultimoVerdeInEntrata(), c.istante());
        if (trascorso.isNegative() || trascorso.toMinutes() >= minuti) return List.of();
        LocalTime prima = LocalTime.ofInstant(c.ultimoVerdeInEntrata(), ZoneId.of("Europe/Rome"));
        return List.of(Motivo.blocca("PASSAGGIO_RIPETUTO", regola(), "Braccialetto già passato alle " + Contesto.ora(prima) + ": si può ripassare dalle "
            + Contesto.ora(prima.plusMinutes(minuti)) + ".", "Se accompagna altre persone, registrare il permesso di accompagnatore."));
    }
}
