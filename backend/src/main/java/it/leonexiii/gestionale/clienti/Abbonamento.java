package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Abbonamento a un corso (mono, bi, trisettimanale) oppure carnet di ingressi. */
@Entity
@Table(name = "abbonamento")
class Abbonamento {

    static final String CORSO = "CORSO";
    static final String CARNET = "CARNET";

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "corso_id")
    private UUID corsoId;
    @Column(name = "volte_a_settimana")
    private Short volteASettimana;
    private Short ingressi;
    @Column(nullable = false)
    private LocalDate dal;
    private LocalDate al;
    private BigDecimal importo;
    private String note;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;
    @Column(name = "offerta_id")
    private UUID offertaId;
    @Column(name = "importo_listino")
    private BigDecimal importoListino;
    @Column(name = "motivo_importo")
    private String motivoImporto;

    protected Abbonamento() {}

    private Abbonamento(UUID personaId, String tipo, LocalDate dal, LocalDate al, BigDecimal importo, String note, UUID registratoDa, Instant ora) {
        if (dal == null) throw new IllegalArgumentException("Indica da quando vale l'abbonamento.");
        if (al != null && al.isBefore(dal)) throw new IllegalArgumentException("L'abbonamento finisce prima di iniziare.");
        if (importo != null && importo.signum() < 0) throw new IllegalArgumentException("L'importo non può essere negativo.");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.tipo = tipo;
        this.dal = dal;
        this.al = al;
        this.importo = importo;
        this.note = Testo.breve(note, 300);
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    static Abbonamento corso(UUID personaId, UUID corsoId, Integer volte, LocalDate dal, LocalDate al, BigDecimal importo, String note, UUID da, Instant ora) {
        if (corsoId == null) throw new IllegalArgumentException("Scegli il corso.");
        if (volte == null || volte < 1 || volte > 7) throw new IllegalArgumentException("Indica quante volte a settimana (1, 2, 3…).");
        Abbonamento a = new Abbonamento(personaId, CORSO, dal, al, importo, note, da, ora);
        a.corsoId = corsoId;
        a.volteASettimana = volte.shortValue();
        return a;
    }

    /** Carnet a entrata libera (corsoId null) o valido solo negli orari dei turni di un corso (es. nuoto libero). */
    static Abbonamento carnet(UUID personaId, Integer ingressi, UUID corsoId, LocalDate dal, LocalDate al, BigDecimal importo, String note, UUID da, Instant ora) {
        if (ingressi == null || ingressi < 1 || ingressi > 500) throw new IllegalArgumentException("Indica quanti ingressi ha il carnet.");
        Abbonamento a = new Abbonamento(personaId, CARNET, dal, al, importo, note, da, ora);
        a.ingressi = ingressi.shortValue();
        a.corsoId = corsoId;
        return a;
    }

    /** Venduto dal listino: l'offerta, l'importo proposto e, se l'importo è diverso, il motivo. */
    void daListino(UUID offertaId, BigDecimal proposto, String motivo) {
        this.offertaId = offertaId;
        this.importoListino = proposto;
        this.motivoImporto = Testo.breve(motivo, 300);
    }

    void annulla(Instant ora) {
        if (annullatoIl == null) annullatoIl = ora;
    }

    void termina(LocalDate ultimoGiorno) {
        if (ultimoGiorno == null || ultimoGiorno.isBefore(dal)) throw new IllegalArgumentException("L'ultimo giorno è prima dell'inizio dell'abbonamento.");
        this.al = ultimoGiorno;
    }

    boolean validoIl(LocalDate g) {
        return annullatoIl == null && !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    boolean isCarnet() { return CARNET.equals(tipo); }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getTipo() { return tipo; }
    UUID getCorsoId() { return corsoId; }
    Integer getVolteASettimana() { return volteASettimana == null ? null : volteASettimana.intValue(); }
    Integer getIngressi() { return ingressi == null ? null : ingressi.intValue(); }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    BigDecimal getImporto() { return importo; }
    UUID getOffertaId() { return offertaId; }
    BigDecimal getImportoListino() { return importoListino; }
    String getMotivoImporto() { return motivoImporto; }
    String getNote() { return note; }
    Instant getAnnullatoIl() { return annullatoIl; }
}
