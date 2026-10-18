package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Ruolo;
import it.leonexiii.gestionale.identita.Utente;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambi orario, sostituzioni e scambi. Percorso: chi chiede → (collega accetta) → coordinatore approva.
 * Gli effetti sui turni si applicano solo all'approvazione, in un'unica transazione.
 */
@Service
class RichiesteService {

    private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    record NuovaRichiesta(TipoRichiesta tipo, UUID turnoId, UUID collegaId, UUID turnoCollegaId,
                          LocalTime nuovoInizio, LocalTime nuovoFine, String messaggio) {}

    private final RichiestaRepository richieste;
    private final TurnoRepository turni;
    private final Persone persone;
    private final Permessi permessi;

    RichiesteService(RichiestaRepository richieste, TurnoRepository turni, Persone persone, Permessi permessi) {
        this.richieste = richieste;
        this.turni = turni;
        this.persone = persone;
        this.permessi = permessi;
    }

    @Transactional
    RichiestaDto crea(Utente u, NuovaRichiesta in) {
        if (in.tipo() == null) throw new IllegalArgumentException("Indica il tipo di richiesta.");
        Turno t = turno(in.turnoId());
        if (!u.personaId().equals(t.getPersonaId())) throw new AccessDeniedException("Puoi fare richieste solo sui tuoi turni.");
        controllaFuturo(t);
        RichiestaTurno r = switch (in.tipo()) {
            case CAMBIO_ORARIO -> RichiestaTurno.cambioOrario(t, u.personaId(), in.nuovoInizio(), in.nuovoFine(), in.messaggio());
            case SOSTITUZIONE -> {
                if (in.collegaId() != null) controllaCollega(in.collegaId(), u.personaId(), t);
                yield RichiestaTurno.sostituzione(t, u.personaId(), in.collegaId(), in.messaggio());
            }
            case SCAMBIO -> {
                if (in.turnoCollegaId() == null) throw new IllegalArgumentException("Scegli il turno del collega con cui scambiare.");
                Turno tc = turno(in.turnoCollegaId());
                if (tc.getPersonaId() == null || tc.getPersonaId().equals(u.personaId())) throw new IllegalArgumentException("Scegli un turno di un collega.");
                if (!tc.impiantoId().equals(t.impiantoId())) throw new IllegalArgumentException("Si scambiano turni dello stesso impianto.");
                controllaFuturo(tc);
                yield RichiestaTurno.scambio(t, u.personaId(), tc, in.messaggio());
            }
        };
        return dto(richieste.saveAndFlush(r));
    }

    /** Le mie richieste e quelle in cui sono indicato come collega. */
    @Transactional(readOnly = true)
    List<RichiestaDto> mie(Utente u) {
        return dto(richieste.diPersona(u.personaId()));
    }

    /** Sostituzioni aperte a chiunque dello staff, esclusi i miei turni. */
    @Transactional(readOnly = true)
    List<RichiestaDto> aperte(Utente u) {
        Set<UUID> impianti = permessi.impiantiStaff(u);
        if (impianti.isEmpty()) return List.of();
        return dto(richieste.sostituzioniAperte(impianti).stream().filter(r -> !r.getRichiedenteId().equals(u.personaId())).toList());
    }

    /** Per coordinatori: le richieste negli stati indicati (default: da approvare) degli impianti gestiti. */
    @Transactional(readOnly = true)
    List<RichiestaDto> perStato(Utente u, Collection<StatoRichiesta> stati) {
        Set<UUID> impianti = permessi.impiantiGestiti(u);
        if (impianti.isEmpty()) return List.of();
        return dto(richieste.perStato(stati, impianti));
    }

    @Transactional
    RichiestaDto accetta(Utente u, UUID id) {
        RichiestaTurno r = richiesta(id);
        UUID io = u.personaId();
        switch (r.getTipo()) {
            case SOSTITUZIONE -> {
                if (r.getCollegaId() == null) controllaCollega(io, r.getRichiedenteId(), r.getTurno());
                else if (!r.getCollegaId().equals(io)) throw new AccessDeniedException("La richiesta è rivolta a un altro collega.");
                Turno t = r.getTurno();
                if (turni.sovrapposto(io, t.getData(), t.getInizio(), t.getFine(), TurnoRepository.NESSUNO))
                    throw new Conflitto("Hai già un turno in quell'orario.");
            }
            case SCAMBIO -> {
                if (!io.equals(r.getCollegaId())) throw new AccessDeniedException("La richiesta è rivolta a un altro collega.");
            }
            case CAMBIO_ORARIO -> throw new Conflitto("Un cambio orario non va accettato da un collega.");
        }
        r.accettaDa(io);
        return dto(r);
    }

    @Transactional
    RichiestaDto rifiuta(Utente u, UUID id) {
        RichiestaTurno r = richiesta(id);
        if (r.getCollegaId() == null || !r.getCollegaId().equals(u.personaId())) throw new AccessDeniedException("La richiesta non è rivolta a te.");
        r.rifiutaDalCollega(u.personaId());
        return dto(r);
    }

    @Transactional
    RichiestaDto annulla(Utente u, UUID id) {
        RichiestaTurno r = richiesta(id);
        if (!r.getRichiedenteId().equals(u.personaId())) throw new AccessDeniedException("Solo chi ha fatto la richiesta può annullarla.");
        r.annulla();
        return dto(r);
    }

    @Transactional
    RichiestaDto approva(Utente u, UUID id, String nota) {
        RichiestaTurno r = richiesta(id);
        permessi.richiediGestione(u, r.getTurno().impiantoId());
        if (r.getStato() != StatoRichiesta.IN_ATTESA_APPROVAZIONE) throw new Conflitto("La richiesta non è in attesa di approvazione.");
        Turno t = r.getTurno();
        if (!t.pianificato() || !r.getRichiedenteId().equals(t.getPersonaId()))
            throw new Conflitto("Il turno è cambiato dopo la richiesta: non si può più applicare.");
        switch (r.getTipo()) {
            case CAMBIO_ORARIO -> {
                if (turni.sovrapposto(t.getPersonaId(), t.getData(), r.getNuovoInizio(), r.getNuovoFine(), t.getId()))
                    throw new Conflitto("Con il nuovo orario si accavalla con un altro turno della stessa persona.");
                t.cambiaOrario(r.getNuovoInizio(), r.getNuovoFine());
            }
            case SOSTITUZIONE -> {
                if (turni.sovrapposto(r.getCollegaId(), t.getData(), t.getInizio(), t.getFine(), TurnoRepository.NESSUNO))
                    throw new Conflitto("Il collega ha già un turno in quell'orario.");
                t.assegna(r.getCollegaId());
            }
            case SCAMBIO -> {
                Turno tc = r.getTurnoCollega();
                if (!tc.pianificato() || !r.getCollegaId().equals(tc.getPersonaId()))
                    throw new Conflitto("Il turno del collega è cambiato dopo la richiesta: non si può più applicare.");
                if (turni.sovrapposto(r.getRichiedenteId(), tc.getData(), tc.getInizio(), tc.getFine(), t.getId())
                    || turni.sovrapposto(r.getCollegaId(), t.getData(), t.getInizio(), t.getFine(), tc.getId()))
                    throw new Conflitto("Dopo lo scambio qualcuno avrebbe due turni sovrapposti.");
                // Passaggio intermedio senza persona: il vincolo anti-sovrapposizione si controlla riga per riga
                t.assegna(null);
                turni.flush();
                tc.assegna(r.getRichiedenteId());
                turni.flush();
                t.assegna(r.getCollegaId());
            }
        }
        r.approva(u.personaId(), nota);
        turni.flush();
        return dto(r);
    }

    @Transactional
    RichiestaDto respingi(Utente u, UUID id, String nota) {
        RichiestaTurno r = richiesta(id);
        permessi.richiediGestione(u, r.getTurno().impiantoId());
        r.respingi(u.personaId(), nota);
        return dto(r);
    }

    // ── Supporto ────────────────────────────────────────────────────────────

    private void controllaCollega(UUID collega, UUID richiedente, Turno t) {
        if (collega.equals(richiedente)) throw new IllegalArgumentException("Scegli un collega diverso da te.");
        if (!persone.haRuolo(collega, Ruolo.STAFF, t.impiantoId())) throw new IllegalArgumentException("Il collega non fa parte dello staff dell'impianto.");
    }

    private static void controllaFuturo(Turno t) {
        if (!t.pianificato()) throw new Conflitto("Il turno è annullato.");
        if (t.getData().isBefore(LocalDate.now(FUSO))) throw new Conflitto("Il turno è già passato.");
    }

    private Turno turno(UUID id) {
        if (id == null) throw new IllegalArgumentException("Indica il turno.");
        return turni.findById(id).orElseThrow(() -> new NonTrovato("Turno non trovato."));
    }

    private RichiestaTurno richiesta(UUID id) {
        return richieste.conTurni(id).orElseThrow(() -> new NonTrovato("Richiesta non trovata."));
    }

    private RichiestaDto dto(RichiestaTurno r) {
        return dto(List.of(r)).getFirst();
    }

    private List<RichiestaDto> dto(List<RichiestaTurno> elenco) {
        Map<UUID, String> nomi = persone.nomiCompleti(elenco.stream()
            .flatMap(r -> Stream.of(r.getRichiedenteId(), r.getCollegaId(), r.getTurno().getPersonaId(),
                r.getTurnoCollega() == null ? null : r.getTurnoCollega().getPersonaId()))
            .filter(Objects::nonNull).distinct().toList());
        return elenco.stream().map(r -> RichiestaDto.da(r, nomi)).toList();
    }
}
