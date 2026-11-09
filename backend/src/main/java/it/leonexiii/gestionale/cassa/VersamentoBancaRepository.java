package it.leonexiii.gestionale.cassa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VersamentoBancaRepository extends JpaRepository<VersamentoBanca, UUID> {

    List<VersamentoBanca> findTop10ByOrderByDataDescRegistratoIlDesc();
}
