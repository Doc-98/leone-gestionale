package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Braccialetti, ban, golden pass, regole del tornello e storico dei passaggi. */
@Service
class GestioneAccessiService {

    record BraccialettoDto(UUID id, String codice, String decimale, String stato, Instant statoDal, Instant assegnatoIl, String note, boolean bannato) {}

    record BloccoDto(UUID id, String tipo, UUID personaId, UUID braccialettoId, String codice, String motivo, LocalDate dal, LocalDate al, Instant revocatoIl, boolean attivo) {}

    record GoldenPassDto(UUID id, String motivo, LocalDate dal, LocalDate al, Instant revocatoIl, boolean attivo) {}

    record PassaggioDto(UUID id, Instant istante, String codice, Verso verso, boolean verde, boolean goldenPass, boolean segnalato,
                        VerificaAccessi.PersonaEsito persona, List<Motivo> motivi, boolean carnetScalato) {}

    record AutorizzazioneDto(UUID id, String tipo, String firmataDa, LocalDate firmataIl, LocalDate dal, LocalDate al, String note, Instant revocataIl, boolean attiva) {}

    record NuovaAutorizzazione(String tipo, String firmataDa, LocalDate firmataIl, LocalDate dal, LocalDate al, String note) {}

    record SchedaAccessi(List<BraccialettoDto> braccialetti, List<BloccoDto> blocchi, List<GoldenPassDto> goldenPass,
                         List<AutorizzazioneDto> autorizzazioni, List<PassaggioDto> passaggi, VerificaAccessi.Esito adesso) {}

    /** «cauzione»: false per non addebitarla (es. braccialetto sostitutivo regalato); di base si addebita, se c'è. */
    record NuovoBraccialetto(String codice, String note, Boolean cauzione) {}

    /** cauzione: DENARO, BORSELLINO o TRATTIENI; crediti: DENARO, BORSELLINO o null (non rimborsarli); metodoId per il denaro. */
    record Riconsegna(String cauzione, String crediti, UUID metodoId) {}

    record NuovoStato(String stato) {}

    /** tipo: BAN (blocca) o SORVEGLIANZA (sotto controllo: non blocca, avvisa la segreteria). */
    record NuovoBlocco(String tipo, UUID personaId, UUID braccialettoId, String motivo, LocalDate dal, LocalDate al) {}

    record NuovoGoldenPass(String motivo, LocalDate dal, LocalDate al) {}

    record ParametroDto(String nome, String etichetta, String unita, Integer valore, Integer predefinito, boolean facoltativo, String seVuoto, int max) {}

    record RegolaDto(String codice, String nome, String descrizione, int ordine, boolean valeConGoldenPass, boolean attiva,
                     List<ParametroDto> parametri, Instant modificataIl) {}

    record ModificaRegola(boolean attiva, Map<String, Integer> parametri) {}

    private final VerificaAccessi verifica;
    private final BraccialettoRepository braccialetti;
    private final BloccoRepository blocchi;
    private final GoldenPassRepository golden;
    private final RegolaRepository regole;
    private final PassaggioRepository passaggi;
    private final AutorizzazioneRepository autorizzazioni;
    private final Persone persone;
    private final Clock orologio;
    private final it.leonexiii.gestionale.pagamenti.Borsellino borsellino;

    GestioneAccessiService(VerificaAccessi verifica, BraccialettoRepository braccialetti, BloccoRepository blocchi, GoldenPassRepository golden,
                           RegolaRepository regole, PassaggioRepository passaggi, AutorizzazioneRepository autorizzazioni, Persone persone, Clock orologio,
                           it.leonexiii.gestionale.pagamenti.Borsellino borsellino) {
        this.borsellino = borsellino;
        this.verifica = verifica;
        this.braccialetti = braccialetti;
        this.blocchi = blocchi;
        this.golden = golden;
        this.regole = regole;
        this.passaggi = passaggi;
        this.autorizzazioni = autorizzazioni;
        this.persone = persone;
        this.orologio = orologio;
    }

    // ── Scheda ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    SchedaAccessi scheda(UUID personaId) {
        persona(personaId);
        LocalDate oggi = LocalDate.now(orologio);
        List<Braccialetto> suoi = braccialetti.findByPersonaIdOrderByAssegnatoIlDesc(personaId);
        Map<UUID, String> codici = suoi.stream().collect(Collectors.toMap(Braccialetto::getId, Braccialetto::getCodice));
        List<Blocco> ban = blocchi.diPersonaOBraccialetti(personaId, codici.isEmpty() ? List.of(UUID.randomUUID()) : codici.keySet());
        Set<UUID> bannati = ban.stream().filter(b -> b.ban() && b.attivoIl(oggi) && b.getBraccialettoId() != null).map(Blocco::getBraccialettoId).collect(Collectors.toSet());
        var bdto = suoi.stream().map(b -> new BraccialettoDto(b.getId(), b.getCodice(), Codici.decimale(b.getCodice()), b.stato().name(), b.getStatoDal(), b.getAssegnatoIl(), b.getNote(),
            bannati.contains(b.getId()))).toList();
        var ldto = ban.stream().map(b -> new BloccoDto(b.getId(), b.tipo().name(), b.getPersonaId(), b.getBraccialettoId(),
            b.getBraccialettoId() == null ? null : codici.get(b.getBraccialettoId()), b.getMotivo(), b.getDal(), b.getAl(), b.getRevocatoIl(), b.attivoIl(oggi))).toList();
        var gdto = golden.findByPersonaIdOrderByDalDesc(personaId).stream()
            .map(g -> new GoldenPassDto(g.getId(), g.getMotivo(), g.getDal(), g.getAl(), g.getRevocatoIl(), g.attivoIl(oggi))).toList();
        var adto = autorizzazioni.findByPersonaIdOrderByDalDesc(personaId).stream()
            .map(a -> new AutorizzazioneDto(a.getId(), a.tipo().name(), a.getFirmataDa(), a.getFirmataIl(), a.getDal(), a.getAl(), a.getNote(), a.getRevocataIl(), a.attivaIl(oggi))).toList();
        return new SchedaAccessi(bdto, ldto, gdto, adto, dto(passaggi.findByPersonaIdOrderByIstanteDesc(personaId, PageRequest.of(0, 15))), verifica.adesso(personaId));
    }

    /** Gli ultimi passaggi di oggi (per lo storico accanto al tornello). */
    @Transactional(readOnly = true)
    List<PassaggioDto> ultimiPassaggi(int quanti) {
        LocalDate oggi = LocalDate.now(orologio);
        return dto(passaggi.tra(oggi.atStartOfDay(VerificaAccessi.FUSO).toInstant(), oggi.plusDays(1).atStartOfDay(VerificaAccessi.FUSO).toInstant(),
            PageRequest.of(0, Math.clamp(quanti, 1, 100))));
    }

    private List<PassaggioDto> dto(List<Passaggio> lista) {
        Set<UUID> ids = new HashSet<>();
        lista.forEach(p -> { if (p.getPersonaId() != null) ids.add(p.getPersonaId()); });
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(ids);
        LocalDate oggi = LocalDate.now(orologio);
        return lista.stream().map(p -> {
            Persone.PersonaScheda s = p.getPersonaId() == null ? null : schede.get(p.getPersonaId());
            var pe = s == null ? null : new VerificaAccessi.PersonaEsito(s.id(), s.nome(), s.cognome(),
                s.dataNascita() == null ? null : Period.between(s.dataNascita(), oggi).getYears(), 0);
            return new PassaggioDto(p.getId(), p.getIstante(), p.getCodice(), p.verso(), p.isVerde(), p.isGoldenPass(), p.isSegnalato(), pe, p.getMotivi(), p.getCarnetId() != null);
        }).toList();
    }

    // ── Braccialetti ────────────────────────────────────────────────────────

    @Transactional
    BraccialettoDto assegna(Utente u, UUID personaId, NuovoBraccialetto in) {
        persona(personaId);
        String codice = Codici.normalizza(in.codice());
        var inUso = braccialetti.findByCodiceOrderByAssegnatoIlDesc(codice).stream().filter(b -> !b.stato().libero()).findFirst();
        if (inUso.isPresent()) {
            if (inUso.get().getPersonaId().equals(personaId)) throw new Conflitto("Questo braccialetto è già suo.");
            throw new Conflitto("Questo braccialetto è già assegnato a " + persone.nomiCompleti(List.of(inUso.get().getPersonaId())).get(inUso.get().getPersonaId())
                + ": va prima disattivato.");
        }
        String note = in.note() == null || in.note().isBlank() ? null : in.note().strip();
        if (note != null && note.length() > 300) throw new IllegalArgumentException("Note troppo lunghe (massimo 300 caratteri).");
        Braccialetto b = braccialetti.saveAndFlush(new Braccialetto(codice, personaId, note, orologio.instant()));
        if (!Boolean.FALSE.equals(in.cauzione())) borsellino.addebitaCauzione(personaId, b.getId(), b.getCodice(), u.personaId());
        return new BraccialettoDto(b.getId(), b.getCodice(), Codici.decimale(b.getCodice()), b.stato().name(), b.getStatoDal(), b.getAssegnatoIl(), b.getNote(), false);
    }

    /** ATTIVO (anche «ritrovato»), SMARRITO, DISATTIVATO. Smarrito: con la regola «crediti persi», i crediti si perdono. */
    @Transactional
    int cambiaStato(Utente u, UUID id, NuovoStato in) {
        Braccialetto b = braccialetti.findById(id).orElseThrow(() -> new NonTrovato("Braccialetto non trovato."));
        Braccialetto.Stato nuovo;
        try {
            nuovo = Braccialetto.Stato.valueOf(String.valueOf(in.stato()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Stato non valido: ATTIVO, SMARRITO o DISATTIVATO.");
        }
        if (nuovo == Braccialetto.Stato.RESTITUITO) throw new IllegalArgumentException("Per la riconsegna usa «Riconsegnato», che restituisce cauzione e crediti.");
        boolean eraSmarrito = b.stato() == Braccialetto.Stato.SMARRITO;
        b.cambiaStato(nuovo, orologio.instant());
        braccialetti.flush();
        // i crediti sono della persona: si perdono solo se non le resta un altro braccialetto attivo
        if (nuovo == Braccialetto.Stato.SMARRITO && !eraSmarrito && altriAttivi(b) == 0) return borsellino.smarrito(b.getPersonaId(), b.getId(), u.personaId());
        return 0;
    }

    /** Il braccialetto torna alla società: cauzione e (se era l'ultimo) crediti dei servizi si restituiscono. */
    @Transactional
    it.leonexiii.gestionale.pagamenti.Borsellino.EsitoRiconsegna riconsegna(Utente u, UUID id, Riconsegna in) {
        Braccialetto b = braccialetti.findById(id).orElseThrow(() -> new NonTrovato("Braccialetto non trovato."));
        if (b.stato() == Braccialetto.Stato.RESTITUITO) throw new Conflitto("Il braccialetto è già stato riconsegnato.");
        b.cambiaStato(Braccialetto.Stato.RESTITUITO, orologio.instant());
        braccialetti.flush();
        String crediti = altriAttivi(b) == 0 ? in.crediti() : null;
        return borsellino.riconsegna(b.getPersonaId(), b.getId(), in.cauzione(), crediti, in.metodoId(), u.personaId());
    }

    private long altriAttivi(Braccialetto b) {
        return braccialetti.findByPersonaIdOrderByAssegnatoIlDesc(b.getPersonaId()).stream().filter(x -> !x.getId().equals(b.getId()) && x.attivo()).count();
    }

    // ── Ban e golden pass ───────────────────────────────────────────────────

    @Transactional
    UUID banna(Utente u, NuovoBlocco in) {
        if (in.braccialettoId() != null) braccialetti.findById(in.braccialettoId()).orElseThrow(() -> new NonTrovato("Braccialetto non trovato."));
        else if (in.personaId() != null) persona(in.personaId());
        LocalDate dal = in.dal() != null ? in.dal() : LocalDate.now(orologio);
        Blocco.Tipo tipo;
        try {
            tipo = in.tipo() == null ? Blocco.Tipo.BAN : Blocco.Tipo.valueOf(in.tipo());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo non valido: BAN o SORVEGLIANZA.");
        }
        return blocchi.saveAndFlush(new Blocco(tipo, in.braccialettoId() != null ? null : in.personaId(), in.braccialettoId(), in.motivo(), dal, in.al(),
            u.personaId(), orologio.instant())).getId();
    }

    @Transactional
    void revocaBan(Utente u, UUID id) {
        blocchi.findById(id).orElseThrow(() -> new NonTrovato("Ban non trovato.")).revoca(u.personaId(), orologio.instant());
    }

    @Transactional
    UUID goldenPass(Utente u, UUID personaId, NuovoGoldenPass in) {
        persona(personaId);
        LocalDate oggi = LocalDate.now(orologio);
        if (golden.findByPersonaIdOrderByDalDesc(personaId).stream().anyMatch(g -> g.attivoIl(oggi))) throw new Conflitto("Ha già un golden pass attivo.");
        LocalDate dal = in.dal() != null ? in.dal() : oggi;
        return golden.saveAndFlush(new GoldenPass(personaId, in.motivo(), dal, in.al(), u.personaId(), orologio.instant())).getId();
    }

    @Transactional
    void revocaGoldenPass(Utente u, UUID id) {
        golden.findById(id).orElseThrow(() -> new NonTrovato("Golden pass non trovato.")).revoca(u.personaId(), orologio.instant());
    }

    // ── Permessi firmati (uscita autonoma, accompagnatore) ───────────────

    @Transactional
    UUID autorizza(Utente u, UUID personaId, NuovaAutorizzazione in) {
        persona(personaId);
        Autorizzazione.Tipo tipo;
        try {
            tipo = Autorizzazione.Tipo.valueOf(String.valueOf(in.tipo()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo non valido: USCITA_AUTONOMA o ACCOMPAGNATORE.");
        }
        LocalDate dal = in.dal() != null ? in.dal() : LocalDate.now(orologio);
        return autorizzazioni.saveAndFlush(new Autorizzazione(personaId, tipo, in.firmataDa(), in.firmataIl(), dal, in.al(), in.note(), u.personaId(), orologio.instant())).getId();
    }

    @Transactional
    void revocaAutorizzazione(Utente u, UUID id) {
        autorizzazioni.findById(id).orElseThrow(() -> new NonTrovato("Permesso non trovato.")).revoca(u.personaId(), orologio.instant());
    }

    /** I passaggi di persone sotto controllo dopo un istante (per gli avvisi in segreteria), al massimo di oggi. */
    @Transactional(readOnly = true)
    List<PassaggioDto> segnalazioni(Instant dopo) {
        Instant inizio = LocalDate.now(orologio).atStartOfDay(VerificaAccessi.FUSO).toInstant();
        return dto(passaggi.segnalatiDopo(dopo == null || dopo.isBefore(inizio) ? inizio : dopo));
    }

    // ── Regole ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<RegolaDto> regole() {
        Map<String, RegolaAccesso> salvate = verifica.regoleSalvate();
        List<RegolaDto> out = new ArrayList<>();
        for (ControlloAccesso c : verifica.controlli()) {
            Parametri p = verifica.parametriDi(c, salvate);
            RegolaAccesso r = salvate.get(c.regola());
            out.add(new RegolaDto(c.regola(), c.nome(), c.descrizione(), c.ordine(), c.valeConGoldenPass(), VerificaAccessi.attivo(c, salvate),
                c.parametri().stream().map(x -> new ParametroDto(x.nome(), x.etichetta(), x.unita(), p.intero(x.nome()), x.predefinito(), x.facoltativo(),
                    x.seVuoto(), x.max())).toList(),
                r == null ? null : r.getModificataIl()));
        }
        return out;
    }

    @Transactional
    void modificaRegola(Utente u, String codice, ModificaRegola in) {
        ControlloAccesso c = verifica.controlli().stream().filter(x -> x.regola().equals(codice)).findFirst()
            .orElseThrow(() -> new NonTrovato("Regola non trovata."));
        Map<String, Object> valori = new HashMap<>();
        Map<String, Integer> dati = in.parametri() == null ? Map.of() : in.parametri();
        for (String k : dati.keySet())
            if (c.parametri().stream().noneMatch(p -> p.nome().equals(k))) throw new IllegalArgumentException("Parametro sconosciuto: " + k + ".");
        for (ControlloAccesso.Parametro p : c.parametri()) {
            Integer v = dati.containsKey(p.nome()) ? dati.get(p.nome()) : verifica.parametriDi(c, verifica.regoleSalvate()).intero(p.nome());
            if (v == null && !p.facoltativo()) throw new IllegalArgumentException("Indica «" + p.etichetta() + "».");
            if (v != null && (v < 0 || v > p.max())) throw new IllegalArgumentException("«" + p.etichetta() + "» va da 0 a " + p.max() + ".");
            valori.put(p.nome(), v);
        }
        RegolaAccesso r = regole.findById(codice).orElseGet(() -> new RegolaAccesso(codice));
        r.modifica(in.attiva(), valori, u.personaId(), orologio.instant());
        regole.saveAndFlush(r);
    }

    private void persona(UUID id) {
        if (persone.schede(List.of(id)).isEmpty()) throw new NonTrovato("Persona non trovata.");
    }
}
