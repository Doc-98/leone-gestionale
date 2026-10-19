package it.leonexiii.gestionale.clienti;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CertificatoRepository extends JpaRepository<CertificatoMedico, UUID> {
    List<CertificatoMedico> findByPersonaIdOrderByScadenzaDesc(UUID personaId);
}
