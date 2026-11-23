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
    /** Vuoto se la macchina è stata eliminata: resta la copia del nome. */
    @Column(name = "dispositivo_id")
    private UUID dispositivoId;
    @Column(name = "dispositivo_nome", nullable = false)
    private String dispositivoNome;
    /** Vuoto se il servizio è stato eliminato (o la macchina non ne aveva): resta la copia del nome. */
    @Column(name = "servizio_id")
    private UUID servizioId;
    @Column(name = "servizio_nome")
    private String servizioNome;
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

    Utilizzo(UUID id, Dispositivo d, Servizio s, String codice, UUID personaId, Instant istante, Esito esito, int crediti, String postazione, boolean simulato) {
        this.id = id;
        this.dispositivoId = d.getId();
        this.dispositivoNome = d.getNome();
        this.servizioId = s == null ? null : s.getId();
        this.servizioNome = s == null ? null : s.getNome();
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
    String getDispositivoNome() { return dispositivoNome; }
    UUID getServizioId() { return servizioId; }
    String getServizioNome() { return servizioNome; }
    String getCodice() { return codice; }
    UUID getPersonaId() { return personaId; }
    Instant getIstante() { return istante; }
    Esito esito() { return Esito.valueOf(esito); }
    int getCrediti() { return crediti; }
    String getPostazione() { return postazione; }
    boolean isSimulato() { return simulato; }
}
