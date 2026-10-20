package it.leonexiii.gestionale.pianovasca;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AttesaRepository extends JpaRepository<Attesa, UUID> {

    /** Chi aspetta nei turni indicati, in ordine di arrivo. */
    @Query("select a from Attesa a where a.stato = 'IN_ATTESA' and a.lezioneId in :lezioni order by a.aggiuntaIl, a.id")
    List<Attesa> inAttesa(Collection<UUID> lezioni);

    @Query("select distinct a.lezioneId from Attesa a where a.stato = 'IN_ATTESA'")
    List<UUID> turniConAttesa();

    @Query("select a from Attesa a where a.stato = 'IN_ATTESA' and a.personaId = :personaId order by a.aggiuntaIl")
    List<Attesa> inAttesaDi(UUID personaId);

    @Query("select a from Attesa a where a.stato = 'IN_ATTESA' and a.lezioneId = :lezioneId and a.personaId = :personaId")
    Optional<Attesa> inAttesa(UUID lezioneId, UUID personaId);
}
