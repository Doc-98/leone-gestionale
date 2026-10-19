package it.leonexiii.gestionale.identita;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("select a from Account a join fetch a.persona where lower(a.email) = lower(:email)")
    Optional<Account> trovaPerEmail(String email);

    @Query("select a from Account a join fetch a.persona p order by p.cognome, p.nome")
    java.util.List<Account> tuttiConPersona();
}
