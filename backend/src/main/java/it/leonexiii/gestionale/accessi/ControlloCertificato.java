package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Certificato medico valido fino al giorno di scadenza compreso; dal giorno dopo blocca. Un certificato provvisorio
 * (dichiarazione firmata in attesa del certificato) vale al massimo N giorni dalla firma e fa passare con un avviso.
 */
@Component
class ControlloCertificato implements ControlloAccesso {

    @Override public String regola() { return "CERTIFICATO"; }
    @Override public String nome() { return "Certificato medico"; }
    @Override public String descrizione() {
        return "Rosso se manca il certificato medico o è scaduto (blocca dal giorno dopo la scadenza). "
            + "Un certificato provvisorio fa entrare per pochi giorni, con un avviso.";
    }
    @Override public int ordine() { return 30; }

    @Override
    public List<Parametro> parametri() {
        return List.of(new Parametro("giorniMaxProvvisorio", "Durata massima del certificato provvisorio", "giorni", 30, false, null, 90));
    }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        int giorni = Math.max(1, p.intero("giorniMaxProvvisorio", 30));
        List<Clienti.Certificato> tutti = c.cliente().certificati();
        if (tutti.isEmpty())
            return List.of(Motivo.blocca("CERTIFICATO_MANCANTE", regola(), "Nessun certificato medico registrato.", "Portare il certificato medico in segreteria."));
        Optional<Clienti.Certificato> definitivo = tutti.stream().filter(x -> !x.provvisorio() && !x.scadenza().isBefore(c.oggi())).findFirst();
        if (definitivo.isPresent()) return List.of();
        Optional<Clienti.Certificato> provvisorio = tutti.stream().filter(x -> x.provvisorio() && !x.validoFino(giorni).isBefore(c.oggi()))
            .max(Comparator.comparing(x -> x.validoFino(giorni)));
        if (provvisorio.isPresent())
            return List.of(new Motivo("CERTIFICATO_PROVVISORIO", regola(), "Certificato provvisorio, valido fino al " + Contesto.data(provvisorio.get().validoFino(giorni)) + ".",
                "Ricordare di portare il certificato medico.", false));
        LocalDate ultima = tutti.stream().map(x -> x.validoFino(giorni)).max(Comparator.naturalOrder()).orElseThrow();
        return List.of(Motivo.blocca("CERTIFICATO_SCADUTO", regola(), "Certificato medico scaduto il " + Contesto.data(ultima) + ".",
            "Portare il nuovo certificato medico in segreteria."));
    }
}
