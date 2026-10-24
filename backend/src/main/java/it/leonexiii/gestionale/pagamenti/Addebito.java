package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cosa una persona deve, entro quando. Con importo negativo è un credito (es. il rimborso per recesso). */
@Entity
@Table(name = "addebito")
class Addebito {

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String descrizione;
    @Column(nullable = false)
    private BigDecimal importo;
    @Column(nullable = false)
    private LocalDate scadenza;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pagamenti.Origine origine;
    @Column(name = "riferimento_id")
    private UUID riferimentoId;
    @Column(name = "offerta_id")
    private UUID offertaId;
    private Integer rata;
    private Integer rate;
    private String note;
    @Column(name = "creato_da")
    private UUID creatoDa;
    @Column(name = "creato_il", nullable = false)
    private Instant creatoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;
    @Column(name = "annullato_da")
    private UUID annullatoDa;
    @Column(name = "motivo_annullamento")
    private String motivoAnnullamento;

    protected Addebito() {}

    Addebito(Pagamenti.NuovoAddebito in, UUID da, Instant ora) {
        if (in.personaId() == null) throw new IllegalArgumentException("Manca la persona dell'addebito.");
        if (in.descrizione() == null || in.descrizione().isBlank()) throw new IllegalArgumentException("Scrivi la descrizione dell'addebito.");
        if (in.descrizione().strip().length() > 200) throw new IllegalArgumentException("La descrizione è troppo lunga (al massimo 200 caratteri).");
        if (in.importo() == null || in.importo().signum() == 0) throw new IllegalArgumentException("Scrivi l'importo dell'addebito.");
        if (in.importo().scale() > 2) throw new IllegalArgumentException("L'importo ha al massimo due decimali.");
        if (in.scadenza() == null) throw new IllegalArgumentException("Manca la scadenza dell'addebito.");
        if ((in.rata() == null) != (in.rate() == null) || (in.rata() != null && (in.rata() < 1 || in.rata() > in.rate())))
            throw new IllegalArgumentException("Rata non valida.");
        if (in.note() != null && in.note().length() > 300) throw new IllegalArgumentException("Le note sono troppo lunghe (al massimo 300 caratteri).");
        this.id = UUID.randomUUID();
        this.personaId = in.personaId();
        this.descrizione = in.descrizione().strip();
        this.importo = in.importo();
        this.scadenza = in.scadenza();
        this.origine = in.origine() == null ? Pagamenti.Origine.ALTRO : in.origine();
        this.riferimentoId = in.riferimentoId();
        this.offertaId = in.offertaId();
        this.rata = in.rata();
        this.rate = in.rate();
        this.note = in.note() == null || in.note().isBlank() ? null : in.note().strip();
        this.creatoDa = da;
        this.creatoIl = ora;
    }

    void annulla(String motivo, UUID da, Instant ora) {
        if (annullatoIl != null) throw new IllegalArgumentException("L'addebito è già annullato.");
        this.annullatoIl = ora;
        this.annullatoDa = da;
        this.motivoAnnullamento = motivo;
    }

    /** Pagato in parte e poi chiuso (es. recesso): resta dovuto solo quello che è stato pagato. */
    void riduciA(BigDecimal pagato, String motivo) {
        this.note = (note == null ? "" : note + " · ") + motivo + " (era " + importo + " €)";
        if (note.length() > 300) note = note.substring(0, 300);
        this.importo = pagato;
    }

    boolean attivo() { return annullatoIl == null; }
    boolean credito() { return importo.signum() < 0; }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getDescrizione() { return descrizione; }
    BigDecimal getImporto() { return importo; }
    LocalDate getScadenza() { return scadenza; }
    Pagamenti.Origine getOrigine() { return origine; }
    UUID getRiferimentoId() { return riferimentoId; }
    UUID getOffertaId() { return offertaId; }
    Integer getRata() { return rata; }
    Integer getRate() { return rate; }
    String getNote() { return note; }
    Instant getCreatoIl() { return creatoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
    String getMotivoAnnullamento() { return motivoAnnullamento; }
}
