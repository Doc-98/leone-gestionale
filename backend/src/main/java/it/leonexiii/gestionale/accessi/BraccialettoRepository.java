package it.leonexiii.gestionale.accessi;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BraccialettoRepository extends JpaRepository<Braccialetto, UUID> {
    /** Tutti i braccialetti con quel codice, dal più recente: al massimo uno non disattivato. */
    List<Braccialetto> findByCodiceOrderByAssegnatoIlDesc(String codice);

    List<Braccialetto> findByPersonaIdOrderByAssegnatoIlDesc(UUID personaId);
}
