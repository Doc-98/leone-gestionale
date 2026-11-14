package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Una persona in un gruppo di clienti, da una data; uscendo si chiude con la data di fine (lo storico resta). */
@Entity
@Table(name = "appartenenza_gruppo")
class AppartenenzaGruppo {

    @Id
    private UUID id;
    @Column(name = "gruppo_id", nullable = false)
    private UUID gruppoId;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(nullable = false)
    private LocalDate dal;
    private LocalDate al;
    /** Falso per quelle dichiarate da una famiglia finché la segreteria non le verifica: intanto non danno vantaggi. */
    @Column(nullable = false)
    private boolean verificata;
    @Column(name = "verificata_il")
    private Instant verificataIl;
    @Column(name = "verificata_da")
    private UUID verificataDa;

    protected AppartenenzaGruppo() {}

    AppartenenzaGruppo(UUID gruppoId, UUID personaId, LocalDate dal) {
        this.id = UUID.randomUUID();
        this.gruppoId = gruppoId;
        this.personaId = personaId;
        this.dal = dal;
        this.verificata = true;
    }

    /** Dichiarata da una famiglia (es. in una richiesta): resta da verificare. */
    static AppartenenzaGruppo dichiarata(UUID gruppoId, UUID personaId, LocalDate dal) {
        AppartenenzaGruppo a = new AppartenenzaGruppo(gruppoId, personaId, dal);
        a.verificata = false;
        return a;
    }

    void verifica(UUID chi, Instant quando) {
        if (verificata) throw new IllegalStateException("È già verificata.");
        verificata = true;
        verificataIl = quando;
        verificataDa = chi;
    }

    /** Esce dal gruppo da oggi (l'ultimo giorno è ieri); se era entrato oggi, è come se non fosse mai entrato. */
    boolean chiudi(LocalDate oggi) {
        if (al != null) throw new IllegalStateException("È già uscito da questo gruppo.");
        if (!dal.isBefore(oggi)) return true;
        al = oggi.minusDays(1);
        return false;
    }

    boolean attivaIl(LocalDate g) {
        return !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    UUID getId() { return id; }
    UUID getGruppoId() { return gruppoId; }
    UUID getPersonaId() { return personaId; }
    LocalDate getDal() { return dal; }
    LocalDate getAl() { return al; }
    boolean isVerificata() { return verificata; }
}
