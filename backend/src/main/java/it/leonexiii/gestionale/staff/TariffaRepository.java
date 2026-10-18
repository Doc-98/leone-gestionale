package it.leonexiii.gestionale.staff;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TariffaRepository extends JpaRepository<Tariffa, UUID> {
    List<Tariffa> findByMansioneIdIn(Collection<UUID> mansioni);
}
