package it.leonexiii.gestionale.clienti;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GruppoClientiRepository extends JpaRepository<GruppoClienti, UUID> {

    List<GruppoClienti> findAllByOrderByNome();
}
