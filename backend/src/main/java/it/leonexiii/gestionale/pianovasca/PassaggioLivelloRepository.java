package it.leonexiii.gestionale.pianovasca;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PassaggioLivelloRepository extends JpaRepository<PassaggioLivello, UUID> {

    @Query("select p from PassaggioLivello p where p.stato = 'IN_ATTESA' and p.iscrizioneId in :iscrizioni")
    List<PassaggioLivello> aperti(Collection<UUID> iscrizioni);
}
