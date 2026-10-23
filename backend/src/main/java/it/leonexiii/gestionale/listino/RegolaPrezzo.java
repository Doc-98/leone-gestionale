package it.leonexiii.gestionale.listino;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Una regola di prezzo di una stagione (riduzione tardiva, rate, recesso, sospensione), accesa o spenta. */
@Entity
@Table(name = "regola_prezzo")
class RegolaPrezzo {

    @Embeddable
    record Chiave(@Column(name = "stagione_id") UUID stagioneId, @Column(name = "tipo") String tipo) implements Serializable {}

    @EmbeddedId
    private Chiave chiave;
    @Column(nullable = false)
    private boolean attiva;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parametri = new HashMap<>();

    protected RegolaPrezzo() {}

    RegolaPrezzo(UUID stagioneId, String tipo) {
        this.chiave = new Chiave(stagioneId, tipo);
    }

    void modifica(boolean attiva, Map<String, Object> parametri) {
        this.attiva = attiva;
        this.parametri = parametri == null ? new HashMap<>() : new HashMap<>(parametri);
    }

    Chiave getChiave() { return chiave; }
    boolean isAttiva() { return attiva; }
    Map<String, Object> getParametri() { return parametri == null ? Map.of() : parametri; }
}
