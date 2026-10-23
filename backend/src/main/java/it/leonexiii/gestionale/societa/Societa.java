package it.leonexiii.gestionale.societa;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pubblica del modulo: i dati della società e le stagioni sportive (ADR 0018). Le stagioni sono decise dalla
 * società (date e quota): niente più «da settembre a luglio» scritto nel codice.
 */
@Service
public class Societa {

    public record Dati(String nome, String ragioneSociale, String partitaIva, String codiceFiscale, String indirizzo, String cap, String citta,
                       String provincia, String email, String telefono, int giorniAnticipo) {}

    public record Stagione(UUID id, String nome, LocalDate dal, LocalDate al, BigDecimal quota, boolean quotaRimborsabile) {
        public boolean contiene(LocalDate g) {
            return !g.isBefore(dal) && !g.isAfter(al);
        }
    }

    public record NuovaStagione(String nome, LocalDate dal, LocalDate al, BigDecimal quota, Boolean quotaRimborsabile) {}

    private final DatiSocietaRepository dati;
    private final StagioneRepository stagioni;

    Societa(DatiSocietaRepository dati, StagioneRepository stagioni) {
        this.dati = dati;
        this.stagioni = stagioni;
    }

    @Transactional(readOnly = true)
    public Dati dati() {
        return riga().dati();
    }

    /** Il nome della società, come compare nelle email e nelle pagine. */
    @Transactional(readOnly = true)
    public String nome() {
        return riga().dati().nome();
    }

    @Transactional
    public Dati modifica(Dati d) {
        DatiSocieta r = riga();
        r.modifica(d);
        return r.dati();
    }

    /** Tutte le stagioni, dalla più recente. */
    @Transactional(readOnly = true)
    public List<Stagione> stagioni() {
        return stagioni.findAllByOrderByDalDesc().stream().map(StagioneSportiva::dto).toList();
    }

    /** La stagione in corso in quella data, se c'è. */
    @Transactional(readOnly = true)
    public Optional<Stagione> stagioneIl(LocalDate g) {
        return stagioni().stream().filter(s -> s.contiene(g)).findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Stagione> stagione(UUID id) {
        return id == null ? Optional.empty() : stagioni.findById(id).map(StagioneSportiva::dto);
    }

    @Transactional(readOnly = true)
    public Optional<Stagione> stagione(String nome) {
        return nome == null ? Optional.empty() : stagioni.findByNome(nome.strip()).map(StagioneSportiva::dto);
    }

    /**
     * Le stagioni per cui ha senso registrare il tesseramento in quella data: quella in corso e, se manca poco al suo
     * inizio (i giorni di anticipo della società), la successiva. Prima la più vicina nel tempo.
     */
    @Transactional(readOnly = true)
    public List<Stagione> stagioniDaProporre(LocalDate g) {
        int anticipo = riga().dati().giorniAnticipo();
        List<Stagione> out = new ArrayList<>();
        stagioneIl(g).ifPresent(out::add);
        stagioni().stream().filter(s -> s.dal().isAfter(g) && !s.dal().isAfter(g.plusDays(anticipo)))
            .min(Comparator.comparing(Stagione::dal)).ifPresent(out::add);
        return out;
    }

    @Transactional
    public Stagione creaStagione(NuovaStagione in) {
        return stagioni.saveAndFlush(new StagioneSportiva(in)).dto();
    }

    @Transactional
    public Stagione modificaStagione(UUID id, NuovaStagione in) {
        StagioneSportiva s = stagioni.findById(id).orElseThrow(() -> new NonTrovato("Stagione non trovata."));
        s.modifica(in);
        stagioni.flush();
        return s.dto();
    }

    /** I tesseramenti già registrati restano com'erano (con il nome e le date della stagione). */
    @Transactional
    public void eliminaStagione(UUID id) {
        stagioni.delete(stagioni.findById(id).orElseThrow(() -> new NonTrovato("Stagione non trovata.")));
    }

    private DatiSocieta riga() {
        return dati.findById((short) 1).orElseThrow(() -> new IllegalStateException("Manca la riga della società (migrazione V13)."));
    }
}
