package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ruolo ruolo;

    @Column(name = "impianto_id")
    private UUID impiantoId;

    @Column(nullable = false)
    private LocalDate dal;

    private LocalDate al;

    protected AssegnazioneRuolo() {}

    Ruolo getRuolo() { return ruolo; }
    UUID getImpiantoId() { return impiantoId; }
}
