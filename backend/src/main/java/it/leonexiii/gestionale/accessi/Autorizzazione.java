package it.leonexiii.gestionale.accessi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un permesso firmato registrato in segreteria: uscita autonoma di un minorenne (firmata da un genitore), oppure
 * accompagnatore (può ripassare il braccialetto durante il cooldown).
 */
@Entity
@Table(name = "autorizzazione")
class Autorizzazione {

    enum Tipo { USCITA_AUTONOMA, ACCOMPAGNATORE }

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private String tipo;
    @Column(name = "firmata_da")
    private String firmataDa;
    @Column(name = "firmata_il")
    private LocalDate firmataIl;
    @Column(nullable = false)
    private LocalDate dal;
    private LocalDate al;
    private String note;
    @Column(name = "creata_da")
    private UUID creataDa;
    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;
    @Column(name = "revocata_il")
    private Instant revocataIl;
    @Column(name = "revocata_da")
    private UUID revocataDa;

    protected Autorizzazione() {}

    Autorizzazione(UUID personaId, Tipo tipo, String firmataDa, LocalDate firmataIl, LocalDate dal, LocalDate al, String note, UUID creataDa, Instant ora) {
        if (tipo == null) throw new IllegalArgumentException("Scegli il tipo di permesso.");
        if (al != null && al.isBefore(dal)) throw new IllegalArgumentException("Il permesso finisce prima di iniziare.");
        if (tipo == Tipo.USCITA_AUTONOMA && (firmataDa == null || firmataDa.isBlank()))
            throw new IllegalArgumentException("Indica chi ha firmato il permesso di uscita (il genitore o chi ne fa le veci).");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.tipo = tipo.name();
        this.firmataDa = breve(firmataDa, 120);
        this.firmataIl = firmataIl;
        this.dal = dal;
        this.al = al;
        this.note = breve(note, 300);
        this.creataDa = creataDa;
        this.creataIl = ora;
    }

    private static String breve(String s, int max) {
        if (s == null || s.isBlank()) return null;
        if (s.strip().length() > max) throw new IllegalArgumentException("Testo troppo lungo (massimo " + max + " caratteri).");
        return s.strip();
    }

    void revoca(UUID da, Instant ora) {
        if (revocataIl != null) return;
        revocataIl = ora;
        revocataDa = da;
    }

    boolean attivaIl(LocalDate g) {
        return revocataIl == null && !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    Tipo tipo() { return Tipo.valueOf(tipo); }
    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    String getFirmataDa() { return firmataDa; }
    LocalDate getFirmataIl() { return firmataIl; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    String getNote() { return note; }
    Instant getRevocataIl() { return revocataIl; }
}
