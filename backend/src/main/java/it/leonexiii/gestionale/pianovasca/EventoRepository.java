package it.leonexiii.gestionale.pianovasca;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EventoRepository extends JpaRepository<Evento, UUID> {

    /** Gli eventi che toccano il periodo (anche quelli di più giorni iniziati prima). */
    @Query("""
        select distinct e from Evento e left join fetch e.spazi
        where e.impiantoId in :impianti and e.data <= :a and coalesce(e.dataFine, e.data) >= :da
        order by e.data, e.inizio
        """)
    List<Evento> nelPeriodo(Collection<UUID> impianti, LocalDate da, LocalDate a);
}
