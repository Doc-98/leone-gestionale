package it.leonexiii.gestionale.pianovasca;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CorsoRepository extends JpaRepository<Corso, UUID> {
    List<Corso> findByImpiantoIdInOrderByNome(Collection<UUID> impianti);
}
