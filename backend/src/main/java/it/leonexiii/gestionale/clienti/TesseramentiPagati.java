package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.pagamenti.Pagamenti;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Quando la quota associativa risulta pagata (o un pagamento viene annullato), il tesseramento lo ricorda. */
@Component
class TesseramentiPagati {

    private final TesseramentoRepository tesseramenti;

    TesseramentiPagati(TesseramentoRepository tesseramenti) {
        this.tesseramenti = tesseramenti;
    }

    @EventListener
    void saldato(Pagamenti.Saldato e) {
        if (e.origine() != Pagamenti.Origine.TESSERAMENTO) return;
        tesseramenti.findById(e.riferimentoId()).ifPresent(t -> t.segnaPagato(e.saldatoIl()));
    }
}
