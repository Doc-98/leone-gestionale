package it.leonexiii.gestionale.struttura;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpazioRepository extends JpaRepository<Spazio, UUID> {

    @Query("select s from Spazio s where s.impiantoId in :impianti order by s.ordine, s.nome")
    List<Spazio> diImpianti(Collection<UUID> impianti);
}
