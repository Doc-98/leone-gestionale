package it.leonexiii.gestionale.struttura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Vasca, corsia, spogliatoio, atrio… di un impianto. */
@Entity
@Table(name = "spazio")
class Spazio {

    @Id
    private UUID id;

    @Column(name = "impianto_id", nullable = false)
    private UUID impiantoId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private int ordine;

    /** La vasca che contiene questa corsia (null per gli spazi di primo livello). */
    @Column(name = "padre_id")
    private UUID padreId;

    /** Per le corsie: in quante corsie è divisa la vasca in questo assetto (es. 5 o 6). */
    private Short assetto;

    protected Spazio() {}

    UUID getId() { return id; }
    UUID getImpiantoId() { return impiantoId; }
    String getNome() { return nome; }
    String getTipo() { return tipo; }
    UUID getPadreId() { return padreId; }
    Integer getAssetto() { return assetto == null ? null : assetto.intValue(); }
}
