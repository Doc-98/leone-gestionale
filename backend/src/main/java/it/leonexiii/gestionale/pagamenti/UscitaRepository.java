package it.leonexiii.gestionale.pagamenti;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UscitaRepository extends JpaRepository<Uscita, UUID> {
    List<Uscita> findByDataBetweenOrderByDataDescRegistratoIlDesc(LocalDate da, LocalDate a);

    /** Registrati dopo «da» e fino ad «a» (compreso), per la cassa. */
    List<Uscita> findByRegistratoIlGreaterThanAndRegistratoIlLessThanEqualOrderByRegistratoIl(java.time.Instant da, java.time.Instant a);
}
