package it.leonexiii.gestionale.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Compenso orario per mansione, con validità; con persona_id è un'eccezione per quella persona. */
@Entity
@Table(name = "tariffa")
class Tariffa {

    @Id
    private UUID id;

    @Column(name = "mansione_id", nullable = false)
    private UUID mansioneId;

    @Column(name = "persona_id")
    private UUID personaId;

    @Column(name = "euro_ora", nullable = false)
    private BigDecimal euroOra;

    @Column(nullable = false)
    private LocalDate dal;

    private LocalDate al;

    protected Tariffa() {}

    Tariffa(UUID mansioneId, UUID personaId, BigDecimal euroOra, LocalDate dal) {
        this.id = UUID.randomUUID();
        this.mansioneId = mansioneId;
        this.personaId = personaId;
        this.dal = dal;
        correggi(euroOra, null);
    }

    /** Corregge l'importo o chiude la tariffa a una data. */
    void correggi(BigDecimal euroOra, LocalDate al) {
        if (euroOra == null || euroOra.signum() < 0) throw new IllegalArgumentException("Indica un compenso orario valido.");
        if (euroOra.scale() > 2) throw new IllegalArgumentException("Il compenso ha al massimo due decimali.");
        if (al != null && al.isBefore(dal)) throw new IllegalArgumentException("La fine della tariffa è prima dell'inizio.");
        this.euroOra = euroOra;
        this.al = al;
    }

    UUID getId() { return id; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }

    UUID getMansioneId() { return mansioneId; }
    UUID getPersonaId() { return personaId; }
    BigDecimal getEuroOra() { return euroOra; }

    boolean validaIl(LocalDate giorno) {
        return !giorno.isBefore(dal) && (al == null || !giorno.isAfter(al));
    }
}
