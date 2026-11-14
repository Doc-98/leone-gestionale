package it.leonexiii.gestionale.pianovasca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** L'esito di fine stagione di un iscritto in un corso (ADR 0026): passa al livello successivo o continua nello stesso. */
@Entity
@Table(name = "esito_livello")
class EsitoLivello {

    enum Esito { PASSA, CONTINUA }

    @Id
    private UUID id;

    @Column(name = "stagione_id", nullable = false)
    private UUID stagioneId;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "corso_id", nullable = false)
    private UUID corsoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Esito esito;

    @Column(name = "deciso_da")
    private UUID decisoDa;

    @Column(name = "deciso_il", nullable = false)
    private Instant decisoIl;

    protected EsitoLivello() {}

    EsitoLivello(UUID stagioneId, UUID personaId, UUID corsoId) {
        this.id = UUID.randomUUID();
        this.stagioneId = stagioneId;
        this.personaId = personaId;
        this.corsoId = corsoId;
    }

    void decidi(Esito esito, UUID chi, Instant quando) {
        if (esito == null) throw new IllegalArgumentException("Scegli l'esito.");
        this.esito = esito;
        this.decisoDa = chi;
        this.decisoIl = quando;
    }

    UUID getId() { return id; }
    UUID getStagioneId() { return stagioneId; }
    UUID getPersonaId() { return personaId; }
    UUID getCorsoId() { return corsoId; }
    Esito getEsito() { return esito; }
    UUID getDecisoDa() { return decisoDa; }
    Instant getDecisoIl() { return decisoIl; }
}
