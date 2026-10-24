package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** La quota annuale di una stagione (es. «2026/27»). */
@Entity
@Table(name = "tesseramento")
class Tesseramento {

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String stagione;
    @Column(nullable = false)
    private LocalDate dal;
    @Column(nullable = false)
    private LocalDate al;
    private BigDecimal importo;
    @Column(name = "pagato_il")
    private LocalDate pagatoIl;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;

    protected Tesseramento() {}

    Tesseramento(UUID personaId, String stagione, LocalDate dal, LocalDate al, BigDecimal importo, LocalDate pagatoIl, UUID registratoDa, Instant ora) {
        if (stagione == null || stagione.isBlank() || stagione.length() > 20) throw new IllegalArgumentException("Indica la stagione (es. 2026/27).");
        if (dal == null || al == null || al.isBefore(dal)) throw new IllegalArgumentException("Controlla le date del tesseramento.");
        if (importo != null && importo.signum() < 0) throw new IllegalArgumentException("L'importo non può essere negativo.");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.stagione = stagione;
        this.dal = dal;
        this.al = al;
        this.importo = importo;
        this.pagatoIl = pagatoIl;
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    /** Dagli addebiti: pagato tutto il «il», o non più (null). */
    void segnaPagato(LocalDate il) {
        this.pagatoIl = il;
    }

    void annulla(Instant ora) {
        if (annullatoIl == null) annullatoIl = ora;
    }

    boolean validoIl(LocalDate g) {
        return annullatoIl == null && !g.isBefore(dal) && !g.isAfter(al);
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getStagione() { return stagione; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    BigDecimal getImporto() { return importo; }
    LocalDate getPagatoIl() { return pagatoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
}
