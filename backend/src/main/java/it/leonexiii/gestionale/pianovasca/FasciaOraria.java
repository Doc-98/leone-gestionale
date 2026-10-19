package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Una fascia della giornata in una vasca (es. lunedì 17:00–17:45 in vasca grande), con l'assetto delle corsie in
 * quella fascia (5 o 6). Gli orari li decide il coordinatore; le fasce della stessa vasca e giorno non si accavallano.
 */
@Entity
@Table(name = "fascia_oraria")
class FasciaOraria {

    @Id
    private UUID id;

    @Column(name = "vasca_id", nullable = false)
    private UUID vascaId;

    @Column(name = "giorno_settimana", nullable = false)
    private short giornoSettimana;

    @Column(nullable = false)
    private LocalTime inizio;

    @Column(nullable = false)
    private LocalTime fine;

    private Short assetto;

    @Column(name = "valida_dal", nullable = false)
    private LocalDate validaDal;

    @Column(name = "valida_al")
    private LocalDate validaAl;

    protected FasciaOraria() {}

    FasciaOraria(UUID vascaId, int giornoSettimana, LocalTime inizio, LocalTime fine, Integer assetto, LocalDate validaDal, LocalDate validaAl) {
        if (giornoSettimana < 1 || giornoSettimana > 7) throw new IllegalArgumentException("Giorno della settimana tra 1 (lunedì) e 7 (domenica).");
        this.id = UUID.randomUUID();
        this.vascaId = vascaId;
        this.giornoSettimana = (short) giornoSettimana;
        this.assetto = assetto == null ? null : assetto.shortValue();
        orari(inizio, fine, validaDal, validaAl);
    }

    void orari(LocalTime inizio, LocalTime fine, LocalDate validaDal, LocalDate validaAl) {
        if (inizio == null || fine == null || !fine.isAfter(inizio)) throw new IllegalArgumentException("La fine della fascia deve essere dopo l'inizio.");
        if (validaDal == null) throw new IllegalArgumentException("Indica da quando vale la fascia.");
        if (validaAl != null && validaAl.isBefore(validaDal)) throw new IllegalArgumentException("La fine della validità è prima dell'inizio.");
        this.inizio = inizio;
        this.fine = fine;
        this.validaDal = validaDal;
        this.validaAl = validaAl;
    }

    void assetto(Integer assetto) {
        this.assetto = assetto == null ? null : assetto.shortValue();
    }

    /** Vale (in parte) nel periodo indicato? */
    boolean valeTra(LocalDate da, LocalDate a) {
        return !validaDal.isAfter(a) && (validaAl == null || !validaAl.isBefore(da));
    }

    boolean valeIl(LocalDate g) {
        return g.getDayOfWeek().getValue() == giornoSettimana && valeTra(g, g);
    }

    /** L'orario si accavalla con quello indicato? */
    boolean tocca(LocalTime i, LocalTime f) {
        return inizio.isBefore(f) && i.isBefore(fine);
    }

    UUID getId() { return id; }
    UUID getVascaId() { return vascaId; }
    int getGiornoSettimana() { return giornoSettimana; }
    LocalTime getInizio() { return inizio; }
    LocalTime getFine() { return fine; }
    Integer getAssetto() { return assetto == null ? null : assetto.intValue(); }
    LocalDate getValidaDal() { return validaDal; }
    LocalDate getValidaAl() { return validaAl; }
}
