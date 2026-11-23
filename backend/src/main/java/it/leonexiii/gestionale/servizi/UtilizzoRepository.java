package it.leonexiii.gestionale.servizi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UtilizzoRepository extends JpaRepository<Utilizzo, UUID> {
    @Query("select count(u) from Utilizzo u where u.personaId = :persona and u.servizioId = :servizio and u.istante >= :dal and u.esito in :esiti")
    long contaDal(UUID persona, UUID servizio, Instant dal, Collection<String> esiti);

    List<Utilizzo> findByPersonaIdOrderByIstanteDesc(UUID personaId, Pageable pagina);

    List<Utilizzo> findByDispositivoIdOrderByIstanteDesc(UUID dispositivoId, Pageable pagina);

    List<Utilizzo> findAllByOrderByIstanteDesc(Pageable pagina);

    @Query("select u.dispositivoId, count(u) from Utilizzo u where u.dispositivoId is not null and u.istante >= :dal group by u.dispositivoId")
    List<Object[]> perDispositivoDal(Instant dal);

    long countByDispositivoId(UUID dispositivoId);

    long countByServizioId(UUID servizioId);

    long countByDispositivoIdAndIstanteGreaterThanEqual(UUID dispositivoId, Instant dal);

    @Query("select u.dispositivoId, count(u) from Utilizzo u where u.dispositivoId is not null group by u.dispositivoId")
    List<Object[]> contaPerDispositivo();

    @Query("select u.servizioId, count(u) from Utilizzo u where u.servizioId is not null group by u.servizioId")
    List<Object[]> contaPerServizio();
}
