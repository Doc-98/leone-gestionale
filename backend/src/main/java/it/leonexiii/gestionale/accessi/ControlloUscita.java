package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.identita.Persone;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * All'uscita (lettore sul lato d'uscita): i minorenni escono da soli solo con il permesso di uscita autonoma firmato da
 * un genitore. Spento di base: oggi il tornello non controlla le uscite.
 */
@Component
class ControlloUscita implements ControlloAccesso {

    private final Persone persone;

    ControlloUscita(Persone persone) {
        this.persone = persone;
    }

    @Override public String regola() { return "USCITA"; }
    @Override public String nome() { return "Uscita dei minorenni"; }
    @Override public String descrizione() {
        return "All'uscita: rosso per i minorenni senza il permesso di uscita autonoma firmato da un genitore (devono uscire con un adulto).";
    }
    @Override public int ordine() { return 70; }
    @Override public boolean valeInEntrata() { return false; }
    @Override public boolean valeInUscita() { return true; }

    @Override
    public List<Parametro> parametri() {
        return List.of(new Parametro("etaMinima", "Esce da solo senza permesso da", "anni", 18, false, null, 99));
    }

    @Override
    public List<Motivo> verifica(Contesto c, Parametri p) {
        Integer eta = c.eta();
        if (eta != null && eta >= p.intero("etaMinima", 18)) return List.of();
        if (c.haAutorizzazione(Autorizzazione.Tipo.USCITA_AUTONOMA)) return List.of();
        String msg = eta == null ? "Data di nascita mancante e nessun permesso di uscita autonoma." : "Minorenne senza permesso di uscita autonoma.";
        List<String> delegati = persone.tutoriDi(c.persona().id()).stream().filter(Persone.Familiare::delegatoRitiro)
            .map(t -> t.nome() + " " + t.cognome() + " (" + t.relazione().toLowerCase(java.util.Locale.ROOT) + ")").toList();
        String rimedio = delegati.isEmpty() ? "Deve uscire con un genitore o un adulto delegato." : "Può uscire con: " + String.join(", ", delegati) + ".";
        return List.of(Motivo.blocca("USCITA_NON_AUTORIZZATA", regola(), msg, rimedio));
    }
}
