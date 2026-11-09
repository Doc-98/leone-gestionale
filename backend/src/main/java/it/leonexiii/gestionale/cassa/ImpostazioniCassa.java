package it.leonexiii.gestionale.cassa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Le scelte della cassa (una riga sola): una cassa o una per postazione; il fondo fisso, riportato dalla chiusura
 * prima o ricontato a ogni apertura; chiusura a fine turno (con l'apertura) o a fine giornata.
 */
@Entity
@Table(name = "impostazioni_cassa")
class ImpostazioniCassa {

    static final Set<String> CASSE = Set.of("UNICA", "PER_POSTAZIONE");
    static final Set<String> FONDO = Set.of("FISSO", "RIPORTATO", "CONTATO");
    static final Set<String> CHIUSURA = Set.of("TURNO", "GIORNATA");
    static final Set<String> CONTROLLO = Set.of("NESSUNA", "OLTRE", "TUTTE");

    @Id
    private short id;
    @Column(nullable = false)
    private String casse;
    @Column(nullable = false)
    private String fondo;
    @Column(name = "importo_fondo", nullable = false)
    private BigDecimal importoFondo;
    @Column(nullable = false)
    private String chiusura;
    private boolean cieca;
    @Column(nullable = false)
    private BigDecimal tolleranza;
    @Column(nullable = false)
    private String controllo;
    private boolean spese;
    private boolean banca;
    @Column(name = "banca_dal")
    private java.time.Instant bancaDal;
    @Column(name = "ore_avviso")
    private int oreAvviso;

    protected ImpostazioniCassa() {}

    /** Le scelte nuove lasciate vuote restano come sono. Accendere il conto della banca lo fa partire da adesso. */
    void modifica(CassaService.ImpostazioniDto in, java.time.Instant ora) {
        if (!CASSE.contains(in.casse())) throw new IllegalArgumentException("Scegli se c'è una cassa sola o una per postazione.");
        if (!FONDO.contains(in.fondo())) throw new IllegalArgumentException("Scegli come funziona il fondo cassa.");
        if (!CHIUSURA.contains(in.chiusura())) throw new IllegalArgumentException("Scegli quando si chiude la cassa.");
        this.casse = in.casse();
        this.fondo = in.fondo();
        this.importoFondo = CassaService.euro(in.importoFondo(), "il fondo cassa");
        this.chiusura = in.chiusura();
        if (in.controllo() != null && !CONTROLLO.contains(in.controllo())) throw new IllegalArgumentException("Scegli quali chiusure si controllano.");
        if (in.oreAvviso() != null && (in.oreAvviso() < 0 || in.oreAvviso() > 72)) throw new IllegalArgumentException("L'avviso va da 0 (mai) a 72 ore.");
        if (in.cieca() != null) this.cieca = in.cieca();
        if (in.tolleranza() != null) this.tolleranza = CassaService.euro(in.tolleranza(), "la differenza tollerata");
        if (in.controllo() != null) this.controllo = in.controllo();
        if (in.spese() != null) this.spese = in.spese();
        if (in.banca() != null) {
            if (in.banca() && !banca) this.bancaDal = ora;
            this.banca = in.banca();
        }
        if (in.oreAvviso() != null) this.oreAvviso = in.oreAvviso();
    }

    CassaService.ImpostazioniDto dto() {
        return new CassaService.ImpostazioniDto(casse, fondo, importoFondo, chiusura, cieca, tolleranza, controllo, spese, banca, oreAvviso);
    }

    /** La differenza va oltre la tolleranza (con tolleranza 0, ogni differenza). */
    boolean oltre(BigDecimal differenza) {
        return tolleranza.signum() == 0 ? differenza.signum() != 0 : differenza.abs().compareTo(tolleranza) > 0;
    }

    boolean isCieca() { return cieca; }
    String getControllo() { return controllo; }
    boolean isSpese() { return spese; }
    boolean isBanca() { return banca; }
    java.time.Instant getBancaDal() { return bancaDal; }
    int getOreAvviso() { return oreAvviso; }

    boolean perPostazione() { return "PER_POSTAZIONE".equals(casse); }
    String getFondo() { return fondo; }
    BigDecimal getImportoFondo() { return importoFondo; }
    String getChiusura() { return chiusura; }
}
