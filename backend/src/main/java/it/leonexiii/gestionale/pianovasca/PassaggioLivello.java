package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * «Passa adesso al livello successivo» (ADR 0026): il bambino tiene il suo turno ({@code iscrizioneId}) ed è in lista
 * d'attesa nei turni del livello nuovo, davanti agli altri; quando entra in uno di quei turni il passaggio è fatto.
 */
@Entity
@Table(name = "passaggio_livello")
class PassaggioLivello {

    enum Stato { IN_ATTESA, FATTO, ANNULLATO }

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "iscrizione_id", nullable = false)
    private UUID iscrizioneId;

    @Column(name = "da_corso_id", nullable = false)
    private UUID daCorsoId;

    @Column(name = "a_corso_id", nullable = false)
    private UUID aCorsoId;

    @Column(name = "deciso_da")
    private UUID decisoDa;

    @Column(name = "deciso_il", nullable = false)
    private Instant decisoIl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stato stato;

    @Column(name = "chiuso_il")
    private Instant chiusoIl;

    protected PassaggioLivello() {}

    PassaggioLivello(Iscrizione iscrizione, UUID daCorsoId, UUID aCorsoId, UUID chi, Instant quando) {
        if (aCorsoId == null) throw new IllegalArgumentException("Il corso non ha un livello successivo.");
        this.id = UUID.randomUUID();
        this.personaId = iscrizione.getPersonaId();
        this.iscrizioneId = iscrizione.getId();
        this.daCorsoId = daCorsoId;
        this.aCorsoId = aCorsoId;
        this.decisoDa = chi;
        this.decisoIl = quando;
        this.stato = Stato.IN_ATTESA;
    }

    void chiudi(Stato come, Instant quando) {
        if (stato != Stato.IN_ATTESA) throw new IllegalStateException("Il passaggio è già chiuso.");
        this.stato = come;
        this.chiusoIl = quando;
    }

    boolean aperto() { return stato == Stato.IN_ATTESA; }
    UUID getId() { return id; }
    UUID getPersonaId() { return personaId; }
    UUID getIscrizioneId() { return iscrizioneId; }
    UUID getDaCorsoId() { return daCorsoId; }
    UUID getACorsoId() { return aCorsoId; }
    Instant getDecisoIl() { return decisoIl; }
    Stato getStato() { return stato; }
}
