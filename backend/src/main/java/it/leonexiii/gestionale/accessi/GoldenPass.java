package it.leonexiii.gestionale.accessi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** I braccialetti della persona danno verde senza i controlli su certificato, tesseramento, abbonamento e orario. */
@Entity
@Table(name = "golden_pass")
class GoldenPass {

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    private String motivo;
    @Column(nullable = false)
    private LocalDate dal;
    private LocalDate al;
    @Column(name = "creato_da")
    private UUID creatoDa;
    @Column(name = "creato_il", nullable = false)
    private Instant creatoIl;
    @Column(name = "revocato_il")
    private Instant revocatoIl;
    @Column(name = "revocato_da")
    private UUID revocatoDa;

    protected GoldenPass() {}

    GoldenPass(UUID personaId, String motivo, LocalDate dal, LocalDate al, UUID creatoDa, Instant ora) {
        if (al != null && al.isBefore(dal)) throw new IllegalArgumentException("Il golden pass finisce prima di iniziare.");
        if (motivo != null && motivo.strip().length() > 300) throw new IllegalArgumentException("Motivo troppo lungo (massimo 300 caratteri).");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.motivo = motivo == null || motivo.isBlank() ? null : motivo.strip();
        this.dal = dal;
        this.al = al;
        this.creatoDa = creatoDa;
        this.creatoIl = ora;
    }

    void revoca(UUID da, Instant ora) {
        if (revocatoIl != null) return;
        revocatoIl = ora;
        revocatoDa = da;
    }

    boolean attivoIl(LocalDate g) {
        return revocatoIl == null && !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getMotivo() { return motivo; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    Instant getRevocatoIl() { return revocatoIl; }
}
