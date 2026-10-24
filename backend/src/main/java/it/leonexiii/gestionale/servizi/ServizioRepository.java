package it.leonexiii.gestionale.servizi;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ServizioRepository extends JpaRepository<Servizio, UUID> {
    List<Servizio> findAllByOrderByOrdineAscNomeAsc();
}
