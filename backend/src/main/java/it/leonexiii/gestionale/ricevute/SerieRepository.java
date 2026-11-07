package it.leonexiii.gestionale.ricevute;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SerieRepository extends JpaRepository<Serie, UUID> {

    List<Serie> findAllByOrderByOrdineAscNomeAsc();

    /** La serie bloccata finché non finisce la transazione: due ricevute insieme non prendono lo stesso numero. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Serie s where s.id = :id")
    Optional<Serie> bloccata(UUID id);
}
