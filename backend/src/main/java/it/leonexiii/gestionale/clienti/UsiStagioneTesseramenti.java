package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.societa.Societa;
import it.leonexiii.gestionale.societa.UsiStagione;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** I tesseramenti di una stagione (anche annullati) la tengono nello storico: il tesseramento ne ricorda il nome. */
@Component
@Order(2)
class UsiStagioneTesseramenti implements UsiStagione {

    private final TesseramentoRepository tesseramenti;

    UsiStagioneTesseramenti(TesseramentoRepository tesseramenti) {
        this.tesseramenti = tesseramenti;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Uso> usiDelNome(Societa.Stagione stagione) {
        return usi(stagione);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Uso> usi(Societa.Stagione stagione) {
        long n = tesseramenti.countByStagione(stagione.nome());
        return n == 0 ? List.of() : List.of(new Uso(n == 1 ? "1 tesseramento" : n + " tesseramenti", true));
    }
}
