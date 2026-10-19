package it.leonexiii.gestionale.accessi;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BloccoRepository extends JpaRepository<Blocco, UUID> {
    @Query("select b from Blocco b where b.personaId = :persona or b.braccialettoId in :braccialetti order by b.dal desc")
    List<Blocco> diPersonaOBraccialetti(UUID persona, Collection<UUID> braccialetti);
}
