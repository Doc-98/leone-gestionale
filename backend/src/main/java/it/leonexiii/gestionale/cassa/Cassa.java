package it.leonexiii.gestionale.cassa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Una cassa (un cassetto): «Segreteria», o una per postazione. La principale prende i pagamenti senza cassa. */
@Entity
@Table(name = "cassa")
class Cassa {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    private boolean attiva;
    private boolean principale;
    private int ordine;

    protected Cassa() {}

    Cassa(String nome, int ordine) {
        this.id = UUID.randomUUID();
        this.attiva = true;
        this.ordine = ordine;
        rinomina(nome);
    }

    void rinomina(String nome) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 40) throw new IllegalArgumentException("Dai un nome alla cassa (al massimo 40 caratteri).");
        this.nome = nome.strip();
    }

    void accendi(boolean attiva) {
        if (!attiva && principale) throw new IllegalArgumentException("La cassa principale non si spegne: prima scegline un'altra come principale.");
        this.attiva = attiva;
    }

    void principale(boolean p) {
        if (p && !attiva) throw new IllegalArgumentException("Una cassa spenta non può essere la principale.");
        this.principale = p;
    }

    void ordine(int ordine) { this.ordine = ordine; }

    CassaService.CassaDto dto() {
        return new CassaService.CassaDto(id, nome, attiva, principale, ordine);
    }

    UUID getId() { return id; }
    String getNome() { return nome; }
    boolean isAttiva() { return attiva; }
    boolean isPrincipale() { return principale; }
    int getOrdine() { return ordine; }
}
