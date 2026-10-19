package it.leonexiii.gestionale.clienti;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AbbonamentoRepository extends JpaRepository<Abbonamento, UUID> {
    List<Abbonamento> findByPersonaIdOrderByDalDesc(UUID personaId);

    /** Per scalare un ingresso senza che due passaggi simultanei lo scalino due volte. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Abbonamento a where a.id = :id")
    Optional<Abbonamento> bloccato(UUID id);
}
