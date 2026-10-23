package it.leonexiii.gestionale.listino;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegolaPrezzoRepository extends JpaRepository<RegolaPrezzo, RegolaPrezzo.Chiave> {

    List<RegolaPrezzo> findByChiaveStagioneId(UUID stagioneId);
}
