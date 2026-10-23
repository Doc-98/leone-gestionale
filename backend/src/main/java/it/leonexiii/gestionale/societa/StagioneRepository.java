package it.leonexiii.gestionale.societa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StagioneRepository extends JpaRepository<StagioneSportiva, UUID> {

    List<StagioneSportiva> findAllByOrderByDalDesc();

    Optional<StagioneSportiva> findByNome(String nome);
}
