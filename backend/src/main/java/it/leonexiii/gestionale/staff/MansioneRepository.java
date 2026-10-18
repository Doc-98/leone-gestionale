package it.leonexiii.gestionale.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MansioneRepository extends JpaRepository<Mansione, UUID> {

    @Query("select distinct m.impiantoId from Mansione m")
    List<UUID> impiantiConMansioni();
}
