package it.leonexiii.gestionale.servizi;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DispositivoRepository extends JpaRepository<Dispositivo, UUID> {
    /** Prima le macchine, poi i lettori del saldo; dentro, per nome. */
    @Query("select d from Dispositivo d order by case when d.tipo = 'SERVIZIO' then 0 else 1 end, lower(d.nome)")
    List<Dispositivo> perTipoENome();

    long countByServizioId(UUID servizioId);

    @Query("select d.servizioId, count(d) from Dispositivo d where d.servizioId is not null group by d.servizioId")
    List<Object[]> contaPerServizio();
}
