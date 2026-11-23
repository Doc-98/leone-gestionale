package it.leonexiii.gestionale.societa;

import it.leonexiii.gestionale.condiviso.Conflitto;
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
                       String provincia, String email, String telefono, int giorniAnticipo, String pagamentiFamiglia, Integer giorniRispostaAttesa) {

        /** Pagamenti di più familiari insieme: un pagamento unico, uno per persona, o si sceglie ogni volta. */
        public static final java.util.Set<String> PAGAMENTI_FAMIGLIA = java.util.Set.of("INSIEME", "PER_PERSONA", "A_SCELTA");
    }

    public record Stagione(UUID id, String nome, LocalDate dal, LocalDate al, BigDecimal quota, boolean quotaRimborsabile) {
        public boolean contiene(LocalDate g) {
            return !g.isBefore(dal) && !g.isAfter(al);
        }
    }

    public record NuovaStagione(String nome, LocalDate dal, LocalDate al, BigDecimal quota, Boolean quotaRimborsabile) {}

    private final DatiSocietaRepository dati;
    private final StagioneRepository stagioni;
    private final List<UsiStagione> usi;

    Societa(DatiSocietaRepository dati, StagioneRepository stagioni, List<UsiStagione> usi) {
        this.dati = dati;
        this.stagioni = stagioni;
        this.usi = usi;
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
        Stagione prima = s.dto();
        if (in.nome() != null && !in.nome().strip().equals(prima.nome())) {
            List<String> testi = usi.stream().flatMap(u -> u.usiDelNome(prima).stream()).map(UsiStagione.Uso::testo).toList();
            if (!testi.isEmpty()) {
                String elenco = testi.size() == 1 ? testi.getFirst() : String.join(", ", testi.subList(0, testi.size() - 1)) + " e " + testi.getLast();
                throw new Conflitto("Non si può rinominare: " + elenco + (testi.size() == 1 && elenco.startsWith("1 ") ? " usa" : " usano")
                    + " il nome «" + prima.nome() + "».\nIl nome resta: le date e la quota si possono cambiare.");
            }
        }
        s.modifica(in);
        stagioni.flush();
        return s.dto();
    }

    /**
     * Elimina una stagione che nessuno usa (ADR 0029). Se qualcosa la usa, 409 con cosa è e cosa fare. Le regole di prezzo
     * della stagione se ne vanno con lei: sono sue impostazioni.
     */
    @Transactional
    public void eliminaStagione(UUID id) {
        StagioneSportiva s = stagioni.findById(id).orElseThrow(() -> new NonTrovato("Stagione non trovata."));
        Optional<String> blocco = motivoBlocco(s.dto());
        if (blocco.isPresent()) throw new Conflitto(blocco.get());
        stagioni.delete(s);
        stagioni.flush();
    }

    /** Perché la stagione non si elimina adesso (due righe: cosa la usa, cosa fare), o vuoto se si può. */
    @Transactional(readOnly = true)
    public Optional<String> motivoBlocco(Stagione stagione) {
        // prima ciò che si può togliere, poi lo storico; a parità, nell'ordine dei moduli
        List<UsiStagione.Uso> tutti = usi.stream().flatMap(u -> u.usi(stagione).stream())
            .sorted(Comparator.comparing(UsiStagione.Uso::storico)).toList();
        if (tutti.isEmpty()) return Optional.empty();
        List<String> testi = tutti.stream().map(UsiStagione.Uso::testo).toList();
        String elenco = testi.size() == 1 ? testi.getFirst()
            : String.join(", ", testi.subList(0, testi.size() - 1)) + " e " + testi.getLast();
        boolean storico = tutti.stream().anyMatch(UsiStagione.Uso::storico);
        return Optional.of("Non si può eliminare: la stagione " + stagione.nome() + " ha " + elenco + ".\n"
            + (storico ? "Resta nello storico: una stagione già usata non si elimina."
                : "Svuota prima il listino: poi potrai eliminare la stagione."));
    }

    private DatiSocieta riga() {
        return dati.findById((short) 1).orElseThrow(() -> new IllegalStateException("Manca la riga della società (migrazione V13)."));
    }
}
