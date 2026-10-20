package it.leonexiii.gestionale.accessi;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AutorizzazioneRepository extends JpaRepository<Autorizzazione, UUID> {
    List<Autorizzazione> findByPersonaIdOrderByDalDesc(UUID personaId);
}
