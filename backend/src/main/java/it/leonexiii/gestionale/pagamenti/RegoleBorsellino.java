package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Set;

/** Le regole del borsellino e del braccialetto: una riga, le decide la società. */
@Entity
@Table(name = "regole_borsellino")
class RegoleBorsellino {

    static final Set<String> MODI_CAUZIONE = Set.of("DENARO", "BORSELLINO", "A_SCELTA");
    static final Set<String> MODI_SMARRIMENTO = Set.of("RESTANO", "PERSI");

    @Id
    private short id;
    @Column(name = "valore_credito", nullable = false)
    private BigDecimal valoreCredito;
    @Column(name = "ricarica_minima", nullable = false)
    private BigDecimal ricaricaMinima;
    @Column(name = "validita_euro_mesi")
    private Integer validitaEuroMesi;
    @Column(nullable = false)
    private boolean trasferibile;
    @Column(nullable = false)
    private boolean restituibile;
    @Column(nullable = false)
    private BigDecimal cauzione;
    @Column(name = "cauzione_riconsegna", nullable = false)
    private String cauzioneRiconsegna;
    @Column(name = "crediti_riconsegna", nullable = false)
    private boolean creditiRiconsegna;
    @Column(name = "crediti_smarrimento", nullable = false)
    private String creditiSmarrimento;

    protected RegoleBorsellino() {}

    void modifica(Borsellino.Regole r) {
        if (r.valoreCredito() == null || r.valoreCredito().signum() <= 0 || r.valoreCredito().scale() > 2)
            throw new IllegalArgumentException("Il valore di un credito è in euro, maggiore di zero (es. 0,20).");
        if (r.ricaricaMinima() == null || r.ricaricaMinima().signum() < 0) throw new IllegalArgumentException("La ricarica minima non può essere negativa.");
        if (r.validitaEuroMesi() != null && (r.validitaEuroMesi() < 1 || r.validitaEuroMesi() > 120))
            throw new IllegalArgumentException("La validità del credito va da 1 a 120 mesi (vuota = non scade).");
        if (r.cauzione() == null || r.cauzione().signum() < 0) throw new IllegalArgumentException("La cauzione non può essere negativa (0 = nessuna).");
        if (!MODI_CAUZIONE.contains(r.cauzioneRiconsegna())) throw new IllegalArgumentException("Restituzione della cauzione: DENARO, BORSELLINO o A_SCELTA.");
        if (!MODI_SMARRIMENTO.contains(r.creditiSmarrimento())) throw new IllegalArgumentException("Crediti di un braccialetto smarrito: RESTANO o PERSI.");
        this.valoreCredito = r.valoreCredito();
        this.ricaricaMinima = r.ricaricaMinima();
        this.validitaEuroMesi = r.validitaEuroMesi();
        this.trasferibile = r.trasferibile();
        this.restituibile = r.restituibile();
        this.cauzione = r.cauzione();
        this.cauzioneRiconsegna = r.cauzioneRiconsegna();
        this.creditiRiconsegna = r.creditiRiconsegna();
        this.creditiSmarrimento = r.creditiSmarrimento();
    }

    Borsellino.Regole dto() {
        return new Borsellino.Regole(valoreCredito, ricaricaMinima, validitaEuroMesi, trasferibile, restituibile, cauzione, cauzioneRiconsegna,
            creditiRiconsegna, creditiSmarrimento);
    }
}
