package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Permesso;
import it.leonexiii.gestionale.identita.Utente;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il foglio ore mensile nel modello della società, compilato dai turni: ore per giorno nella colonna della
 * mansione, e chi è stato sostituito quando il turno arriva da una sostituzione approvata.
 */
@Service
class FoglioOreService {

    record File(String nome, byte[] contenuto) {}

    private final TurnoRepository turni;
    private final RichiestaRepository richieste;
    private final Persone persone;
    private final Permessi permessi;
    private final ModelloFoglioOre modello;

    FoglioOreService(TurnoRepository turni, RichiestaRepository richieste, Persone persone, Permessi permessi, ModelloFoglioOre modello) {
        this.turni = turni;
        this.richieste = richieste;
        this.persone = persone;
        this.permessi = permessi;
        this.modello = modello;
    }

    List<String> voci() {
        return modello.voci();
    }

    /** Il mio foglio ore, con i turni di tutti gli impianti in cui ho lavorato. */
    @Transactional(readOnly = true)
    File mio(Utente u, YearMonth mese) {
        List<Turno> miei = turni.diPersona(u.personaId(), mese.atDay(1), mese.atEndOfMonth());
        return foglio(u.personaId(), mese, miei);
    }

    /** Il foglio ore di una persona, con i turni negli impianti che gestisco. */
    @Transactional(readOnly = true)
    File diPersona(Utente u, UUID personaId, YearMonth mese) {
        List<Turno> suoi = turniGestiti(u, mese).stream().filter(t -> personaId.equals(t.getPersonaId())).toList();
        if (suoi.isEmpty()) throw new NonTrovato("Nessun turno di questa persona nel mese.");
        return foglio(personaId, mese, suoi);
    }

    /** Un archivio .zip con il foglio ore di ognuno dello staff che ha lavorato nel mese. */
    @Transactional(readOnly = true)
    File tutti(Utente u, YearMonth mese) {
        Map<UUID, List<Turno>> perPersona = new LinkedHashMap<>();
        for (Turno t : turniGestiti(u, mese)) if (t.getPersonaId() != null) perPersona.computeIfAbsent(t.getPersonaId(), k -> new ArrayList<>()).add(t);
        if (perPersona.isEmpty()) throw new NonTrovato("Nessun turno assegnato nel mese.");
        try (var buffer = new ByteArrayOutputStream(); var zip = new ZipOutputStream(buffer)) {
            for (var e : perPersona.entrySet()) {
                File f = foglio(e.getKey(), mese, e.getValue());
                zip.putNextEntry(new ZipEntry(f.nome()));
                zip.write(f.contenuto());
                zip.closeEntry();
            }
            zip.finish();
            return new File("FOGLI ORE - " + ModelloFoglioOre.MESI[mese.getMonthValue() - 1] + " " + mese.getYear() + ".zip", buffer.toByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<Turno> turniGestiti(Utente u, YearMonth mese) {
        Set<UUID> impianti = permessi.impianti(u, Permesso.ORE_STAFF);
        if (impianti.isEmpty()) return List.of();
        return turni.nelPeriodo(mese.atDay(1), mese.atEndOfMonth(), impianti);
    }

    private File foglio(UUID personaId, YearMonth mese, List<Turno> elenco) {
        List<Turno> validi = elenco.stream().filter(Turno::pianificato).filter(t -> personaId.equals(t.getPersonaId())).toList();

        Map<String, SortedMap<Integer, BigDecimal>> ore = new LinkedHashMap<>();
        for (Turno t : validi) {
            Mansione m = t.getPostazione().getMansione();
            String voce = m.getVoceFoglioOre() != null ? m.getVoceFoglioOre() : m.getNome();
            ore.computeIfAbsent(voce, k -> new TreeMap<>()).merge(t.getData().getDayOfMonth(), t.ore(), BigDecimal::add);
        }
        ore.values().forEach(g -> g.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()));

        // chi ho sostituito
        Map<UUID, Turno> perId = new HashMap<>();
        validi.forEach(t -> perId.put(t.getId(), t));
        List<RichiestaTurno> sostituzioni = richieste.sostituzioniApprovate(mese.atDay(1), mese.atEndOfMonth()).stream()
            .filter(r -> personaId.equals(r.getCollegaId()) && perId.containsKey(r.getTurno().getId())).toList();
        Map<UUID, String> nomi = persone.nomiCompleti(java.util.stream.Stream.concat(Set.of(personaId).stream(),
            sostituzioni.stream().map(RichiestaTurno::getRichiedenteId)).filter(Objects::nonNull).distinct().toList());
        SortedMap<Integer, List<String>> note = new TreeMap<>();
        for (RichiestaTurno r : sostituzioni)
            note.computeIfAbsent(r.getTurno().getData().getDayOfMonth(), k -> new ArrayList<>())
                .add("sost. " + nomi.getOrDefault(r.getRichiedenteId(), "?") + " (" + r.getTurno().getPostazione().getNome() + ")");

        String nome = nomi.getOrDefault(personaId, "?");
        byte[] xlsx = modello.compila(new ModelloFoglioOre.Compilazione(nome, mese, mese.atEndOfMonth(), ore, note));
        String file = "FOGLIO ORE - " + nome.replaceAll("[\\\\/:*?\"<>|]", "") + " - "
            + ModelloFoglioOre.MESI[mese.getMonthValue() - 1] + " " + mese.getYear() + ".xlsx";
        return new File(file, xlsx);
    }
}
