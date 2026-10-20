package it.leonexiii.gestionale.notifiche;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EmailInviataRepository extends JpaRepository<EmailInviata, UUID> {
    @Query("select e from EmailInviata e order by e.creataIl desc")
    List<EmailInviata> ultime(Pageable pagina);

    List<EmailInviata> findByPersonaIdOrderByCreataIlDesc(UUID personaId, Pageable pagina);
}
