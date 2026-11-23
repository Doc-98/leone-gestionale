package it.leonexiii.gestionale.ricevute;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ImpostazioniRepository extends JpaRepository<Impostazioni, Short> {

    /**
     * La riga delle impostazioni bloccata finché non finisce la transazione: chi spegne o elimina una serie la prende prima
     * di controllare «l'ultima serie di ricevute accesa», così due richieste insieme non le spengono tutte.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Impostazioni i where i.id = 1")
    Optional<Impostazioni> bloccata();
}
