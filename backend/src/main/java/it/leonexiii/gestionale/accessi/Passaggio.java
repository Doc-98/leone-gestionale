package it.leonexiii.gestionale.accessi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Un passaggio al tornello, verde o rosso, con tutti i motivi. */
@Entity
@Table(name = "passaggio")
class Passaggio {

    @Id
    private UUID id;
    @Column(nullable = false)
    private Instant istante;
    @Column(nullable = false)
    private String codice;
    @Column(name = "braccialetto_id")
    private UUID braccialettoId;
    @Column(name = "persona_id")
    private UUID personaId;
    @Column(nullable = false)
    private boolean verde;
    @Column(name = "golden_pass", nullable = false)
    private boolean goldenPass;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Motivo> motivi = new ArrayList<>();
    @Column(name = "carnet_id")
    private UUID carnetId;
    @Column(name = "letto_da")
    private UUID lettoDa;
    @Column(nullable = false)
    private String verso = "ENTRATA";
    @Column(nullable = false)
    private boolean segnalato;

    protected Passaggio() {}

    Passaggio(Instant istante, String codice, Verso verso, UUID braccialettoId, UUID personaId, boolean verde, boolean goldenPass, List<Motivo> motivi, UUID lettoDa) {
        this.verso = verso.name();
        this.segnalato = motivi.stream().anyMatch(m -> ControlloSorveglianza.REGOLA.equals(m.regola()));
        this.id = UUID.randomUUID();
        this.istante = istante;
        this.codice = codice;
        this.braccialettoId = braccialettoId;
        this.personaId = personaId;
        this.verde = verde;
        this.goldenPass = goldenPass;
        this.motivi = new ArrayList<>(motivi);
        this.lettoDa = lettoDa;
    }

    void carnetScalato(UUID carnetId) {
        this.carnetId = carnetId;
    }

    /** Il carnet era esaurito al momento di scalare (due passaggi insieme): diventa rosso. */
    void respinto(Motivo m) {
        this.verde = false;
        this.motivi.add(m);
    }

    UUID getId() { return id; }
    Instant getIstante() { return istante; }
    String getCodice() { return codice; }
    UUID getPersonaId() { return personaId; }
    boolean isVerde() { return verde; }
    boolean isGoldenPass() { return goldenPass; }
    List<Motivo> getMotivi() { return motivi; }
    UUID getCarnetId() { return carnetId; }
    UUID getBraccialettoId() { return braccialettoId; }
    Verso verso() { return Verso.valueOf(verso); }
    boolean isSegnalato() { return segnalato; }
}
