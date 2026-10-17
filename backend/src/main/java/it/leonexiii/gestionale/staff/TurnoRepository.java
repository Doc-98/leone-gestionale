package it.leonexiii.gestionale.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TurnoRepository extends JpaRepository<Turno, UUID> {

    @Query("""
        select t from Turno t join fetch t.postazione p join fetch p.mansione
        where t.personaId = :personaId and t.data between :da and :a
        order by t.data, t.inizio
        """)
    List<Turno> diPersona(UUID personaId, LocalDate da, LocalDate a);

    @Query("""
        select t from Turno t join fetch t.postazione p join fetch p.mansione m
        where t.data between :da and :a
        order by t.data, t.inizio, p.ordine
        """)
    List<Turno> nelPeriodo(LocalDate da, LocalDate a);
}
