package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * La copia di un metodo di pagamento presa quando si registra un pagamento o un'uscita: il nome e le proprietà che servono
 * allo storico e ai conti. Lo storico legge questa, non l'elenco dei metodi: così un metodo eliminato (o cambiato dopo)
 * non lascia «?» né cambia i conti passati.
 */
@Embeddable
class CopiaMetodo {

    @Column(name = "metodo_nome", nullable = false)
    private String nome;
    @Column(name = "metodo_contanti", nullable = false)
    private boolean contanti;
    @Column(name = "metodo_da_incassare", nullable = false)
    private boolean daIncassare;
    @Column(name = "metodo_riscontro", nullable = false)
    private boolean riscontro;
    @Column(name = "metodo_borsellino", nullable = false)
    private boolean borsellino;

    protected CopiaMetodo() {}

    CopiaMetodo(MetodoPagamento m) {
        this.nome = m.getNome();
        this.contanti = m.isContanti();
        this.daIncassare = m.isDaIncassare();
        this.riscontro = m.isRiscontro();
        this.borsellino = m.isBorsellino();
    }

    String getNome() { return nome; }
    boolean isContanti() { return contanti; }
    boolean isDaIncassare() { return daIncassare; }
    boolean isRiscontro() { return riscontro; }
    boolean isBorsellino() { return borsellino; }
}
