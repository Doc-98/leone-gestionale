package it.leonexiii.gestionale.listino;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OffertaRepository extends JpaRepository<Offerta, UUID> {

    List<Offerta> findByStagioneIdOrderByOrdineAscNomeAsc(UUID stagioneId);

    /** Le offerte (di tutte le stagioni) con almeno un prezzo riservato al gruppo. */
    @Query("select distinct o from Offerta o join o.prezzi p where p.gruppoId = :gruppoId order by o.ordine, o.nome")
    List<Offerta> conPrezziDelGruppo(UUID gruppoId);
}
