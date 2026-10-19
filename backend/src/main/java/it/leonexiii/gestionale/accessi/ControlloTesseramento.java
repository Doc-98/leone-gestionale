package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import java.util.List;
import org.springframework.stereotype.Component;

/** Il tesseramento annuale (la quota della stagione) deve coprire la data di oggi. */
@Component
class ControlloTesseramento implements ControlloAccesso {

    @Override public String regola() { return "TESSERAMENTO"; }
    @Override public String nome() { return "Tesseramento annuale"; }
    @Override public String descrizione() { return "Rosso se il tesseramento della stagione (la quota annuale) non è registrato o è scaduto."; }
    @Override public int ordine() { return 40; }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        List<Clienti.TesseramentoBreve> tutti = c.cliente().tesseramenti();
        if (tutti.stream().anyMatch(t -> !c.oggi().isBefore(t.dal()) && !c.oggi().isAfter(t.al()))) return List.of();
        String rimedio = "Pagare il tesseramento annuale in segreteria.";
        var prossimo = tutti.stream().filter(t -> t.dal().isAfter(c.oggi())).findFirst();
        if (prossimo.isPresent())
            return List.of(Motivo.blocca("TESSERAMENTO_NON_INIZIATO", regola(), "Il tesseramento " + prossimo.get().stagione() + " vale dal "
                + Contesto.data(prossimo.get().dal()) + ".", "Controllare le date del tesseramento nella scheda."));
        if (!tutti.isEmpty())
            return List.of(Motivo.blocca("TESSERAMENTO_SCADUTO", regola(), "Tesseramento " + tutti.getFirst().stagione() + " scaduto il "
                + Contesto.data(tutti.getFirst().al()) + ".", rimedio));
        return List.of(Motivo.blocca("TESSERAMENTO_MANCANTE", regola(), "Tesseramento annuale non registrato.", rimedio));
    }
}
