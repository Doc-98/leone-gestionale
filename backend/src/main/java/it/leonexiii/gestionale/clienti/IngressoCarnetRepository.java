package it.leonexiii.gestionale.clienti;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IngressoCarnetRepository extends JpaRepository<IngressoCarnet, UUID> {
    List<IngressoCarnet> findByAbbonamentoIdInOrderByIstanteDesc(Collection<UUID> abbonamenti);

    /** Per ogni carnet: ingressi usati e ultimo ingresso (per le scadenze di tutti). */
    @org.springframework.data.jpa.repository.Query("select i.abbonamentoId, count(i), max(i.istante) from IngressoCarnet i group by i.abbonamentoId")
    List<Object[]> riepilogo();
}
