package it.leonexiii.gestionale.servizi;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ScritturaBraccialettoRepository extends JpaRepository<ScritturaBraccialetto, UUID> {
    @Query("select s from ScritturaBraccialetto s where s.braccialettoId = :braccialetto and s.stato = 'PREPARATA'")
    Optional<ScritturaBraccialetto> aperta(UUID braccialetto);
}
