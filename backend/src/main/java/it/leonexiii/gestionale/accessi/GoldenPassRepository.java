package it.leonexiii.gestionale.accessi;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GoldenPassRepository extends JpaRepository<GoldenPass, UUID> {
    List<GoldenPass> findByPersonaIdOrderByDalDesc(UUID personaId);
}
