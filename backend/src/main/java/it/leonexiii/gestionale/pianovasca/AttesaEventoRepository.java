package it.leonexiii.gestionale.pianovasca;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AttesaEventoRepository extends JpaRepository<AttesaEvento, UUID> {

    /** Gli ultimi eventi, dal più recente. */
    @Query("select e from AttesaEvento e order by e.quando desc, e.id")
    List<AttesaEvento> ultimi(Limit quanti);
}
