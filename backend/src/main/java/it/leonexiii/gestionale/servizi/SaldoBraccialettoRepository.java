package it.leonexiii.gestionale.servizi;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SaldoBraccialettoRepository extends JpaRepository<SaldoBraccialetto, UUID> {}
