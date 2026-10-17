package it.leonexiii.gestionale.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Es. «Assistente spogliatoio», «Assistente atrio», «Istruttore». */
@Entity
@Table(name = "mansione")
class Mansione {

    @Id
    private UUID id;

    @Column(name = "impianto_id", nullable = false)
    private UUID impiantoId;

    @Column(nullable = false)
    private String codice;

    @Column(nullable = false)
    private String nome;

    protected Mansione() {}

    UUID getId() { return id; }
    UUID getImpiantoId() { return impiantoId; }
    String getCodice() { return codice; }
    String getNome() { return nome; }
}
