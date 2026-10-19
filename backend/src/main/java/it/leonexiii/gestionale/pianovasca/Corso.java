package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Es. «Scuola nuoto ragazzi · delfini»: un corso dell'impianto, con le sue lezioni settimanali. */
@Entity
@Table(name = "corso")
class Corso {

    @Id
    private UUID id;

    @Column(name = "impianto_id", nullable = false)
    private UUID impiantoId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCorso tipo;

    private String livello;

    @Column(name = "eta_da")
    private Short etaDa;

    @Column(name = "eta_a")
    private Short etaA;

    @Column(name = "istruttore_id")
    private UUID istruttoreId;

    private String note;

    /** «#RRGGBB», facoltativo: il colore del livello (es. Delfino Giallo). */
    private String colore;

    @Column(nullable = false)
    private boolean attivo;

    protected Corso() {}

    Corso(UUID impiantoId) {
        this.id = UUID.randomUUID();
        this.impiantoId = impiantoId;
        this.attivo = true;
    }

    void modifica(String nome, TipoCorso tipo, String livello, Integer etaDa, Integer etaA, UUID istruttoreId, String note, boolean attivo, String colore) {
        if (colore != null && !colore.isBlank() && !colore.matches("^#[0-9A-Fa-f]{6}$")) throw new IllegalArgumentException("Colore non valido: usa il formato #RRGGBB.");
        this.colore = colore == null || colore.isBlank() ? null : colore.toUpperCase();
        if (tipo == null) throw new IllegalArgumentException("Scegli il tipo di corso.");
        if (etaDa != null && (etaDa < 0 || etaDa > 120) || etaA != null && (etaA < 0 || etaA > 120))
            throw new IllegalArgumentException("Età non valida.");
        if (etaDa != null && etaA != null && etaA < etaDa) throw new IllegalArgumentException("L'età massima è minore della minima.");
        this.nome = Testi.obbligatorio(nome, "il nome del corso", 120);
        this.tipo = tipo;
        this.livello = Testi.facoltativo(livello, "il livello", 60);
        this.etaDa = etaDa == null ? null : etaDa.shortValue();
        this.etaA = etaA == null ? null : etaA.shortValue();
        this.istruttoreId = istruttoreId;
        this.note = Testi.facoltativo(note, "le note", 500);
        this.attivo = attivo;
    }

    UUID getId() { return id; }
    UUID getImpiantoId() { return impiantoId; }
    String getNome() { return nome; }
    TipoCorso getTipo() { return tipo; }
    String getLivello() { return livello; }
    Integer getEtaDa() { return etaDa == null ? null : etaDa.intValue(); }
    Integer getEtaA() { return etaA == null ? null : etaA.intValue(); }
    UUID getIstruttoreId() { return istruttoreId; }
    String getNote() { return note; }
    boolean isAttivo() { return attivo; }
    String getColore() { return colore; }
}
