package it.leonexiii.gestionale.pagamenti;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UscitaRepository extends JpaRepository<Uscita, UUID> {
    List<Uscita> findByDataBetweenOrderByDataDescRegistratoIlDesc(LocalDate da, LocalDate a);

    /** Registrati dopo «da» e fino ad «a» (compreso), per la cassa. */
    List<Uscita> findByRegistratoIlGreaterThanAndRegistratoIlLessThanEqualOrderByRegistratoIl(java.time.Instant da, java.time.Instant a);

    /** Quante uscite sono state registrate su una cassa (anche annullate). */
    long countByCassaId(java.util.UUID cassaId);

    /** Per ogni cassa: quante uscite vi sono state registrate (anche annullate). Righe [cassaId, quanti]. */
    @org.springframework.data.jpa.repository.Query("select u.cassaId, count(u) from Uscita u where u.cassaId is not null group by u.cassaId")
    List<Object[]> perCassa();
}
