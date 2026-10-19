package it.leonexiii.gestionale.clienti;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TesseramentoRepository extends JpaRepository<Tesseramento, UUID> {
    List<Tesseramento> findByPersonaIdOrderByDalDesc(UUID personaId);
}
