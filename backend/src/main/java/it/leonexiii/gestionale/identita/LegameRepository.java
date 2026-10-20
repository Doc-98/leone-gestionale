package it.leonexiii.gestionale.identita;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LegameRepository extends JpaRepository<Legame, UUID> {
    @Query("select l from Legame l where l.al is null and l.personaId in :persone")
    List<Legame> attiviDi(Collection<UUID> persone);

    @Query("select l from Legame l where l.al is null and l.tutoreId in :tutori")
    List<Legame> attiviCome(Collection<UUID> tutori);
}
