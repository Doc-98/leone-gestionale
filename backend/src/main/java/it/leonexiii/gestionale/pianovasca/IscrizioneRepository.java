package it.leonexiii.gestionale.pianovasca;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface IscrizioneRepository extends JpaRepository<Iscrizione, UUID> {

    /** Le iscrizioni ai turni indicati che toccano il periodo. */
    @Query("select i from Iscrizione i where i.lezioneId in :lezioni and i.dal <= :a and (i.al is null or i.al >= :da)")
    List<Iscrizione> nelPeriodo(Collection<UUID> lezioni, LocalDate da, LocalDate a);

    List<Iscrizione> findByLezioneIdOrderByDal(UUID lezioneId);

    List<Iscrizione> findByPersonaIdOrderByDal(UUID personaId);
}
