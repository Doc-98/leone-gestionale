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

    protected ImpostazioniCassa() {}

    void modifica(CassaService.ImpostazioniDto in) {
        if (!CASSE.contains(in.casse())) throw new IllegalArgumentException("Scegli se c'è una cassa sola o una per postazione.");
        if (!FONDO.contains(in.fondo())) throw new IllegalArgumentException("Scegli come funziona il fondo cassa.");
        if (!CHIUSURA.contains(in.chiusura())) throw new IllegalArgumentException("Scegli quando si chiude la cassa.");
        this.casse = in.casse();
        this.fondo = in.fondo();
        this.importoFondo = CassaService.euro(in.importoFondo(), "il fondo cassa");
        this.chiusura = in.chiusura();
    }

    CassaService.ImpostazioniDto dto() {
        return new CassaService.ImpostazioniDto(casse, fondo, importoFondo, chiusura);
    }

    boolean perPostazione() { return "PER_POSTAZIONE".equals(casse); }
    String getFondo() { return fondo; }
    BigDecimal getImportoFondo() { return importoFondo; }
    String getChiusura() { return chiusura; }
}
