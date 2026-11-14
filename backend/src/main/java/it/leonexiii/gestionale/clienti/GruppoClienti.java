package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Un gruppo di clienti (es. «Alunni dell'Istituto»): prezzi diversi (ADR 0018) e vantaggi nelle campagne (ADR 0026). */
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
    /** Posizione nell'ordine dei gruppi con precedenza (1 = la più forte); null = nessuna precedenza (ADR 0026). */
    private Integer precedenza;
    @Column(name = "giorni_per_pagare")
    private Integer giorniPerPagare;
    @Column(nullable = false)
    private boolean dichiarabile;
    @Column(name = "frase_dichiarazione")
    private String fraseDichiarazione;

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

    /** I vantaggi nelle campagne di iscrizione (ADR 0026); la precedenza si ordina a parte. */
    void vantaggi(Integer giorniPerPagare, boolean dichiarabile, String fraseDichiarazione) {
        if (giorniPerPagare != null && (giorniPerPagare < 1 || giorniPerPagare > 60))
            throw new IllegalArgumentException("I giorni per pagare vanno da 1 a 60 (vuoto = quelli di base).");
        String frase = fraseDichiarazione == null || fraseDichiarazione.isBlank() ? null : fraseDichiarazione.strip();
        if (dichiarabile && frase == null)
            throw new IllegalArgumentException("Scrivi la frase che le famiglie vedono (es. «Frequenta la scuola dell'Istituto»).");
        if (frase != null && frase.length() > 160) throw new IllegalArgumentException("La frase è troppo lunga (al massimo 160 caratteri).");
        this.giorniPerPagare = giorniPerPagare;
        this.dichiarabile = dichiarabile;
        this.fraseDichiarazione = dichiarabile ? frase : null;
    }

    void precedenza(Integer posizione) { this.precedenza = posizione; }

    UUID getId() { return id; }
    String getNome() { return nome; }
    String getDescrizione() { return descrizione; }
    boolean isAttivo() { return attivo; }
    Integer getPrecedenza() { return precedenza; }
    Integer getGiorniPerPagare() { return giorniPerPagare; }
    boolean isDichiarabile() { return dichiarabile; }
    String getFraseDichiarazione() { return fraseDichiarazione; }
}
