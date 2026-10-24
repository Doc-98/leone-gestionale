package it.leonexiii.gestionale.pagamenti;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MetodoRepository extends JpaRepository<MetodoPagamento, UUID> {
    List<MetodoPagamento> findAllByOrderByOrdineAscNomeAsc();
}
