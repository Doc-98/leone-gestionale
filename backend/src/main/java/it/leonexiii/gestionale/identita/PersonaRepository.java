package it.leonexiii.gestionale.identita;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PersonaRepository extends JpaRepository<Persona, UUID> {

    /** Ricerca per nome e cognome (anche «cognome nome»), senza distinguere maiuscole e accenti di base. */
    @Query("""
        select p from Persona p
        where lower(concat(p.nome, ' ', p.cognome)) like :testo or lower(concat(p.cognome, ' ', p.nome)) like :testo
        order by p.cognome, p.nome
        """)
    List<Persona> cerca(String testo, org.springframework.data.domain.Pageable pagina);

    /** Persone con un account attivo e un ruolo valido oggi, nell'impianto indicato o in tutta la società. */
    @Query("""
        select distinct p from Persona p, Account a, AssegnazioneRuolo r
        where a.persona = p and r.accountId = a.id and a.attivo = true
          and r.ruolo = :ruolo and (r.impiantoId is null or r.impiantoId = :impiantoId)
          and r.dal <= :giorno and (r.al is null or r.al >= :giorno)
        order by p.cognome, p.nome
        """)
    List<Persona> conRuolo(Ruolo ruolo, UUID impiantoId, LocalDate giorno);
}
