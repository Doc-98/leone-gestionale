package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Un periodo in cui l'abbonamento non vale (es. infortunio); la scadenza si allunga di altrettanti giorni. */
@Entity
@Table(name = "sospensione")
class Sospensione {

    @Id
    private UUID id;
    @Column(name = "abbonamento_id", nullable = false)
    private UUID abbonamentoId;
    @Column(nullable = false)
    private LocalDate dal;
    @Column(nullable = false)
    private LocalDate al;
    private String motivo;
    @Column(name = "creata_da")
    private UUID creataDa;
    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;
    @Column(name = "annullata_il")
    private Instant annullataIl;

    protected Sospensione() {}

    Sospensione(UUID abbonamentoId, LocalDate dal, LocalDate al, String motivo, UUID creataDa, Instant ora) {
        if (dal == null || al == null) throw new IllegalArgumentException("Indica da quando a quando è sospeso.");
        if (al.isBefore(dal)) throw new IllegalArgumentException("La sospensione finisce prima di iniziare.");
        this.id = UUID.randomUUID();
        this.abbonamentoId = abbonamentoId;
        this.dal = dal;
        this.al = al;
        this.motivo = Testo.breve(motivo, 300);
        this.creataDa = creataDa;
        this.creataIl = ora;
    }

    void annulla(Instant ora) {
        if (annullataIl == null) annullataIl = ora;
    }

    long giorni() {
        return ChronoUnit.DAYS.between(dal, al) + 1;
    }

    boolean siAccavallaCon(LocalDate d, LocalDate a) {
        return !d.isAfter(al) && !a.isBefore(dal);
    }

    UUID getId() { return id; }
    UUID getAbbonamentoId() { return abbonamentoId; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    String getMotivo() { return motivo; }
    Instant getAnnullataIl() { return annullataIl; }
}
