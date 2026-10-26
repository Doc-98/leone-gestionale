package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /** Il primo istruttore del parco: quello che vale per i turni che non ne hanno uno proprio. */
    @Column(name = "istruttore_id")
    private UUID istruttoreId;

    /** Il parco istruttori: chi lavora per questo corso; ogni turno ne ha assegnati uno o più. */
    @ElementCollection
    @CollectionTable(name = "corso_istruttore", joinColumns = @JoinColumn(name = "corso_id"))
    @Column(name = "persona_id")
    private Set<UUID> istruttori = new LinkedHashSet<>();

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

    void modifica(String nome, TipoCorso tipo, String livello, Integer etaDa, Integer etaA, List<UUID> istruttori, String note, boolean attivo, String colore) {
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
        List<UUID> parco = istruttori == null ? List.of() : istruttori.stream().filter(java.util.Objects::nonNull).distinct().toList();
        this.istruttoreId = parco.isEmpty() ? null : parco.get(0);
        this.istruttori.clear();
        this.istruttori.addAll(parco);
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
    Set<UUID> getIstruttori() { return istruttori; }

    /** Aggiunge al parco chi viene assegnato a un turno senza esserci ancora. */
    void aggiungiAlParco(Set<UUID> persone) {
        istruttori.addAll(persone);
        if (istruttoreId == null && !istruttori.isEmpty()) istruttoreId = istruttori.iterator().next();
    }
    String getNote() { return note; }
    boolean isAttivo() { return attivo; }
    String getColore() { return colore; }
}
