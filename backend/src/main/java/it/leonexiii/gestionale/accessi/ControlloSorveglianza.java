package it.leonexiii.gestionale.accessi;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Persona o braccialetto «sotto controllo» (l'alert list del vecchio gestionale): non blocca, ma l'esito porta un avviso
 * e la segreteria viene avvisata a ogni passaggio, in entrata e in uscita.
 */
@Component
class ControlloSorveglianza implements ControlloAccesso {

    static final String REGOLA = "SORVEGLIANZA";

    @Override public String regola() { return REGOLA; }
    @Override public String nome() { return "Sotto controllo"; }
    @Override public String descrizione() {
        return "Non blocca: chi è sotto controllo entra, ma il tornello lo segnala e la segreteria riceve un avviso a ogni passaggio.";
    }
    @Override public int ordine() { return 25; }
    @Override public boolean valeConGoldenPass() { return true; }
    @Override public boolean valeInUscita() { return true; }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        List<Motivo> out = new ArrayList<>();
        for (Blocco b : c.blocchiAttivi()) {
            if (b.ban()) continue;
            boolean suQuesto = b.getBraccialettoId() != null && c.braccialetto() != null && b.getBraccialettoId().equals(c.braccialetto().getId());
            if (suQuesto) out.add(new Motivo("BRACCIALETTO_SOTTO_CONTROLLO", REGOLA, "Braccialetto sotto controllo: " + b.getMotivo(), "La segreteria è stata avvisata.", false));
            else if (b.getPersonaId() != null) out.add(new Motivo("PERSONA_SOTTO_CONTROLLO", REGOLA, "Sotto controllo: " + b.getMotivo(), "La segreteria è stata avvisata.", false));
        }
        return out;
    }
}
