package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Un gruppo di clienti (es. «Alunni dell'Istituto»): per prezzi diversi e precedenza nei rinnovi (ADR 0018). */
@Entity
@Table(name = "gruppo_clienti")
class GruppoClienti {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    private String descrizione;
    @Column(nullable = false)
    private boolean attivo;

    protected GruppoClienti() {}

    GruppoClienti(String nome, String descrizione) {
        this.id = UUID.randomUUID();
        this.attivo = true;
        modifica(nome, descrizione, true);
    }

    void modifica(String nome, String descrizione, boolean attivo) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 80) throw new IllegalArgumentException("Dai un nome al gruppo (al massimo 80 caratteri).");
        if (descrizione != null && descrizione.length() > 300) throw new IllegalArgumentException("La descrizione è troppo lunga (al massimo 300 caratteri).");
        this.nome = nome.strip();
        this.descrizione = descrizione == null || descrizione.isBlank() ? null : descrizione.strip();
        this.attivo = attivo;
    }

    UUID getId() { return id; }
    String getNome() { return nome; }
    String getDescrizione() { return descrizione; }
    boolean isAttivo() { return attivo; }
}
