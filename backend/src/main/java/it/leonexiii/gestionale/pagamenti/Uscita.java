package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Soldi restituiti a una persona (cauzione, credito in denaro): escono dalla cassa. */
@Entity
@Table(name = "uscita")
class Uscita {

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private LocalDate data;
    @Column(nullable = false)
    private BigDecimal importo;
    /** Il metodo, finché esiste: se viene eliminato si svuota e resta la copia. */
    @Column(name = "metodo_id")
    private UUID metodoId;
    /** Il metodo com'era quando si sono restituiti i soldi: lo storico legge questa. */
    @jakarta.persistence.Embedded
    private CopiaMetodo copia;
    @Column(nullable = false)
    private String descrizione;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;
    /** La cassa da cui sono usciti i soldi (vuoto = la principale). */
    @Column(name = "cassa_id")
    private UUID cassaId;

    protected Uscita() {}

    Uscita(UUID personaId, LocalDate data, BigDecimal importo, MetodoPagamento metodo, String descrizione, UUID registratoDa, Instant ora, UUID cassaId) {
        this.cassaId = cassaId;
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.data = data;
        this.importo = importo;
        this.metodoId = metodo.getId();
        this.copia = new CopiaMetodo(metodo);
        this.descrizione = descrizione;
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    LocalDate getData() { return data; }
    BigDecimal getImporto() { return importo; }
    UUID getMetodoId() { return metodoId; }
    CopiaMetodo getCopia() { return copia; }
    String getDescrizione() { return descrizione; }
    UUID getRegistratoDa() { return registratoDa; }
    Instant getRegistratoIl() { return registratoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
    UUID getCassaId() { return cassaId; }
}
