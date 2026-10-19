package it.leonexiii.gestionale.accessi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PassaggioRepository extends JpaRepository<Passaggio, UUID> {
    @Query("select p from Passaggio p where p.istante >= :da and p.istante < :a order by p.istante desc")
    List<Passaggio> tra(Instant da, Instant a, Pageable pagina);

    List<Passaggio> findByPersonaIdOrderByIstanteDesc(UUID personaId, Pageable pagina);
}
