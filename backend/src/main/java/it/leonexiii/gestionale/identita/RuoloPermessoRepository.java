package it.leonexiii.gestionale.identita;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RuoloPermessoRepository extends JpaRepository<RuoloPermesso, RuoloPermesso.Chiave> {
    @Modifying
    @Query("delete from RuoloPermesso rp where rp.chiave.ruolo = :ruolo")
    void eliminaDelRuolo(String ruolo);

    @Query("select rp from RuoloPermesso rp where rp.chiave.ruolo = :ruolo")
    List<RuoloPermesso> delRuolo(String ruolo);
}
