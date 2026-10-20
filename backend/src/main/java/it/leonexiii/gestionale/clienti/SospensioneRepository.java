package it.leonexiii.gestionale.clienti;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SospensioneRepository extends JpaRepository<Sospensione, UUID> {
    List<Sospensione> findByAbbonamentoIdInOrderByDal(Collection<UUID> abbonamenti);
}
