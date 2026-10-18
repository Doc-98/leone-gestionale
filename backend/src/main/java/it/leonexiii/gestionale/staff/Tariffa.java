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

    UUID getMansioneId() { return mansioneId; }
    UUID getPersonaId() { return personaId; }
    BigDecimal getEuroOra() { return euroOra; }

    boolean validaIl(LocalDate giorno) {
        return !giorno.isBefore(dal) && (al == null || !giorno.isAfter(al));
    }
}
