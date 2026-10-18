package it.leonexiii.gestionale.staff;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ModelloRepository extends JpaRepository<ModelloSettimanale, UUID> {

    @Query("select m from ModelloSettimanale m where m.impiantoId in :impianti order by m.validoDal desc")
    List<ModelloSettimanale> diImpianti(Collection<UUID> impianti);
}
