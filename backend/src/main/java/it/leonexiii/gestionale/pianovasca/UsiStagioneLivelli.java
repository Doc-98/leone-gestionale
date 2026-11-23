package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.societa.Societa;
import it.leonexiii.gestionale.societa.UsiStagione;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gli esiti dei livelli (chi passa, chi continua) sono lo storico di una stagione: finché ci sono, non si elimina. */
@Component
@Order(3)
class UsiStagioneLivelli implements UsiStagione {

    private final EsitoLivelloRepository esiti;

    UsiStagioneLivelli(EsitoLivelloRepository esiti) {
        this.esiti = esiti;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Uso> usi(Societa.Stagione stagione) {
        long n = esiti.countByStagioneId(stagione.id());
        return n == 0 ? List.of() : List.of(new Uso(n == 1 ? "1 esito dei livelli" : n + " esiti dei livelli", true));
    }
}
