package it.leonexiii.gestionale.accessi;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

/** Braccialetto smarrito o disattivato: vale anche con il golden pass (chi lo trova non deve entrare). */
@Component
class ControlloBraccialetto implements ControlloAccesso {

    @Override public String regola() { return "BRACCIALETTO"; }
    @Override public String nome() { return "Braccialetto smarrito o disattivato"; }
    @Override public String descrizione() {
        return "Rosso se il braccialetto è segnato come smarrito o è stato disattivato (restituito, rotto). Vale anche con il golden pass.";
    }
    @Override public int ordine() { return 10; }
    @Override public boolean valeConGoldenPass() { return true; }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        Braccialetto b = c.braccialetto();
        if (b == null) return List.of();
        LocalDate dal = LocalDate.ofInstant(b.getStatoDal(), ZoneId.of("Europe/Rome"));
        long altri = c.braccialettiAttivi();
        String rimedioAltri = altri == 0 ? "La persona non ha braccialetti attivi: assegnargliene uno nuovo."
            : altri == 1 ? "La persona ha un altro braccialetto attivo." : "La persona ha " + altri + " braccialetti attivi.";
        return switch (b.stato()) {
            case SMARRITO -> List.of(Motivo.blocca("BRACCIALETTO_SMARRITO", regola(), "Braccialetto segnato come smarrito il " + Contesto.data(dal) + ".",
                "Se è stato ritrovato, segnarlo come ritrovato nella scheda. " + rimedioAltri));
            case DISATTIVATO -> List.of(Motivo.blocca("BRACCIALETTO_DISATTIVATO", regola(), "Braccialetto disattivato il " + Contesto.data(dal) + ".",
                rimedioAltri));
            case ATTIVO -> List.of();
        };
    }
}
