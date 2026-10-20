package it.leonexiii.gestionale.accessi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ban di una persona (tutti i suoi braccialetti) oppure di un singolo braccialetto. Con tipo SORVEGLIANZA («sotto
 * controllo», l'alert list del vecchio gestionale) non blocca: la segreteria viene avvisata a ogni passaggio.
 */
@Entity
@Table(name = "blocco")
class Blocco {

    enum Tipo { BAN, SORVEGLIANZA }

    @Id
    private UUID id;
    @Column(nullable = false)
    private String tipo = Tipo.BAN.name();
    @Column(name = "persona_id")
    private UUID personaId;
    @Column(name = "braccialetto_id")
    private UUID braccialettoId;
    @Column(nullable = false)
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

    protected Blocco() {}

    Blocco(Tipo tipo, UUID personaId, UUID braccialettoId, String motivo, LocalDate dal, LocalDate al, UUID creatoDa, Instant ora) {
        if ((personaId == null) == (braccialettoId == null)) throw new IllegalArgumentException("Il ban è sulla persona oppure su un braccialetto.");
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Scrivi il motivo.");
        if (motivo.strip().length() > 300) throw new IllegalArgumentException("Motivo troppo lungo (massimo 300 caratteri).");
        if (al != null && al.isBefore(dal)) throw new IllegalArgumentException("Il ban finisce prima di iniziare.");
        this.id = UUID.randomUUID();
        this.tipo = (tipo == null ? Tipo.BAN : tipo).name();
        this.personaId = personaId;
        this.braccialettoId = braccialettoId;
        this.motivo = motivo.strip();
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

    Tipo tipo() { return Tipo.valueOf(tipo); }
    boolean ban() { return tipo() == Tipo.BAN; }
    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    UUID getBraccialettoId() { return braccialettoId; }
    String getMotivo() { return motivo; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    Instant getRevocatoIl() { return revocatoIl; }
}
