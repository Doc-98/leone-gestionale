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
    @Column(name = "metodo_id", nullable = false)
    private UUID metodoId;
    @Column(nullable = false)
    private String descrizione;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;

    protected Uscita() {}

    Uscita(UUID personaId, LocalDate data, BigDecimal importo, UUID metodoId, String descrizione, UUID registratoDa, Instant ora) {
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.data = data;
        this.importo = importo;
        this.metodoId = metodoId;
        this.descrizione = descrizione;
        this.registratoDa = registratoDa;
        this.registratoIl = ora;
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    LocalDate getData() { return data; }
    BigDecimal getImporto() { return importo; }
    UUID getMetodoId() { return metodoId; }
    String getDescrizione() { return descrizione; }
    UUID getRegistratoDa() { return registratoDa; }
    Instant getRegistratoIl() { return registratoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
}
