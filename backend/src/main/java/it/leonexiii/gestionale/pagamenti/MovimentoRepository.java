package it.leonexiii.gestionale.pagamenti;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MovimentoRepository extends JpaRepository<MovimentoBorsellino, UUID> {
    List<MovimentoBorsellino> findByPersonaIdOrderByCreatoIlDesc(UUID personaId);

    List<MovimentoBorsellino> findByPersonaIdAndSaldoOrderByCreatoIlAsc(UUID personaId, String saldo);

    List<MovimentoBorsellino> findByPagamentoId(UUID pagamentoId);

    @Query("select coalesce(sum(m.quantita), 0) from MovimentoBorsellino m where m.personaId = :persona and m.saldo = :saldo")
    BigDecimal somma(UUID persona, String saldo);

    /** Chi ha euro con la scadenza passata (da controllare per farli scadere). */
    @Query("select distinct m.personaId from MovimentoBorsellino m where m.saldo = 'EURO' and m.scadenza < :oggi")
    List<UUID> conEuroScaduti(LocalDate oggi);

    /** I movimenti dello stesso borsellino si fanno uno alla volta, fino alla fine della transazione. */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtext(cast(:persona as text))) as text)", nativeQuery = true)
    String blocca(String persona);
}
