package it.leonexiii.gestionale.ricevute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Una serie numerata: ricevute, fatture, o altre che la società crea. */
@Entity
@Table(name = "serie_ricevute")
class Serie {

    enum Tipo { RICEVUTA, FATTURA }

    /** Quando il numero riparte da 1. */
    enum Riparte { STAGIONE, ANNO, MAI }

    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;
    private boolean attiva;
    @Column(nullable = false)
    private String formato;
    private short cifre;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Riparte riparte;
    @Column(name = "prossimo_numero")
    private Integer prossimoNumero;
    private short ordine;

    protected Serie() {}

    Serie(Tipo tipo, short ordine) {
        this.id = UUID.randomUUID();
        this.tipo = tipo;
        this.ordine = ordine;
    }

    void modifica(String nome, boolean attiva, String formato, int cifre, Riparte riparte) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 60) throw new IllegalArgumentException("Scrivi il nome della serie (al massimo 60 caratteri).");
        if (formato == null || !formato.contains("{numero}")) throw new IllegalArgumentException("Il formato deve contenere {numero}.");
        if (formato.strip().length() > 40) throw new IllegalArgumentException("Il formato è troppo lungo (al massimo 40 caratteri).");
        if (cifre < 1 || cifre > 8) throw new IllegalArgumentException("Le cifre del numero vanno da 1 a 8.");
        if (riparte == null) throw new IllegalArgumentException("Scegli quando riparte il numero.");
        this.nome = nome.strip();
        this.attiva = attiva;
        this.formato = formato.strip();
        this.cifre = (short) cifre;
        this.riparte = riparte;
    }

    void prossimoNumero(Integer n) {
        if (n != null && n < 1) throw new IllegalArgumentException("Il prossimo numero parte da 1.");
        this.prossimoNumero = n;
    }

    UUID getId() { return id; }
    String getNome() { return nome; }
    Tipo getTipo() { return tipo; }
    boolean isAttiva() { return attiva; }
    String getFormato() { return formato; }
    int getCifre() { return cifre; }
    Riparte getRiparte() { return riparte; }
    Integer getProssimoNumero() { return prossimoNumero; }
    int getOrdine() { return ordine; }
}
