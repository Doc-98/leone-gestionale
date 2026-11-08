package it.leonexiii.gestionale.cassa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un'apertura e chiusura di una cassa. Conta quello che è passato dalla cassa tra «dal» (la chiusura prima, o l'inizio
 * del giorno) e la chiusura. «Esplicita»: qualcuno l'ha aperta (chiusura per turno, o fondo da contare); se no si è
 * aperta da sola alla prima operazione o alla chiusura.
 */
@Entity
@Table(name = "sessione_cassa")
class Sessione {

    /** I totali per metodo alla chiusura: entrate e uscite, e se il metodo finisce nel cassetto. */
    public record TotaleMetodo(String metodo, boolean contanti, BigDecimal entrate, BigDecimal uscite) {}

    /** La fotografia dei conti alla chiusura (se poi si annulla un pagamento, la chiusura fatta non cambia). */
    public record Riepilogo(List<TotaleMetodo> perMetodo, int pagamenti, BigDecimal entrati, BigDecimal usciti, BigDecimal versamenti,
                            BigDecimal spese, BigDecimal aggiunte) {}

    @Id
    private UUID id;
    @Column(name = "cassa_id", nullable = false)
    private UUID cassaId;
    @Column(nullable = false)
    private Instant dal;
    @Column(name = "aperta_il", nullable = false)
    private Instant apertaIl;
    @Column(name = "aperta_da")
    private UUID apertaDa;
    private boolean esplicita;
    @Column(nullable = false)
    private BigDecimal fondo;
    @Column(name = "fondo_atteso")
    private BigDecimal fondoAtteso;
    @Column(name = "chiusa_il")
    private Instant chiusaIl;
    @Column(name = "chiusa_da")
    private UUID chiusaDa;
    private BigDecimal attesi;
    private BigDecimal contati;
    private BigDecimal differenza;
    private BigDecimal lasciato;
    private BigDecimal versato;
    private String note;
    @JdbcTypeCode(SqlTypes.JSON)
    private Riepilogo riepilogo;

    protected Sessione() {}

    Sessione(UUID cassaId, Instant dal, Instant ora, UUID da, boolean esplicita, BigDecimal fondo, BigDecimal fondoAtteso) {
        this.id = UUID.randomUUID();
        this.cassaId = cassaId;
        this.dal = dal;
        this.apertaIl = ora;
        this.apertaDa = da;
        this.esplicita = esplicita;
        this.fondo = fondo;
        this.fondoAtteso = fondoAtteso;
    }

    void chiudi(Instant ora, UUID da, BigDecimal attesi, BigDecimal contati, BigDecimal lasciato, String note, Riepilogo riepilogo) {
        if (chiusaIl != null) throw new IllegalStateException("La cassa è già chiusa.");
        this.chiusaIl = ora;
        this.chiusaDa = da;
        this.attesi = attesi;
        this.contati = contati;
        this.differenza = contati.subtract(attesi);
        this.lasciato = lasciato;
        this.versato = contati.subtract(lasciato);
        this.riepilogo = riepilogo;
        aggiungiNota(note);
    }

    /** Si riapre l'ultima chiusura (es. un contato sbagliato): resta la nota con chi, quando e perché. */
    void riapri(String chi, String quando, String motivo) {
        this.chiusaIl = null;
        this.chiusaDa = null;
        this.attesi = null;
        this.contati = null;
        this.differenza = null;
        this.lasciato = null;
        this.versato = null;
        this.riepilogo = null;
        aggiungiNota("Riaperta " + quando + (chi == null ? "" : " da " + chi) + ": " + motivo);
    }

    private void aggiungiNota(String testo) {
        if (testo == null || testo.isBlank()) return;
        String n = note == null ? testo.strip() : note + "\n" + testo.strip();
        if (n.length() > 1000) throw new IllegalArgumentException("Le note sono troppo lunghe (al massimo 1000 caratteri).");
        this.note = n;
    }

    boolean aperta() { return chiusaIl == null; }

    UUID getId() { return id; }
    UUID getCassaId() { return cassaId; }
    Instant getDal() { return dal; }
    Instant getApertaIl() { return apertaIl; }
    UUID getApertaDa() { return apertaDa; }
    boolean isEsplicita() { return esplicita; }
    BigDecimal getFondo() { return fondo; }
    BigDecimal getFondoAtteso() { return fondoAtteso; }
    Instant getChiusaIl() { return chiusaIl; }
    UUID getChiusaDa() { return chiusaDa; }
    BigDecimal getAttesi() { return attesi; }
    BigDecimal getContati() { return contati; }
    BigDecimal getDifferenza() { return differenza; }
    BigDecimal getLasciato() { return lasciato; }
    BigDecimal getVersato() { return versato; }
    String getNote() { return note; }
    Riepilogo getRiepilogo() { return riepilogo; }
}
