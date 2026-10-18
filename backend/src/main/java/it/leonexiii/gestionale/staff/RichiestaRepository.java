package it.leonexiii.gestionale.staff;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RichiestaRepository extends JpaRepository<RichiestaTurno, UUID> {

    String CON_TURNI = "select r from RichiestaTurno r join fetch r.turno t join fetch t.postazione p join fetch p.mansione m "
        + "left join fetch r.turnoCollega tc left join fetch tc.postazione pc left join fetch pc.mansione ";

    /** Le richieste che ho fatto o che mi riguardano come collega, dalle più recenti. */
    @Query(CON_TURNI + "where r.richiedenteId = :persona or r.collegaId = :persona order by r.creataIl desc")
    List<RichiestaTurno> diPersona(UUID persona);

    /** Sostituzioni aperte (nessun collega ancora) negli impianti indicati. */
    @Query(CON_TURNI + "where r.tipo = 'SOSTITUZIONE' and r.stato = 'IN_ATTESA_COLLEGA' and r.collegaId is null and m.impiantoId in :impianti order by t.data, t.inizio")
    List<RichiestaTurno> sostituzioniAperte(Collection<UUID> impianti);

    @Query(CON_TURNI + "where r.stato in :stati and m.impiantoId in :impianti order by r.creataIl")
    List<RichiestaTurno> perStato(Collection<StatoRichiesta> stati, Collection<UUID> impianti);

    @Query(CON_TURNI + "where r.id = :id")
    java.util.Optional<RichiestaTurno> conTurni(UUID id);

    /** Sostituzioni approvate sui turni del periodo: nel foglio ore si indica chi è stato sostituito. */
    @Query("select r from RichiestaTurno r join fetch r.turno t where r.tipo = 'SOSTITUZIONE' and r.stato = 'APPROVATA' and t.data between :da and :a")
    List<RichiestaTurno> sostituzioniApprovate(java.time.LocalDate da, java.time.LocalDate a);
}
