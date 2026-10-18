package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Ruolo;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciò che oggi sono le sezioni e le righe dell'Excel dei turni: mansioni, postazioni e le tariffe orarie.
 * Lo gestiscono coordinatori (del loro impianto) e admin. Niente si cancella: le postazioni si disattivano,
 * le tariffe si chiudono con una data di fine.
 */
@Service
class AnagraficheService {

    private static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    record ImpiantoDto(UUID id, String nome) {}

    record SpazioDto(UUID id, UUID impiantoId, String nome, String tipo) {}

    record PostazioneGestioneDto(UUID id, UUID mansioneId, String nome, UUID spazioId, int ordine, boolean attiva) {}

    record MansioneDto(UUID id, UUID impiantoId, String codice, String nome, String voceFoglioOre, List<PostazioneGestioneDto> postazioni) {}

    record MansioneInput(UUID impiantoId, String nome, String voceFoglioOre) {}

    record PostazioneInput(String nome, UUID spazioId, Boolean attiva) {}

    /** Esito di una modifica alla postazione: quanti turni futuri restano su una postazione disattivata. */
    record EsitoPostazione(PostazioneGestioneDto postazione, long turniFuturi) {}

    record TariffaDto(UUID id, UUID mansioneId, String mansione, PersonaBreve persona, BigDecimal euroOra, LocalDate dal, LocalDate al) {}

    record NuovaTariffa(UUID mansioneId, UUID personaId, BigDecimal euroOra, LocalDate dal) {}

    record CorrezioneTariffa(BigDecimal euroOra, LocalDate al) {}

    private final MansioneRepository mansioni;
    private final PostazioneRepository postazioni;
    private final TariffaRepository tariffe;
    private final TurnoRepository turni;
    private final Impianti impianti;
    private final Persone persone;
    private final Permessi permessi;
    private final ModelloFoglioOre foglioOre;

    AnagraficheService(MansioneRepository mansioni, PostazioneRepository postazioni, TariffaRepository tariffe, TurnoRepository turni,
                       Impianti impianti, Persone persone, Permessi permessi, ModelloFoglioOre foglioOre) {
        this.foglioOre = foglioOre;
        this.mansioni = mansioni;
        this.postazioni = postazioni;
        this.tariffe = tariffe;
        this.turni = turni;
        this.impianti = impianti;
        this.persone = persone;
        this.permessi = permessi;
    }

    @Transactional(readOnly = true)
    List<ImpiantoDto> impianti(Utente u) {
        Set<UUID> gestiti = permessi.impiantiGestiti(u);
        return impianti.tutti().stream().filter(i -> gestiti.contains(i.id())).map(i -> new ImpiantoDto(i.id(), i.nome())).toList();
    }

    @Transactional(readOnly = true)
    List<SpazioDto> spazi(Utente u) {
        return impianti.spazi(permessi.impiantiGestiti(u)).stream()
            .map(s -> new SpazioDto(s.id(), s.impiantoId(), s.nome(), s.tipo())).toList();
    }

    // ── Mansioni e postazioni ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<MansioneDto> mansioni(Utente u) {
        Set<UUID> gestiti = permessi.impiantiGestiti(u);
        if (gestiti.isEmpty()) return List.of();
        var perMansione = postazioni.diImpianti(gestiti).stream()
            .collect(java.util.stream.Collectors.groupingBy(p -> p.getMansione().getId()));
        return mansioni.findByImpiantoIdInOrderByNome(gestiti).stream()
            .map(m -> new MansioneDto(m.getId(), m.getImpiantoId(), m.getCodice(), m.getNome(), m.getVoceFoglioOre(),
                perMansione.getOrDefault(m.getId(), List.of()).stream().map(AnagraficheService::dto).toList()))
            .toList();
    }

    @Transactional
    MansioneDto creaMansione(Utente u, MansioneInput in) {
        if (in.impiantoId() == null) throw new IllegalArgumentException("Scegli l'impianto.");
        permessi.richiediGestione(u, in.impiantoId());
        String nome = Testi.obbligatorio(in.nome(), "il nome della mansione", 120);
        String codice = codiceLibero(in.impiantoId(), codice(nome));
        Mansione nuova = new Mansione(in.impiantoId(), codice, nome);
        nuova.voceFoglioOre(foglioOre.voceValida(in.voceFoglioOre()));
        Mansione m = mansioni.saveAndFlush(nuova);
        return new MansioneDto(m.getId(), m.getImpiantoId(), m.getCodice(), m.getNome(), m.getVoceFoglioOre(), List.of());
    }

    @Transactional
    MansioneDto rinominaMansione(Utente u, UUID id, MansioneInput in) {
        Mansione m = mansione(u, id);
        m.rinomina(in.nome());
        m.voceFoglioOre(foglioOre.voceValida(in.voceFoglioOre()));
        return new MansioneDto(m.getId(), m.getImpiantoId(), m.getCodice(), m.getNome(), m.getVoceFoglioOre(),
            postazioni.diImpianti(Set.of(m.getImpiantoId())).stream().filter(p -> p.getMansione().getId().equals(id)).map(AnagraficheService::dto).toList());
    }

    @Transactional
    PostazioneGestioneDto creaPostazione(Utente u, UUID mansioneId, PostazioneInput in) {
        Mansione m = mansione(u, mansioneId);
        controllaSpazio(in.spazioId(), m.getImpiantoId());
        Postazione p = new Postazione(m, in.nome(), in.spazioId(), postazioni.ultimoOrdine(m.getImpiantoId()) + 1);
        return dto(postazioni.saveAndFlush(p));
    }

    @Transactional
    EsitoPostazione modificaPostazione(Utente u, UUID id, PostazioneInput in) {
        Postazione p = postazioni.findById(id).orElseThrow(() -> new NonTrovato("Postazione non trovata."));
        permessi.richiediGestione(u, p.getMansione().getImpiantoId());
        controllaSpazio(in.spazioId(), p.getMansione().getImpiantoId());
        boolean attiva = in.attiva() == null ? p.isAttiva() : in.attiva();
        p.modifica(in.nome(), in.spazioId(), attiva);
        long futuri = attiva ? 0 : turni.futuriSuPostazione(id, LocalDate.now(FUSO));
        return new EsitoPostazione(dto(p), futuri);
    }

    /** Nuovo ordine delle postazioni (è l'ordine delle righe nella pianificazione). */
    @Transactional
    List<MansioneDto> ordinaPostazioni(Utente u, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Nessuna postazione da ordinare.");
        List<Postazione> elenco = postazioni.findAllById(ids);
        if (elenco.size() != Set.copyOf(ids).size()) throw new NonTrovato("Postazione non trovata.");
        for (Postazione p : elenco) permessi.richiediGestione(u, p.getMansione().getImpiantoId());
        Map<UUID, Postazione> perId = new java.util.HashMap<>();
        elenco.forEach(p -> perId.put(p.getId(), p));
        for (int i = 0; i < ids.size(); i++) perId.get(ids.get(i)).ordina(i + 1);
        postazioni.flush();
        return mansioni(u);
    }

    // ── Tariffe ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<TariffaDto> tariffe(Utente u) {
        Set<UUID> gestiti = permessi.impiantiGestiti(u);
        if (gestiti.isEmpty()) return List.of();
        Map<UUID, Mansione> perId = new java.util.HashMap<>();
        mansioni.findByImpiantoIdInOrderByNome(gestiti).forEach(m -> perId.put(m.getId(), m));
        if (perId.isEmpty()) return List.of();
        List<Tariffa> elenco = tariffe.findByMansioneIdIn(perId.keySet());
        Map<UUID, String> nomi = persone.nomiCompleti(elenco.stream().map(Tariffa::getPersonaId).filter(Objects::nonNull).distinct().toList());
        return elenco.stream()
            .sorted(Comparator.comparing((Tariffa t) -> perId.get(t.getMansioneId()).getNome())
                .thenComparing(t -> t.getPersonaId() == null ? "" : nomi.getOrDefault(t.getPersonaId(), "?"))
                .thenComparing(Tariffa::getDal, Comparator.reverseOrder()))
            .map(t -> dto(t, perId.get(t.getMansioneId()), nomi))
            .toList();
    }

    /**
     * Una nuova tariffa dalla data indicata. La tariffa precedente per la stessa mansione (e la stessa persona,
     * per le eccezioni) si chiude il giorno prima; se ne esiste già una che parte dallo stesso giorno, si
     * corregge l'importo di quella.
     */
    @Transactional
    TariffaDto nuovaTariffa(Utente u, NuovaTariffa in) {
        if (in.mansioneId() == null) throw new IllegalArgumentException("Scegli la mansione.");
        if (in.dal() == null) throw new IllegalArgumentException("Indica da quando vale la tariffa.");
        Mansione m = mansione(u, in.mansioneId());
        if (in.personaId() != null && !persone.haRuolo(in.personaId(), Ruolo.STAFF, m.getImpiantoId()))
            throw new IllegalArgumentException("Questa persona non fa parte dello staff dell'impianto.");

        List<Tariffa> stesse = tariffe.findByMansioneIdIn(List.of(m.getId())).stream()
            .filter(t -> Objects.equals(t.getPersonaId(), in.personaId())).toList();
        Tariffa risultato = null;
        for (Tariffa t : stesse) {
            if (t.getDal().equals(in.dal())) {
                t.correggi(in.euroOra(), t.getAl());
                risultato = t;
            } else if (t.getDal().isAfter(in.dal())) {
                throw new Conflitto("Esiste già una tariffa che parte dopo il " + in.dal() + ": correggi quella.");
            } else if (t.getAl() == null || !t.getAl().isBefore(in.dal())) {
                t.correggi(t.getEuroOra(), in.dal().minusDays(1));
            }
        }
        if (risultato == null) risultato = tariffe.save(new Tariffa(m.getId(), in.personaId(), in.euroOra(), in.dal()));
        tariffe.flush();
        return dto(risultato, m, persone.nomiCompleti(in.personaId() == null ? List.of() : List.of(in.personaId())));
    }

    @Transactional
    TariffaDto correggiTariffa(Utente u, UUID id, CorrezioneTariffa in) {
        Tariffa t = tariffe.findById(id).orElseThrow(() -> new NonTrovato("Tariffa non trovata."));
        Mansione m = mansione(u, t.getMansioneId());
        t.correggi(in.euroOra(), in.al());
        return dto(t, m, persone.nomiCompleti(t.getPersonaId() == null ? List.of() : List.of(t.getPersonaId())));
    }

    // ── Supporto ────────────────────────────────────────────────────────────

    private Mansione mansione(Utente u, UUID id) {
        Mansione m = mansioni.findById(id).orElseThrow(() -> new NonTrovato("Mansione non trovata."));
        permessi.richiediGestione(u, m.getImpiantoId());
        return m;
    }

    private void controllaSpazio(UUID spazioId, UUID impiantoId) {
        if (spazioId != null && !impianti.spazioDi(spazioId, impiantoId)) throw new IllegalArgumentException("Lo spazio non appartiene all'impianto.");
    }

    /** «Assistente spogliatoio» → «ASSISTENTE_SPOGLIATOIO». */
    static String codice(String nome) {
        String senzaAccenti = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String c = senzaAccenti.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (c.isEmpty()) c = "MANSIONE";
        return c.length() > 36 ? c.substring(0, 36) : c;
    }

    private String codiceLibero(UUID impiantoId, String base) {
        String c = base;
        for (int i = 2; mansioni.existsByImpiantoIdAndCodice(impiantoId, c); i++) c = base + "_" + i;
        return c;
    }

    private static PostazioneGestioneDto dto(Postazione p) {
        return new PostazioneGestioneDto(p.getId(), p.getMansione().getId(), p.getNome(), p.getSpazioId(), p.getOrdine(), p.isAttiva());
    }

    private static TariffaDto dto(Tariffa t, Mansione m, Map<UUID, String> nomi) {
        return new TariffaDto(t.getId(), m.getId(), m.getNome(),
            t.getPersonaId() == null ? null : new PersonaBreve(t.getPersonaId(), nomi.getOrDefault(t.getPersonaId(), "?")),
            t.getEuroOra(), t.getDal(), t.getAl());
    }
}
