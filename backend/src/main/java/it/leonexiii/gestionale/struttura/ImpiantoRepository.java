package it.leonexiii.gestionale.struttura;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ImpiantoRepository extends JpaRepository<Impianto, UUID> {

    @Query("select i from Impianto i order by i.nome")
    List<Impianto> ordinati();
}
