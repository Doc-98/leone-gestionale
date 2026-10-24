package it.leonexiii.gestionale.servizi;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DispositivoRepository extends JpaRepository<Dispositivo, UUID> {
    List<Dispositivo> findAllByOrderByNomeAsc();
}
