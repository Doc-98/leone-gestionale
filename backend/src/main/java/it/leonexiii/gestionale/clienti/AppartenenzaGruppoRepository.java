package it.leonexiii.gestionale.clienti;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AppartenenzaGruppoRepository extends JpaRepository<AppartenenzaGruppo, UUID> {

    @Query("select a from AppartenenzaGruppo a where a.personaId = :personaId and a.al is null order by a.dal")
    List<AppartenenzaGruppo> attiveDi(UUID personaId);

    @Query("select a from AppartenenzaGruppo a where a.gruppoId = :gruppoId and a.al is null order by a.dal")
    List<AppartenenzaGruppo> attiveDelGruppo(UUID gruppoId);

    @Query("select a.gruppoId, count(a) from AppartenenzaGruppo a where a.al is null group by a.gruppoId")
    List<Object[]> contaAttive();
}
