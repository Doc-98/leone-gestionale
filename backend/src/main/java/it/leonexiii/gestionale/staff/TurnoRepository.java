package it.leonexiii.gestionale.staff;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TurnoRepository extends JpaRepository<Turno, UUID> {

    /** Id che non corrisponde a nessun turno, per «non escludere niente» in {@link #sovrapposto}. */
    UUID NESSUNO = new UUID(0, 0);

    @Query("""
        select t from Turno t join fetch t.postazione p join fetch p.mansione
        where t.personaId = :personaId and t.data between :da and :a
        order by t.data, t.inizio
        """)
    List<Turno> diPersona(UUID personaId, LocalDate da, LocalDate a);

    @Query("""
        select t from Turno t join fetch t.postazione p join fetch p.mansione m
        where t.data between :da and :a and m.impiantoId in :impianti
        order by t.data, t.inizio, p.ordine
        """)
    List<Turno> nelPeriodo(LocalDate da, LocalDate a, Collection<UUID> impianti);

    /** Turni pianificati della persona che si accavallano con l'orario indicato (escluso un turno, per le modifiche: altrimenti NESSUNO). */
    @Query("""
        select count(t) > 0 from Turno t
        where t.personaId = :personaId and t.stato = 'PIANIFICATO' and t.data = :data
          and t.inizio < :fine and t.fine > :inizio and t.id <> :escluso
        """)
    boolean sovrapposto(UUID personaId, LocalDate data, LocalTime inizio, LocalTime fine, UUID escluso);

    @Query("select count(t) > 0 from Turno t where t.postazione.id = :postazioneId and t.data = :data and t.inizio = :inizio and t.stato = 'PIANIFICATO'")
    boolean esisteGia(UUID postazioneId, LocalDate data, LocalTime inizio);

    @Query("select count(t) from Turno t where t.postazione.id = :postazioneId and t.data >= :da and t.stato = 'PIANIFICATO'")
    long futuriSuPostazione(UUID postazioneId, LocalDate da);

    boolean existsByModelloRigaIdIn(Collection<UUID> righe);
}
