package it.leonexiii.gestionale.cassa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Un versamento in banca: i contanti messi in cassaforte alle chiusure, portati in banca un certo giorno. */
@Entity
@Table(name = "versamento_banca")
class VersamentoBanca {

    @Id
    private UUID id;
    @Column(nullable = false)
    private LocalDate data;
    @Column(nullable = false)
    private BigDecimal importo;
    private String distinta;
    private String note;
    @Column(name = "registrato_da")
    private UUID registratoDa;
    @Column(name = "registrato_il", nullable = false)
    private Instant registratoIl;
    @Column(name = "annullato_il")
    private Instant annullatoIl;
    @Column(name = "annullato_da")
    private UUID annullatoDa;

    protected VersamentoBanca() {}

    VersamentoBanca(LocalDate data, BigDecimal importo, String distinta, String note, UUID da, Instant ora) {
        this.id = UUID.randomUUID();
        this.data = data;
        this.importo = importo;
        this.distinta = distinta == null || distinta.isBlank() ? null : distinta.strip();
        this.note = note == null || note.isBlank() ? null : note.strip();
        this.registratoDa = da;
        this.registratoIl = ora;
        if (this.distinta != null && this.distinta.length() > 60) throw new IllegalArgumentException("Il numero della distinta è troppo lungo.");
        if (this.note != null && this.note.length() > 300) throw new IllegalArgumentException("La nota è troppo lunga (al massimo 300 caratteri).");
    }

    void annulla(Instant ora, UUID da) {
        if (annullatoIl != null) throw new IllegalArgumentException("Il versamento è già annullato.");
        this.annullatoIl = ora;
        this.annullatoDa = da;
    }

    UUID getId() { return id; }
    LocalDate getData() { return data; }
    BigDecimal getImporto() { return importo; }
    String getDistinta() { return distinta; }
    String getNote() { return note; }
    UUID getRegistratoDa() { return registratoDa; }
    Instant getRegistratoIl() { return registratoIl; }
    Instant getAnnullatoIl() { return annullatoIl; }
}
