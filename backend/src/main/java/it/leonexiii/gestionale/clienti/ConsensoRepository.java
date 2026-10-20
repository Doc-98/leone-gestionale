package it.leonexiii.gestionale.clienti;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConsensoRepository extends JpaRepository<Consenso, UUID> {
    List<Consenso> findByPersonaIdOrderByDatoIlDesc(UUID personaId);
}
