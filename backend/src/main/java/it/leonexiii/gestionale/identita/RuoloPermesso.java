package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Un permesso dato a un ruolo. */
@Entity
@Table(name = "ruolo_permesso")
class RuoloPermesso {

    @Embeddable
    record Chiave(@Column(name = "ruolo") String ruolo, @Column(name = "permesso") String permesso) implements Serializable {}

    @EmbeddedId
    private Chiave chiave;

    protected RuoloPermesso() {}

    RuoloPermesso(String ruolo, Permesso permesso) {
        this.chiave = new Chiave(ruolo, permesso.name());
    }

    Chiave getChiave() { return chiave; }
}
