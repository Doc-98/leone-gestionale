package it.leonexiii.gestionale.pianovasca;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EsitoLivelloRepository extends JpaRepository<EsitoLivello, UUID> {

    List<EsitoLivello> findByStagioneIdAndCorsoIdIn(UUID stagioneId, Collection<UUID> corsi);

    /** Gli esiti di fine stagione registrati per una stagione. */
    long countByStagioneId(UUID stagioneId);
}
