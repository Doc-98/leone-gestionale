package it.leonexiii.gestionale.staff;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PostazioneRepository extends JpaRepository<Postazione, UUID> {

    @Query("select p from Postazione p join fetch p.mansione m where p.attiva = true and m.impiantoId in :impianti order by p.ordine")
    List<Postazione> attive(Collection<UUID> impianti);

    /** Anche le disattivate, per la gestione. */
    @Query("select p from Postazione p join fetch p.mansione m where m.impiantoId in :impianti order by p.ordine, p.nome")
    List<Postazione> diImpianti(Collection<UUID> impianti);

    @Query("select coalesce(max(p.ordine), 0) from Postazione p where p.mansione.impiantoId = :impiantoId")
    int ultimoOrdine(UUID impiantoId);
}
