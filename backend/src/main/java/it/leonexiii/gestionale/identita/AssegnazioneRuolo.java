package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Un ruolo di un account, limitato a un impianto oppure valido per tutta la società (impianto null). */
@Entity
@Table(name = "assegnazione_ruolo")
class AssegnazioneRuolo {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String ruolo;

    @Column(name = "impianto_id")
    private UUID impiantoId;

    @Column(nullable = false)
    private LocalDate dal;

    private LocalDate al;

    protected AssegnazioneRuolo() {}

    AssegnazioneRuolo(UUID accountId, String ruolo, UUID impiantoId, LocalDate dal) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.ruolo = ruolo;
        this.impiantoId = impiantoId;
        this.dal = dal;
    }

    /** Toglie il ruolo da oggi; se non era ancora iniziato, la riga non serve più (lo dice il servizio). */
    void chiudi(LocalDate oggi) {
        this.al = oggi.minusDays(1);
    }

    boolean validoIl(LocalDate g) {
        return !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    UUID getId() { return id; }
    UUID getAccountId() { return accountId; }
    String getRuolo() { return ruolo; }
    UUID getImpiantoId() { return impiantoId; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
}
