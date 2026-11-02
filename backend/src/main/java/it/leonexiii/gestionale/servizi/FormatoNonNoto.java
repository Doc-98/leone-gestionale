package it.leonexiii.gestionale.servizi;

import it.leonexiii.gestionale.condiviso.Conflitto;
import java.util.List;
import java.util.Map;

/**
 * Il formato finché non si conosce quello vero (ADR 0025): non legge e non scrive niente, e lo dice. Le ricariche sul
 * braccialetto restano bloccate; si può comunque scrivere a mano il saldo mostrato dal display delle macchine.
 */
class FormatoNonNoto implements FormatoBraccialetto {

    private static final String NOTA = "Non conosciamo ancora come le macchine scrivono i crediti sul braccialetto (formato e crittografia): "
        + "finché non lo sappiamo il gestionale non può leggerli né scriverli.";

    public String codice() { return "NON_NOTO"; }

    public String nome() { return "Non ancora noto"; }

    public boolean pronto() { return false; }

    public String nota() { return NOTA; }

    public Accesso accesso() { return new Accesso(null, List.of(), List.of()); }

    public int crediti(MemoriaBraccialetto memoria) { throw new Conflitto(NOTA); }

    public Map<Integer, String> scrivi(MemoriaBraccialetto attuale, int crediti) { throw new Conflitto(NOTA); }
}
