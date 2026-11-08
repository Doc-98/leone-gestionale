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
    /** Il borsellino della persona che paga (il suo credito in euro): non si rinomina né si spegne come gli altri. */
    @Column(nullable = false)
    private boolean borsellino;
    /** Finisce nel cassetto (di solito solo i contanti): conta nei contanti attesi alla chiusura di cassa. */
    @Column(nullable = false)
    private boolean contanti;

    protected MetodoPagamento() {}

    MetodoPagamento(String nome, boolean daIncassare, boolean contanti, int ordine) {
        this.id = UUID.randomUUID();
        this.attivo = true;
        modifica(nome, daIncassare, contanti, true, ordine);
    }

    void modifica(String nome, boolean daIncassare, boolean contanti, boolean attivo, int ordine) {
        if (borsellino && (daIncassare || contanti || (this.nome != null && nome != null && !this.nome.equals(nome.strip()))))
            throw new IllegalArgumentException("Il borsellino si può solo spegnere o spostare.");
        if (daIncassare && contanti) throw new IllegalArgumentException("Un metodo da incassare a mano non finisce nel cassetto.");
        if (nome == null || nome.isBlank() || nome.strip().length() > 60) throw new IllegalArgumentException("Dai un nome al metodo (al massimo 60 caratteri).");
        this.nome = nome.strip();
        this.daIncassare = daIncassare;
        this.contanti = contanti;
        this.attivo = attivo;
        this.ordine = ordine;
    }

    Pagamenti.Metodo dto() {
        return new Pagamenti.Metodo(id, nome, daIncassare, contanti, attivo, ordine, borsellino);
    }

    UUID getId() { return id; }
    String getNome() { return nome; }
    boolean isDaIncassare() { return daIncassare; }
    boolean isAttivo() { return attivo; }
    boolean isBorsellino() { return borsellino; }
    boolean isContanti() { return contanti; }
    int getOrdine() { return ordine; }
}
