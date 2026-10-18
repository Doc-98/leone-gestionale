package it.leonexiii.gestionale.pianovasca;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EccezioneRepository extends JpaRepository<Eccezione, UUID> {}
