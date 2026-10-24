package it.leonexiii.gestionale.pagamenti;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AddebitoRepository extends JpaRepository<Addebito, UUID> {
    List<Addebito> findByPersonaIdInOrderByScadenzaAscCreatoIlAsc(Collection<UUID> persone);

    List<Addebito> findByOrigineAndRiferimentoId(Pagamenti.Origine origine, UUID riferimentoId);

    /** Bloccati fino alla fine della transazione: due pagamenti dello stesso addebito non si accavallano. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Addebito a where a.id in :ids")
    List<Addebito> bloccati(Collection<UUID> ids);
}
