package it.leonexiii.gestionale.staff;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MansioneRepository extends JpaRepository<Mansione, UUID> {

    List<Mansione> findByImpiantoIdInOrderByNome(Collection<UUID> impianti);

    boolean existsByImpiantoIdAndCodice(UUID impiantoId, String codice);
}
