package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.condiviso.Conflitto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Richiesta di cambio orario, sostituzione o scambio su un turno, con il suo percorso di approvazione. */
@Entity
@Table(name = "richiesta_turno")
class RichiestaTurno {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRichiesta tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "richiedente_id", nullable = false)
    private UUID richiedenteId;

    @Column(name = "collega_id")
    private UUID collegaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_collega_id")
    private Turno turnoCollega;

    @Column(name = "nuovo_inizio")
    private LocalTime nuovoInizio;

    @Column(name = "nuovo_fine")
    private LocalTime nuovoFine;

    private String messaggio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoRichiesta stato;

    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;

    @Column(name = "decisa_da")
    private UUID decisaDa;

    @Column(name = "decisa_il")
    private Instant decisaIl;

    @Column(name = "nota_decisione")
    private String notaDecisione;

    protected RichiestaTurno() {}

    private RichiestaTurno(TipoRichiesta tipo, Turno turno, UUID richiedenteId, String messaggio, StatoRichiesta stato) {
        this.id = UUID.randomUUID();
        this.tipo = tipo;
        this.turno = turno;
        this.richiedenteId = richiedenteId;
        this.messaggio = messaggio == null || messaggio.isBlank() ? null : messaggio.trim();
        this.stato = stato;
        this.creataIl = Instant.now();
    }

    static RichiestaTurno cambioOrario(Turno turno, UUID richiedente, LocalTime inizio, LocalTime fine, String messaggio) {
        if (inizio == null || fine == null || !fine.isAfter(inizio)) throw new IllegalArgumentException("Indica il nuovo orario: la fine deve essere dopo l'inizio.");
        if (inizio.equals(turno.getInizio()) && fine.equals(turno.getFine())) throw new IllegalArgumentException("Il nuovo orario è uguale a quello attuale.");
        RichiestaTurno r = new RichiestaTurno(TipoRichiesta.CAMBIO_ORARIO, turno, richiedente, messaggio, StatoRichiesta.IN_ATTESA_APPROVAZIONE);
        r.nuovoInizio = inizio;
        r.nuovoFine = fine;
        return r;
    }

    /** collega null = sostituzione «aperta»: la può accettare chiunque dello staff dell'impianto. */
    static RichiestaTurno sostituzione(Turno turno, UUID richiedente, UUID collega, String messaggio) {
        RichiestaTurno r = new RichiestaTurno(TipoRichiesta.SOSTITUZIONE, turno, richiedente, messaggio, StatoRichiesta.IN_ATTESA_COLLEGA);
        r.collegaId = collega;
        return r;
    }

    static RichiestaTurno scambio(Turno turno, UUID richiedente, Turno turnoCollega, String messaggio) {
        RichiestaTurno r = new RichiestaTurno(TipoRichiesta.SCAMBIO, turno, richiedente, messaggio, StatoRichiesta.IN_ATTESA_COLLEGA);
        r.turnoCollega = turnoCollega;
        r.collegaId = turnoCollega.getPersonaId();
        return r;
    }

    void accettaDa(UUID collega) {
        richiedi(StatoRichiesta.IN_ATTESA_COLLEGA);
        this.collegaId = collega;
        this.stato = StatoRichiesta.IN_ATTESA_APPROVAZIONE;
    }

    void rifiutaDalCollega(UUID collega) {
        richiedi(StatoRichiesta.IN_ATTESA_COLLEGA);
        chiudi(StatoRichiesta.RESPINTA, collega, "Rifiutata dal collega.");
    }

    void annulla() {
        if (!stato.aperta()) throw new Conflitto("La richiesta è già chiusa.");
        this.stato = StatoRichiesta.ANNULLATA;
        this.decisaIl = Instant.now();
    }

    void approva(UUID decisore, String nota) {
        richiedi(StatoRichiesta.IN_ATTESA_APPROVAZIONE);
        chiudi(StatoRichiesta.APPROVATA, decisore, nota);
    }

    void respingi(UUID decisore, String nota) {
        richiedi(StatoRichiesta.IN_ATTESA_APPROVAZIONE);
        chiudi(StatoRichiesta.RESPINTA, decisore, nota);
    }

    private void richiedi(StatoRichiesta atteso) {
        if (stato != atteso) throw new Conflitto("La richiesta non è più in questo stato (ora: " + stato + ").");
    }

    private void chiudi(StatoRichiesta esito, UUID decisore, String nota) {
        this.stato = esito;
        this.decisaDa = decisore;
        this.decisaIl = Instant.now();
        this.notaDecisione = nota == null || nota.isBlank() ? null : nota.trim();
    }

    UUID getId() { return id; }
    TipoRichiesta getTipo() { return tipo; }
    Turno getTurno() { return turno; }
    UUID getRichiedenteId() { return richiedenteId; }
    UUID getCollegaId() { return collegaId; }
    Turno getTurnoCollega() { return turnoCollega; }
    LocalTime getNuovoInizio() { return nuovoInizio; }
    LocalTime getNuovoFine() { return nuovoFine; }
    String getMessaggio() { return messaggio; }
    StatoRichiesta getStato() { return stato; }
    Instant getCreataIl() { return creataIl; }
    Instant getDecisaIl() { return decisaIl; }
    String getNotaDecisione() { return notaDecisione; }
}
