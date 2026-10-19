package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un ruolo: i quattro di sistema (ADMIN, COORDINATORE, SEGRETERIA, STAFF) e quelli creati dagli admin. */
@Entity
@Table(name = "ruolo")
class RuoloDefinito {

    static final String ADMIN = "ADMIN";

    @Id
    private String codice;
    @Column(nullable = false)
    private String nome;
    private String descrizione;
    @Column(name = "di_sistema", nullable = false)
    private boolean diSistema;

    protected RuoloDefinito() {}

    RuoloDefinito(String codice, String nome, String descrizione) {
        this.codice = codice;
        modifica(nome, descrizione);
    }

    void modifica(String nome, String descrizione) {
        String n = nome == null ? "" : nome.strip();
        if (n.isEmpty() || n.length() > 80) throw new IllegalArgumentException("Il nome del ruolo va da 1 a 80 caratteri.");
        String d = descrizione == null || descrizione.isBlank() ? null : descrizione.strip();
        if (d != null && d.length() > 300) throw new IllegalArgumentException("Descrizione troppo lunga (massimo 300 caratteri).");
        this.nome = n;
        this.descrizione = d;
    }

    String getCodice() { return codice; }
    String getNome() { return nome; }
    String getDescrizione() { return descrizione; }
    boolean isDiSistema() { return diSistema; }
}
