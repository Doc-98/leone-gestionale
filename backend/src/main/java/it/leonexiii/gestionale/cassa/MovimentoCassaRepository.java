package it.leonexiii.gestionale.cassa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MovimentoCassaRepository extends JpaRepository<MovimentoCassa, UUID> {

    List<MovimentoCassa> findBySessioneIdInOrderByRegistratoIl(Collection<UUID> sessioni);
}
