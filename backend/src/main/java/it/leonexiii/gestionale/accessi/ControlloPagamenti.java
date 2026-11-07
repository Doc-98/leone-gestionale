package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.pagamenti.Pagamenti;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chi non paga (ADR 0018, parte 6): dopo un certo numero di giorni dalla scadenza di un pagamento il tornello avvisa
 * (verde, con la segnalazione per il tornellista), dopo altri giorni dà rosso. Spento di base; i giorni e la soglia
 * minima si scelgono nella pagina delle regole. Senza i giorni del rosso, avvisa soltanto.
 */
@Component
class ControlloPagamenti implements ControlloAccesso {

    static final String REGOLA = "PAGAMENTI";

    private final Pagamenti pagamenti;

    ControlloPagamenti(Pagamenti pagamenti) {
        this.pagamenti = pagamenti;
    }

    @Override public String regola() { return REGOLA; }
    @Override public String nome() { return "Pagamenti scaduti"; }
    @Override public String descrizione() {
        return "Per chi ha pagamenti scaduti e non saldati: prima un avviso al tornellista, poi, se si sceglie, il rosso.";
    }
    @Override public int ordine() { return 45; }

    @Override
    public List<Parametro> parametri() {
        return List.of(
            new Parametro("giorniAvviso", "Avvisa dopo la scadenza", "giorni", 7, false, null, 365),
            new Parametro("giorniRosso", "Rosso dopo la scadenza", "giorni", null, true, "mai: solo l'avviso", 365),
            new Parametro("sogliaEuro", "Solo se il dovuto supera", "€", 0, false, null, 10000));
    }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        var arretrato = pagamenti.arretratoDi(c.persona().id(), c.oggi()).orElse(null);
        if (arretrato == null || arretrato.importo().compareTo(BigDecimal.valueOf(p.intero("sogliaEuro", 0))) <= 0) return List.of();
        long giorni = arretrato.giorniDi(c.oggi());
        Integer rosso = p.intero("giorniRosso");
        String quanto = euro(arretrato.importo()) + (arretrato.voci() > 1 ? " (" + arretrato.voci() + " voci)" : "");
        String messaggio = "Da pagare " + quanto + ", scaduto dal " + Contesto.data(arretrato.primaScadenza()) + ".";
        if (rosso != null && giorni >= rosso)
            return List.of(Motivo.blocca("PAGAMENTO_SCADUTO", REGOLA, messaggio, "Pagare in segreteria (scheda del cliente, «Pagamenti»)."));
        if (giorni >= p.intero("giorniAvviso", 7))
            return List.of(new Motivo("PAGAMENTO_IN_RITARDO", REGOLA, messaggio,
                rosso == null ? "Ricordare di passare in segreteria." : "Ricordare di passare in segreteria: dal " + Contesto.data(arretrato.primaScadenza().plusDays(rosso))
                    + " il tornello darà rosso.", false));
        return List.of();
    }

    static String euro(BigDecimal importo) {
        return importo.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }
}
