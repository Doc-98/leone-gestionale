package it.leonexiii.gestionale.pagamenti;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    @Query("select distinct p from Pagamento p join p.voci v where v.addebitoId in :addebiti")
    List<Pagamento> diAddebiti(Collection<UUID> addebiti);

    List<Pagamento> findByPaganteId(UUID paganteId);

    List<Pagamento> findByDataBetweenOrderByDataDescRegistratoIlDesc(LocalDate da, LocalDate a);

    @Query("select distinct p from Pagamento p join p.metodi m where m.incassatoIl is null and p.annullatoIl is null")
    List<Pagamento> conDaIncassare();

    @Query("select p from Pagamento p join p.metodi m where m.id = :metodoUsato")
    Optional<Pagamento> conMetodoUsato(UUID metodoUsato);
}
