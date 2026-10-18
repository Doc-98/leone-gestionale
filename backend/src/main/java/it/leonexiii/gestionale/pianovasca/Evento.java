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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Gara, lezione privata o di prova, chiusura…: occupa degli spazi (nessuno = tutto l'impianto) per un intervallo. */
@Entity
@Table(name = "evento")
class Evento {

    @Id
    private UUID id;

    @Column(name = "impianto_id", nullable = false)
    private UUID impiantoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipo;

    @Column(nullable = false)
    private String titolo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "data_fine")
    private LocalDate dataFine;

    @Column(nullable = false)
    private LocalTime inizio;

    @Column(nullable = false)
    private LocalTime fine;

    @Column(name = "istruttore_id")
    private UUID istruttoreId;

    private String note;

    @Column(nullable = false)
    private boolean annullato;

    @ElementCollection
    @CollectionTable(name = "evento_spazio", joinColumns = @JoinColumn(name = "evento_id"))
    @Column(name = "spazio_id")
    private Set<UUID> spazi = new HashSet<>();

    protected Evento() {}

    Evento(UUID impiantoId) {
        this.id = UUID.randomUUID();
        this.impiantoId = impiantoId;
    }

    void modifica(TipoEvento tipo, String titolo, LocalDate data, LocalDate dataFine, LocalTime inizio, LocalTime fine,
                  Set<UUID> spazi, UUID istruttoreId, String note) {
        if (tipo == null) throw new IllegalArgumentException("Scegli il tipo di evento.");
        if (data == null) throw new IllegalArgumentException("Indica il giorno.");
        if (dataFine != null && dataFine.isBefore(data)) throw new IllegalArgumentException("L'ultimo giorno è prima del primo.");
        if (inizio == null || fine == null || !fine.isAfter(inizio)) throw new IllegalArgumentException("La fine deve essere dopo l'inizio.");
        if (dataFine != null && java.time.temporal.ChronoUnit.DAYS.between(data, dataFine) > 62)
            throw new IllegalArgumentException("Un evento dura al massimo due mesi.");
        this.tipo = tipo;
        this.titolo = Testi.obbligatorio(titolo, "il titolo", 120);
        this.data = data;
        this.dataFine = dataFine == null || dataFine.equals(data) ? null : dataFine;
        this.inizio = inizio;
        this.fine = fine;
        this.spazi.clear();
        if (spazi != null) this.spazi.addAll(spazi);
        this.istruttoreId = istruttoreId;
        this.note = Testi.facoltativo(note, "le note", 500);
    }

    void annulla() {
        this.annullato = true;
    }

    /** L'evento occupa quel giorno? */
    boolean occupa(LocalDate giorno) {
        return !giorno.isBefore(data) && !giorno.isAfter(ultimoGiorno());
    }

    LocalDate ultimoGiorno() {
        return dataFine == null ? data : dataFine;
    }

    UUID getId() { return id; }
    UUID getImpiantoId() { return impiantoId; }
    TipoEvento getTipo() { return tipo; }
    String getTitolo() { return titolo; }
    LocalDate getData() { return data; }
    LocalDate getDataFine() { return dataFine; }
    LocalTime getInizio() { return inizio; }
    LocalTime getFine() { return fine; }
    UUID getIstruttoreId() { return istruttoreId; }
    String getNote() { return note; }
    boolean isAnnullato() { return annullato; }
    Set<UUID> getSpazi() { return spazi; }
}
