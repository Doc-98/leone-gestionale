package it.leonexiii.gestionale.accessi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Un controllo del tornello: attivo o no, con i suoi parametri (es. minuti prima dell'inizio del corso). */
@Entity
@Table(name = "regola_accesso")
class RegolaAccesso {

    @Id
    private String codice;
    @Column(nullable = false)
    private boolean attiva;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parametri = new HashMap<>();
    @Column(name = "modificata_da")
    private UUID modificataDa;
    @Column(name = "modificata_il")
    private Instant modificataIl;

    protected RegolaAccesso() {}

    RegolaAccesso(String codice) {
        this.codice = codice;
        this.attiva = true;
    }

    void modifica(boolean attiva, Map<String, Object> parametri, UUID da, Instant ora) {
        this.attiva = attiva;
        this.parametri = new HashMap<>(parametri);
        this.modificataDa = da;
        this.modificataIl = ora;
    }

    String getCodice() { return codice; }
    boolean isAttiva() { return attiva; }
    Map<String, Object> getParametri() { return parametri == null ? Map.of() : parametri; }
    Instant getModificataIl() { return modificataIl; }
}
