package it.leonexiii.gestionale.clienti;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConsultazioneRepository extends JpaRepository<ConsultazioneScheda, UUID> {}
