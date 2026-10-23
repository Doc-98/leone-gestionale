package it.leonexiii.gestionale.societa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Una stagione sportiva (es. «2026/27», dal 1/9 al 31/8) con la sua quota associativa. */
@Entity
@Table(name = "stagione")
class StagioneSportiva {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private LocalDate dal;
    @Column(nullable = false)
    private LocalDate al;
    private BigDecimal quota;
    @Column(name = "quota_rimborsabile", nullable = false)
    private boolean quotaRimborsabile;

    protected StagioneSportiva() {}

    StagioneSportiva(Societa.NuovaStagione in) {
        this.id = UUID.randomUUID();
        modifica(in);
    }

    void modifica(Societa.NuovaStagione in) {
        if (in.nome() == null || in.nome().isBlank() || in.nome().strip().length() > 20)
            throw new IllegalArgumentException("Dai un nome alla stagione (es. 2026/27), al massimo 20 caratteri.");
        if (in.dal() == null || in.al() == null || !in.al().isAfter(in.dal())) throw new IllegalArgumentException("Controlla le date: la fine viene dopo l'inizio.");
        if (in.quota() != null && in.quota().signum() < 0) throw new IllegalArgumentException("La quota non può essere negativa.");
        this.nome = in.nome().strip();
        this.dal = in.dal();
        this.al = in.al();
        this.quota = in.quota();
        this.quotaRimborsabile = Boolean.TRUE.equals(in.quotaRimborsabile());
    }

    Societa.Stagione dto() {
        return new Societa.Stagione(id, nome, dal, al, quota, quotaRimborsabile);
    }

    UUID getId() { return id; }
    LocalDate getDal() { return dal; }
}
