package it.leonexiii.gestionale.pagamenti;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

interface MetodoRepository extends JpaRepository<MetodoPagamento, UUID> {
    List<MetodoPagamento> findAllByOrderByOrdineAscNomeAsc();

    /**
     * Il metodo bloccato fino alla fine della transazione. Chi ha già inserito un pagamento o un'uscita con lui tiene sulla
     * riga un blocco (lo prende la chiave esterna) fino alla fine della sua transazione, e l'eliminazione aspetta e poi
     * ricontrolla. Chi ha solo letto il metodo non lo blocca: se nel frattempo viene eliminato, l'inserimento fallisce sulla
     * chiave esterna (409, «ricarica e scegline un altro»).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MetodoPagamento m where m.id = :id")
    Optional<MetodoPagamento> bloccato(UUID id);
}
