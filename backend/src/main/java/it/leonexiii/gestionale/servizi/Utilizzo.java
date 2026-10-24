package it.leonexiii.gestionale.servizi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un braccialetto passato su una macchina, con l'esito (anche quando non va). */
@Entity
@Table(name = "utilizzo")
class Utilizzo {

    enum Esito { OK, GRATIS, CREDITI_FINITI, SCONOSCIUTO, NON_ATTIVO, SERVIZIO_SPENTO }

    @Id
    private UUID id;
    @Column(name = "dispositivo_id", nullable = false)
    private UUID dispositivoId;
    @Column(name = "servizio_id")
    private UUID servizioId;
    @Column(nullable = false)
    private String codice;
    @Column(name = "persona_id")
    private UUID personaId;
    @Column(nullable = false)
    private Instant istante;
    @Column(nullable = false)
    private String esito;
    @Column(nullable = false)
    private int crediti;
    private String postazione;
    @Column(nullable = false)
    private boolean simulato;

    protected Utilizzo() {}

    Utilizzo(UUID id, UUID dispositivoId, UUID servizioId, String codice, UUID personaId, Instant istante, Esito esito, int crediti, String postazione, boolean simulato) {
        this.id = id;
        this.dispositivoId = dispositivoId;
        this.servizioId = servizioId;
        this.codice = codice.length() > 40 ? codice.substring(0, 40) : codice;
        this.personaId = personaId;
        this.istante = istante;
        this.esito = esito.name();
        this.crediti = crediti;
        this.postazione = postazione == null || postazione.isBlank() ? null : postazione.strip().substring(0, Math.min(20, postazione.strip().length()));
        this.simulato = simulato;
    }

    UUID getId() { return id; }
    UUID getDispositivoId() { return dispositivoId; }
    UUID getServizioId() { return servizioId; }
    String getCodice() { return codice; }
    UUID getPersonaId() { return personaId; }
    Instant getIstante() { return istante; }
    Esito esito() { return Esito.valueOf(esito); }
    int getCrediti() { return crediti; }
    String getPostazione() { return postazione; }
    boolean isSimulato() { return simulato; }
}
