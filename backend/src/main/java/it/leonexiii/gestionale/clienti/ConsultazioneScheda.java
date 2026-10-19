package it.leonexiii.gestionale.clienti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Chi ha aperto la scheda di un cliente e quando. */
@Entity
@Table(name = "consultazione_scheda")
class ConsultazioneScheda {

    @Id
    private UUID id;
    @Column(name = "persona_id", nullable = false)
    private UUID personaId;
    @Column(name = "consultata_da", nullable = false)
    private UUID consultataDa;
    @Column(nullable = false)
    private Instant istante;

    protected ConsultazioneScheda() {}

    ConsultazioneScheda(UUID personaId, UUID consultataDa, Instant istante) {
        this.id = UUID.randomUUID();
        this.personaId = personaId;
        this.consultataDa = consultataDa;
        this.istante = istante;
    }
}
