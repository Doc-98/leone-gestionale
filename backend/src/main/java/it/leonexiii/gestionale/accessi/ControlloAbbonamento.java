package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Serve un abbonamento valido oggi: al corso, oppure un carnet con ingressi residui. Il carnet scala al primo
 * passaggio e non nei minuti di rientro successivi.
 */
@Component
class ControlloAbbonamento implements ControlloAccesso {

    @Override public String regola() { return "ABBONAMENTO"; }
    @Override public String nome() { return "Abbonamento o carnet"; }
    @Override public String descrizione() {
        return "Rosso se non c'è un abbonamento valido oggi: nessuno, scaduto, non ancora iniziato o carnet esaurito. "
            + "Il carnet scala un ingresso al primo passaggio; nei minuti di rientro i passaggi successivi non scalano.";
    }
    @Override public int ordine() { return 50; }

    @Override
    public List<Parametro> parametri() {
        return List.of(new Parametro("minutiRientro", "Rientro senza scalare il carnet", "minuti", 90, false, null, 600));
    }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        if (c.corsoValido() || c.carnetUtilizzabile().isPresent()) return List.of();
        List<Clienti.AbbonamentoBreve> tutti = c.cliente().abbonamenti();
        String rimedio = "Rinnovare l'abbonamento in segreteria.";
        var futuro = tutti.stream().filter(a -> a.dal().isAfter(c.oggi())).min(Comparator.comparing(Clienti.AbbonamentoBreve::dal));
        if (futuro.isPresent())
            return List.of(Motivo.blocca("ABBONAMENTO_NON_INIZIATO", regola(), descrivi(futuro.get()) + " inizia il " + Contesto.data(futuro.get().dal()) + ".",
                "Controllare le date dell'abbonamento nella scheda."));
        var esaurito = tutti.stream().filter(a -> a.carnet() && a.validoIl(c.oggi())).findFirst();
        if (esaurito.isPresent())
            return List.of(Motivo.blocca("CARNET_ESAURITO", regola(), "Carnet esaurito: usati " + esaurito.get().usati() + " ingressi su " + esaurito.get().ingressi() + ".",
                "Acquistare un nuovo carnet in segreteria."));
        var scaduto = tutti.stream().filter(a -> a.al() != null && a.al().isBefore(c.oggi())).max(Comparator.comparing(Clienti.AbbonamentoBreve::al));
        if (scaduto.isPresent())
            return List.of(Motivo.blocca("ABBONAMENTO_SCADUTO", regola(), descrivi(scaduto.get()) + " scaduto il " + Contesto.data(scaduto.get().al()) + ".", rimedio));
        return List.of(Motivo.blocca("ABBONAMENTO_MANCANTE", regola(), "Nessun abbonamento.", "Fare l'abbonamento in segreteria."));
    }

    private static String descrivi(Clienti.AbbonamentoBreve a) {
        return a.carnet() ? "Il carnet da " + a.ingressi() + " ingressi" : "L'abbonamento" + (a.corso() == null ? "" : " a " + a.corso());
    }
}
