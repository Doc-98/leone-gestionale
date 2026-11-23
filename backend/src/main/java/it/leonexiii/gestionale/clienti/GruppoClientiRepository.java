package it.leonexiii.gestionale.clienti;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GruppoClientiRepository extends JpaRepository<GruppoClienti, UUID> {

    List<GruppoClienti> findAllByOrderByOrdineAscNomeAsc();

    /** Per chi aggiunge persone o elimina il gruppo: la riga si blocca fino alla fine della transazione, niente corse. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GruppoClienti g where g.id = :id")
    Optional<GruppoClienti> bloccato(UUID id);
}
