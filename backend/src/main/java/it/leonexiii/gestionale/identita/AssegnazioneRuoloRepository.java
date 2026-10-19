package it.leonexiii.gestionale.identita;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AssegnazioneRuoloRepository extends JpaRepository<AssegnazioneRuolo, UUID> {

    /** Ruoli validi in una data (dal ≤ giorno ≤ al, con al aperto). */
    @Query("""
        select r from AssegnazioneRuolo r
        where r.accountId = :accountId and r.dal <= :giorno and (r.al is null or r.al >= :giorno)
        """)
    List<AssegnazioneRuolo> validi(UUID accountId, LocalDate giorno);

    @Query("select r from AssegnazioneRuolo r where r.dal <= :giorno and (r.al is null or r.al >= :giorno)")
    List<AssegnazioneRuolo> tuttiValidi(LocalDate giorno);

    long countByRuolo(String ruolo);
}
