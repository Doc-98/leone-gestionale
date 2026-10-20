package it.leonexiii.gestionale.accessi;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Ban del braccialetto o della persona (tutti i suoi braccialetti). Vale anche con il golden pass. */
@Component
class ControlloBan implements ControlloAccesso {

    @Override public String regola() { return "BAN"; }
    @Override public String nome() { return "Ban"; }
    @Override public String descrizione() {
        return "Rosso se è bannato il braccialetto oppure la persona (vale per tutti i suoi braccialetti).";
    }
    @Override public int ordine() { return 20; }
    @Override public boolean valeConGoldenPass() { return true; }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        List<Motivo> out = new ArrayList<>();
        for (Blocco b : c.blocchiAttivi()) {
            if (!b.ban()) continue;
            String fino = b.getAl() == null ? "" : " (fino al " + Contesto.data(b.getAl()) + ")";
            if (b.getBraccialettoId() != null && c.braccialetto() != null && b.getBraccialettoId().equals(c.braccialetto().getId()))
                out.add(Motivo.blocca("BRACCIALETTO_BANNATO", regola(), "Braccialetto bannato" + fino + ": " + b.getMotivo(), "Rivolgersi alla segreteria."));
            else if (b.getPersonaId() != null)
                out.add(Motivo.blocca("PERSONA_BANNATA", regola(), "Persona bannata" + fino + ": " + b.getMotivo(), "Rivolgersi alla segreteria."));
        }
        return out;
    }
}
