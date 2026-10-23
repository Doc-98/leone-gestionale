package it.leonexiii.gestionale.listino;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OffertaRepository extends JpaRepository<Offerta, UUID> {

    List<Offerta> findByStagioneIdOrderByOrdineAscNomeAsc(UUID stagioneId);
}
