package it.leonexiii.gestionale.cassa;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CassaRepository extends JpaRepository<Cassa, UUID> {

    List<Cassa> findAllByOrderByOrdineAscNomeAsc();

    Optional<Cassa> findByPrincipaleTrue();

    /** La cassa bloccata finché non finisce la transazione: due aperture o chiusure insieme non si pestano i piedi. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cassa c where c.id = :id")
    Optional<Cassa> bloccata(UUID id);
}
