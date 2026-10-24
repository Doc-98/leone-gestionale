package it.leonexiii.gestionale.pagamenti;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UscitaRepository extends JpaRepository<Uscita, UUID> {
    List<Uscita> findByDataBetweenOrderByDataDescRegistratoIlDesc(LocalDate da, LocalDate a);
}
