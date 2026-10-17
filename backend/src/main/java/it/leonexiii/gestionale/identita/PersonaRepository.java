package it.leonexiii.gestionale.identita;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PersonaRepository extends JpaRepository<Persona, UUID> {}
