package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** In una data la lezione non si fa (SALTATA) o si fa in un altro giorno o orario (SPOSTATA). */
@Entity
@Table(name = "lezione_eccezione")
class Eccezione {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lezione_id", nullable = false)
    private Lezione lezione;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEccezione tipo;

    @Column(name = "nuova_data")
    private LocalDate nuovaData;

    @Column(name = "nuovo_inizio")
    private LocalTime nuovoInizio;

    @Column(name = "nuovo_fine")
    private LocalTime nuovoFine;

    private String motivo;

    protected Eccezione() {}

    Eccezione(Lezione lezione, LocalDate data, TipoEccezione tipo, LocalDate nuovaData, LocalTime nuovoInizio, LocalTime nuovoFine, String motivo) {
        if (tipo == null) throw new IllegalArgumentException("Scegli se la lezione è saltata o spostata.");
        if (tipo == TipoEccezione.SPOSTATA) {
            if (nuovaData == null || nuovoInizio == null || nuovoFine == null) throw new IllegalArgumentException("Indica il nuovo giorno e il nuovo orario.");
            if (!nuovoFine.isAfter(nuovoInizio)) throw new IllegalArgumentException("La fine deve essere dopo l'inizio.");
        }
        this.id = UUID.randomUUID();
        this.lezione = lezione;
        this.data = data;
        this.tipo = tipo;
        this.nuovaData = tipo == TipoEccezione.SPOSTATA ? nuovaData : null;
        this.nuovoInizio = tipo == TipoEccezione.SPOSTATA ? nuovoInizio : null;
        this.nuovoFine = tipo == TipoEccezione.SPOSTATA ? nuovoFine : null;
        this.motivo = Testi.facoltativo(motivo, "il motivo", 300);
    }

    UUID getId() { return id; }
    Lezione getLezione() { return lezione; }
    LocalDate getData() { return data; }
    TipoEccezione getTipo() { return tipo; }
    LocalDate getNuovaData() { return nuovaData; }
    LocalTime getNuovoInizio() { return nuovoInizio; }
    LocalTime getNuovoFine() { return nuovoFine; }
    String getMotivo() { return motivo; }
}
