package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Certificato medico valido fino al giorno di scadenza compreso; dal giorno dopo blocca. */
@Component
class ControlloCertificato implements ControlloAccesso {

    @Override public String regola() { return "CERTIFICATO"; }
    @Override public String nome() { return "Certificato medico"; }
    @Override public String descrizione() { return "Rosso se manca il certificato medico o è scaduto (blocca dal giorno dopo la scadenza)."; }
    @Override public int ordine() { return 30; }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        Optional<Clienti.Certificato> ultimo = c.cliente().certificati().stream().max(Comparator.comparing(Clienti.Certificato::scadenza));
        if (ultimo.isEmpty())
            return List.of(Motivo.blocca("CERTIFICATO_MANCANTE", regola(), "Nessun certificato medico registrato.", "Portare il certificato medico in segreteria."));
        if (ultimo.get().scadenza().isBefore(c.oggi()))
            return List.of(Motivo.blocca("CERTIFICATO_SCADUTO", regola(), "Certificato medico scaduto il " + Contesto.data(ultimo.get().scadenza()) + ".",
                "Portare il nuovo certificato medico in segreteria."));
        return List.of();
    }
}
