package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Contanti, bancomat, carta, Satispay, bonifico…: l'elenco lo decide la società. «Da incassare» = si segna incassato a mano. */
@Entity
@Table(name = "metodo_pagamento")
class MetodoPagamento {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Column(name = "da_incassare", nullable = false)
    private boolean daIncassare;
    @Column(nullable = false)
    private boolean attivo;
    @Column(nullable = false)
    private int ordine;

    protected MetodoPagamento() {}

    MetodoPagamento(String nome, boolean daIncassare, int ordine) {
        this.id = UUID.randomUUID();
        this.attivo = true;
        modifica(nome, daIncassare, true, ordine);
    }

    void modifica(String nome, boolean daIncassare, boolean attivo, int ordine) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 60) throw new IllegalArgumentException("Dai un nome al metodo (al massimo 60 caratteri).");
        this.nome = nome.strip();
        this.daIncassare = daIncassare;
        this.attivo = attivo;
        this.ordine = ordine;
    }

    Pagamenti.Metodo dto() {
        return new Pagamenti.Metodo(id, nome, daIncassare, attivo, ordine);
    }

    UUID getId() { return id; }
    String getNome() { return nome; }
    boolean isDaIncassare() { return daIncassare; }
    boolean isAttivo() { return attivo; }
    int getOrdine() { return ordine; }
}
