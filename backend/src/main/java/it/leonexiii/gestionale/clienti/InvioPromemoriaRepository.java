package it.leonexiii.gestionale.clienti;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvioPromemoriaRepository extends JpaRepository<InvioPromemoria, UUID> {
    List<InvioPromemoria> findByCreatoIlAfter(Instant dopo);

    List<InvioPromemoria> findByPromemoriaId(UUID promemoriaId);
}
