package it.leonexiii.gestionale.pianovasca;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LezioneRepository extends JpaRepository<Lezione, UUID> {

    /** Le lezioni dei corsi attivi degli impianti, con spazi ed eccezioni. */
    @Query("""
        select distinct l from Lezione l join fetch l.corso c left join fetch l.spazi left join fetch l.eccezioni
        where c.impiantoId in :impianti and c.attivo = true
        """)
    List<Lezione> deiCorsiAttivi(Collection<UUID> impianti);

    @Query("select distinct l from Lezione l join fetch l.corso c left join fetch l.spazi left join fetch l.istruttori where c.id in :corsi")
    List<Lezione> deiCorsi(Collection<UUID> corsi);

    @Query("select l from Lezione l join fetch l.corso left join fetch l.spazi left join fetch l.eccezioni where l.id = :id")
    Optional<Lezione> completa(UUID id);
}
