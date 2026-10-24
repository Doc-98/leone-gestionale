package it.leonexiii.gestionale.pagamenti;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Saldi e movimenti del borsellino, usati dai pagamenti (pagare con il borsellino) e dal borsellino stesso. Il credito
 * in euro è fatto di «lotti» (ogni entrata con la sua scadenza): le uscite consumano prima quelli che scadono prima.
 */
@Component
class Movimenti {

    /** Un'entrata in euro con quello che ne resta. */
    record Lotto(LocalDate scadenza, BigDecimal residuo) {}

    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final MovimentoRepository movimenti;
    private final RegoleBorsellinoRepository regole;
    private final Clock orologio;

    Movimenti(MovimentoRepository movimenti, RegoleBorsellinoRepository regole, Clock orologio) {
        this.movimenti = movimenti;
        this.regole = regole;
        this.orologio = orologio;
    }

    RegoleBorsellino regole() {
        return regole.findById((short) 1).orElseThrow();
    }

    void blocca(UUID persona) {
        movimenti.blocca(persona.toString());
    }

    BigDecimal euro(UUID persona) {
        return movimenti.somma(persona, MovimentoBorsellino.Saldo.EURO.name()).setScale(2);
    }

    int crediti(UUID persona) {
        return movimenti.somma(persona, MovimentoBorsellino.Saldo.CREDITI.name()).intValue();
    }

    /** Fino a quando vale un credito in euro che entra oggi (null = non scade). */
    LocalDate scadenzaNuova() {
        Integer mesi = regole().dto().validitaEuroMesi();
        return mesi == null ? null : LocalDate.now(orologio).plusMonths(mesi);
    }

    MovimentoBorsellino salva(MovimentoBorsellino m) {
        return movimenti.saveAndFlush(m);
    }

    /** I lotti in euro con quello che ne resta, dal primo che scade (senza scadenza in fondo). */
    List<Lotto> lotti(UUID persona) {
        return lotti(movimenti.findByPersonaIdAndSaldoOrderByCreatoIlAsc(persona, MovimentoBorsellino.Saldo.EURO.name()));
    }

    static List<Lotto> lotti(List<MovimentoBorsellino> euro) {
        List<MovimentoBorsellino> entrate = euro.stream().filter(m -> m.getQuantita().signum() > 0)
            .sorted(Comparator.comparing(MovimentoBorsellino::getScadenza, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MovimentoBorsellino::getCreatoIl)).toList();
        BigDecimal uscite = euro.stream().filter(m -> m.getQuantita().signum() < 0).map(MovimentoBorsellino::getQuantita).reduce(ZERO, BigDecimal::add).negate();
        List<Lotto> out = new ArrayList<>();
        for (MovimentoBorsellino e : entrate) {
            BigDecimal consumato = uscite.min(e.getQuantita());
            uscite = uscite.subtract(consumato);
            BigDecimal resto = e.getQuantita().subtract(consumato);
            if (resto.signum() > 0) out.add(new Lotto(e.getScadenza(), resto.setScale(2)));
        }
        return out;
    }

    /** Toglie euro dal borsellino (es. per pagare): se non bastano, errore. */
    MovimentoBorsellino togliEuro(UUID persona, BigDecimal importo, MovimentoBorsellino.Causale causale, String descrizione, UUID pagamentoId, UUID da) {
        blocca(persona);
        BigDecimal saldo = euro(persona);
        if (saldo.compareTo(importo) < 0)
            throw new IllegalArgumentException("Nel borsellino ci sono " + saldo + " €: non bastano per " + importo + " €.");
        return salva(new MovimentoBorsellino(persona, MovimentoBorsellino.Saldo.EURO, importo.negate(), causale, descrizione, null, da, orologio.instant())
            .conPagamento(pagamentoId));
    }

    /** Un pagamento annullato: tornano gli euro spesi col borsellino, e si tolgono i crediti comprati con una ricarica. */
    void annullaPagamento(UUID pagamentoId, UUID da) {
        for (MovimentoBorsellino m : movimenti.findByPagamentoId(pagamentoId)) {
            if (m.causale() != MovimentoBorsellino.Causale.PAGAMENTO && m.causale() != MovimentoBorsellino.Causale.RICARICA) continue;
            blocca(m.getPersonaId());
            String descr = m.causale() == MovimentoBorsellino.Causale.PAGAMENTO ? "Pagamento annullato: tornano gli euro" : "Ricarica annullata: si tolgono i crediti";
            salva(new MovimentoBorsellino(m.getPersonaId(), m.saldo(), m.getQuantita().negate(), MovimentoBorsellino.Causale.ANNULLAMENTO, descr,
                scadenzaNuova(), da, orologio.instant()).conPagamento(pagamentoId));
        }
    }
}
