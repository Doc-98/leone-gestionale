package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * I gruppi di staff (le mansioni) di cui un utente vede ore e compensi (ADR 0022): negli impianti in cui ha il
 * permesso «Ore dello staff», i gruppi che coordina; se in un impianto non ne coordina nessuno, tutti quelli
 * dell'impianto (la direzione).
 */
@Component
class GruppiStaff {

    private final Permessi permessi;
    private final MansioneRepository mansioni;

    GruppiStaff(Permessi permessi, MansioneRepository mansioni) {
        this.permessi = permessi;
        this.mansioni = mansioni;
    }

    /** I gruppi visibili, in ordine di nome. */
    List<Mansione> visibili(Utente u) {
        Set<UUID> impianti = permessi.impianti(u, Permesso.ORE_STAFF);
        if (impianti.isEmpty()) return List.of();
        Map<UUID, List<Mansione>> perImpianto = mansioni.findByImpiantoIdInOrderByNome(impianti).stream()
            .collect(Collectors.groupingBy(Mansione::getImpiantoId));
        Set<UUID> visibili = new HashSet<>();
        for (List<Mansione> delImpianto : perImpianto.values()) {
            List<Mansione> miei = delImpianto.stream().filter(m -> m.getCoordinatori().contains(u.personaId())).toList();
            (miei.isEmpty() ? delImpianto : miei).forEach(m -> visibili.add(m.getId()));
        }
        return mansioni.findByImpiantoIdInOrderByNome(impianti).stream().filter(m -> visibili.contains(m.getId())).toList();
    }

    Set<UUID> idVisibili(Utente u) {
        return visibili(u).stream().map(Mansione::getId).collect(Collectors.toSet());
    }
}
