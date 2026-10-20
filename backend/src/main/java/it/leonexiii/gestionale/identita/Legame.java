package it.leonexiii.gestionale.identita;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Un tutore (genitore, nonno…) di una persona, di solito un minore. Il nucleo famiglia: chi ha un tutore in comune. */
@Entity
@Table(name = "legame")
class Legame {

    enum Relazione { MADRE, PADRE, TUTORE, NONNO, ALTRO }

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(name = "tutore_id", nullable = false)
    private UUID tutoreId;
    @Column(nullable = false)
    private String relazione;
    @Column(nullable = false)
    private boolean comunicazioni;
    @Column(name = "delegato_ritiro", nullable = false)
    private boolean delegatoRitiro;
    @Column(nullable = false)
    private LocalDate dal;
    private LocalDate al;

    protected Legame() {}

    Legame(UUID personaId, UUID tutoreId, Relazione relazione, boolean comunicazioni, boolean delegatoRitiro, LocalDate dal) {
        if (personaId.equals(tutoreId)) throw new IllegalArgumentException("Una persona non può essere tutore di se stessa.");
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.tutoreId = tutoreId;
        this.dal = dal;
        modifica(relazione, comunicazioni, delegatoRitiro);
    }

    void modifica(Relazione relazione, boolean comunicazioni, boolean delegatoRitiro) {
        if (relazione == null) throw new IllegalArgumentException("Indica la relazione (madre, padre…).");
        this.relazione = relazione.name();
        this.comunicazioni = comunicazioni;
        this.delegatoRitiro = delegatoRitiro;
    }

    /** Non è più tutore da oggi (resta nello storico); se aggiunto oggi, la riga si toglie (lo decide il servizio). */
    void chiudi(LocalDate oggi) {
        this.al = oggi.minusDays(1).isBefore(dal) ? dal : oggi.minusDays(1);
    }

    boolean attivo(LocalDate g) {
        return !g.isBefore(dal) && (al == null || !g.isAfter(al));
    }

    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    UUID getTutoreId() { return tutoreId; }
    String getRelazione() { return relazione; }
    boolean isComunicazioni() { return comunicazioni; }
    boolean isDelegatoRitiro() { return delegatoRitiro; }
    LocalDate getDal() { return dal; }
}
