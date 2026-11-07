package it.leonexiii.gestionale.ricevute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Le scelte che valgono per tutte le ricevute (una riga sola). */
@Entity
@Table(name = "impostazioni_ricevute")
class Impostazioni {

    static final java.util.Set<String> INTESTATA = java.util.Set.of("PAGANTE", "BENEFICIARIO");
    static final java.util.Set<String> DOPO = java.util.Set.of("CHIEDI", "STAMPA", "NIENTE");
    static final java.util.Set<String> FAMIGLIA = java.util.Set.of("CHIEDI", "FAMIGLIA", "PERSONA");

    @Id
    private short id;
    @Column(nullable = false)
    private String intestata;
    @Column(name = "codici_fiscali")
    private boolean codiciFiscali;
    private boolean bollo;
    @Column(name = "bollo_soglia", nullable = false)
    private BigDecimal bolloSoglia;
    @Column(name = "bollo_importo", nullable = false)
    private BigDecimal bolloImporto;
    private String dicitura;
    private boolean firma;
    @Column(nullable = false)
    private String dopo;
    private boolean email;
    @Column(nullable = false)
    private String famiglia;

    protected Impostazioni() {}

    void modifica(RicevuteService.ImpostazioniDto in) {
        if (!INTESTATA.contains(in.intestata())) throw new IllegalArgumentException("Scegli a chi è intestata la ricevuta.");
        if (!DOPO.contains(in.dopo())) throw new IllegalArgumentException("Scegli cosa fare dopo un pagamento.");
        if (!FAMIGLIA.contains(in.famiglia())) throw new IllegalArgumentException("Scegli come fare le ricevute di più familiari.");
        if (in.bolloSoglia() == null || in.bolloSoglia().signum() < 0 || in.bolloSoglia().scale() > 2)
            throw new IllegalArgumentException("Scrivi la soglia della marca da bollo in euro (al massimo due decimali).");
        if (in.bolloImporto() == null || in.bolloImporto().signum() < 0 || in.bolloImporto().scale() > 2)
            throw new IllegalArgumentException("Scrivi l'importo della marca da bollo in euro (al massimo due decimali).");
        if (in.dicitura() != null && in.dicitura().length() > 1000) throw new IllegalArgumentException("La dicitura è troppo lunga (al massimo 1000 caratteri).");
        this.intestata = in.intestata();
        this.codiciFiscali = in.codiciFiscali();
        this.bollo = in.bollo();
        this.bolloSoglia = in.bolloSoglia();
        this.bolloImporto = in.bolloImporto();
        this.dicitura = in.dicitura() == null || in.dicitura().isBlank() ? null : in.dicitura().strip();
        this.firma = in.firma();
        this.dopo = in.dopo();
        this.email = in.email();
        this.famiglia = in.famiglia();
    }

    String getIntestata() { return intestata; }
    boolean isCodiciFiscali() { return codiciFiscali; }
    boolean isBollo() { return bollo; }
    BigDecimal getBolloSoglia() { return bolloSoglia; }
    BigDecimal getBolloImporto() { return bolloImporto; }
    String getDicitura() { return dicitura; }
    boolean isFirma() { return firma; }
    String getDopo() { return dopo; }
    boolean isEmail() { return email; }
    String getFamiglia() { return famiglia; }
}
