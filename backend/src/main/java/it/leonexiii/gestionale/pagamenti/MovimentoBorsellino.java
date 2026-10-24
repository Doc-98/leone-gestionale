package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Un movimento del borsellino: euro o crediti, in entrata (positivo) o in uscita (negativo). */
@Entity
@Table(name = "movimento_borsellino")
class MovimentoBorsellino {

    enum Saldo { EURO, CREDITI }

    enum Causale { RICARICA, CONSUMO, ACCREDITO, PAGAMENTO, TRASFERIMENTO, RIMBORSO, SCADENZA, SMARRIMENTO, CORREZIONE, ANNULLAMENTO }

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String saldo;
    @Column(nullable = false)
    private BigDecimal quantita;
    @Column(nullable = false)
    private String causale;
    @Column(nullable = false)
    private String descrizione;
    private LocalDate scadenza;
    @Column(name = "pagamento_id")
    private UUID pagamentoId;
    @Column(name = "uscita_id")
    private UUID uscitaId;
    @Column(name = "controparte_id")
    private UUID controparteId;
    @Column(name = "riferimento_id")
    private UUID riferimentoId;
    @Column(name = "creato_da")
    private UUID creatoDa;
    @Column(name = "creato_il", nullable = false)
    private Instant creatoIl;

    protected MovimentoBorsellino() {}

    MovimentoBorsellino(UUID personaId, Saldo saldo, BigDecimal quantita, Causale causale, String descrizione, LocalDate scadenza, UUID creatoDa, Instant ora) {
        if (quantita.signum() == 0) throw new IllegalArgumentException("Un movimento non può essere di zero.");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.saldo = saldo.name();
        this.quantita = quantita;
        this.causale = causale.name();
        this.descrizione = descrizione.length() > 200 ? descrizione.substring(0, 200) : descrizione;
        this.scadenza = quantita.signum() > 0 && saldo == Saldo.EURO ? scadenza : null;
        this.creatoDa = creatoDa;
        this.creatoIl = ora;
    }

    MovimentoBorsellino conPagamento(UUID id) { this.pagamentoId = id; return this; }
    MovimentoBorsellino conUscita(UUID id) { this.uscitaId = id; return this; }
    MovimentoBorsellino conControparte(UUID id) { this.controparteId = id; return this; }
    MovimentoBorsellino conRiferimento(UUID id) { this.riferimentoId = id; return this; }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    Saldo saldo() { return Saldo.valueOf(saldo); }
    BigDecimal getQuantita() { return quantita; }
    Causale causale() { return Causale.valueOf(causale); }
    String getDescrizione() { return descrizione; }
    LocalDate getScadenza() { return scadenza; }
    UUID getPagamentoId() { return pagamentoId; }
    UUID getControparteId() { return controparteId; }
    UUID getRiferimentoId() { return riferimentoId; }
    UUID getCreatoDa() { return creatoDa; }
    Instant getCreatoIl() { return creatoIl; }
}
