package it.leonexiii.gestionale.pianovasca;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PresenzaRepository extends JpaRepository<Presenza, Presenza.Chiave> {

    @Query("select p from Presenza p where p.chiave.lezioneId = :lezione and p.chiave.data = :data")
    List<Presenza> delGiorno(UUID lezione, LocalDate data);
}
