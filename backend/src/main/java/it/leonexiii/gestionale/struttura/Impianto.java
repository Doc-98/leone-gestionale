package it.leonexiii.gestionale.struttura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Es. «Piscina»: un impianto di una sede, dedicato a una disciplina. */
@Entity
@Table(name = "impianto")
class Impianto {

    @Id
    private UUID id;

    @Column(name = "sede_id", nullable = false)
    private UUID sedeId;

    @Column(nullable = false)
    private String nome;

    protected Impianto() {}

    UUID getId() { return id; }
    String getNome() { return nome; }
}
