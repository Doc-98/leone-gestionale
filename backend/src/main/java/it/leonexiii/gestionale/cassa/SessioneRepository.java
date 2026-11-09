package it.leonexiii.gestionale.cassa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessioneRepository extends JpaRepository<Sessione, UUID> {

    Optional<Sessione> findByCassaIdAndChiusaIlIsNull(UUID cassaId);

    /** L'ultima chiusura di una cassa (da lì riparte la sessione dopo, con il fondo lasciato). */
    Optional<Sessione> findFirstByCassaIdAndChiusaIlIsNotNullOrderByChiusaIlDesc(UUID cassaId);

    boolean existsByChiusaIlIsNull();

    List<Sessione> findByChiusaIlIsNull();

    long countByDaControllareTrueAndControllataIlIsNullAndChiusaIlIsNotNull();

    /** In cassaforte: chiuse dopo «dal», con qualcosa messo da parte e non ancora portate in banca. */
    List<Sessione> findByChiusaIlGreaterThanEqualAndVersamentoIdIsNullAndPerCassaforteGreaterThanOrderByChiusaIl(Instant dal, java.math.BigDecimal zero);

    List<Sessione> findByVersamentoId(UUID versamento);

    /** Le chiusure tra «da» (compreso) e «a» (escluso), le più recenti per prime. */
    List<Sessione> findByChiusaIlGreaterThanEqualAndChiusaIlLessThanOrderByChiusaIlDesc(Instant da, Instant a);
}
