package it.leonexiii.gestionale.ricevute;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RicevutaRepository extends JpaRepository<Ricevuta, UUID> {

    List<Ricevuta> findByPagamentoIdOrderByNumeroTestoAsc(UUID pagamentoId);

    @Query("select coalesce(max(r.numero), 0) from Ricevuta r where r.serieId = :serie and r.periodo = :periodo")
    int ultimoNumero(UUID serie, String periodo);

    long countBySerieIdAndPeriodo(UUID serie, String periodo);

    /** Le ricevute di una persona: intestate a lei o con lei tra i beneficiari. */
    @Query(value = "select * from ricevuta where intestatario_id = :persona or beneficiari @> cast(:cerca as jsonb) order by creata_il desc limit :quante",
        nativeQuery = true)
    List<Ricevuta> diPersona(UUID persona, String cerca, int quante);
}
