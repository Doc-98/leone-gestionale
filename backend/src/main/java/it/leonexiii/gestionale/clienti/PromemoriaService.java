package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.notifiche.Posta;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * I promemoria via email delle scadenze (ADR 0013): regole modificabili con il testo, un giro automatico ogni mattina
 * e l'invio a mano dalla pagina delle scadenze. Ogni invio si registra, così lo stesso promemoria non parte due volte.
 */
@Service
class PromemoriaService {

    private static final Logger log = LoggerFactory.getLogger(PromemoriaService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    record RegolaDto(UUID id, Promemoria.Tipo tipo, int giorni, Integer maxInvii, boolean attivo, String oggetto, String testo) {}

    record ModificaRegola(int giorni, Integer maxInvii, boolean attivo, String oggetto, String testo) {}

    record NuovaRegola(Promemoria.Tipo tipo) {}

    record Pagina(List<RegolaDto> regole, List<Posta.EmailBreve> ultimeEmail, boolean postaConfigurata, String orario) {}

    record Resoconto(int email, int inviate, int nonInviate, int senzaEmail, int errori) {}

    record InvioManuale(UUID personaId, String tipo, String riferimento) {}

    private final PromemoriaRepository regole;
    private final InvioPromemoriaRepository invii;
    private final ScadenzeService scadenze;
    private final Posta posta;
    private final Clock orologio;
    private final String societa;
    private final String orario;

    PromemoriaService(PromemoriaRepository regole, InvioPromemoriaRepository invii, ScadenzeService scadenze, Posta posta, Clock orologio,
                      @Value("${gestionale.societa.nome:Leone XIII Sport}") String societa,
                      @Value("${gestionale.promemoria.cron:0 0 9 * * *}") String orario) {
        this.regole = regole;
        this.invii = invii;
        this.scadenze = scadenze;
        this.posta = posta;
        this.orologio = orologio;
        this.societa = societa;
        this.orario = orario;
    }

    /** Il tipo di regola che copre una voce delle scadenze. */
    static Promemoria.Tipo tipoPromemoria(String tipoVoce) {
        return switch (tipoVoce) {
            case "CERTIFICATO", "CERTIFICATO_MANCANTE" -> Promemoria.Tipo.CERTIFICATO;
            case "ABBONAMENTO", "CARNET" -> Promemoria.Tipo.ABBONAMENTO;
            case "TESSERAMENTO" -> Promemoria.Tipo.TESSERAMENTO;
            default -> Promemoria.Tipo.TESSERAMENTO_MANCANTE;
        };
    }

    @Transactional(readOnly = true)
    Pagina pagina() {
        List<RegolaDto> r = regole.findAll().stream()
            .sorted(Comparator.comparing((Promemoria p) -> p.tipo().ordinal()).thenComparing(Comparator.comparingInt(Promemoria::getGiorni).reversed()))
            .map(PromemoriaService::dto).toList();
        String ora = orario.matches("0 0 \\d{1,2} \\* \\* \\*") ? "ogni giorno alle " + orario.split(" ")[2] + ":00" : orario.equals("-") ? "spento" : orario;
        return new Pagina(r, posta.ultime(50), posta.configurata(), ora);
    }

    private static RegolaDto dto(Promemoria p) {
        return new RegolaDto(p.getId(), p.tipo(), p.getGiorni(), p.getMaxInvii(), p.isAttivo(), p.getOggetto(), p.getTesto());
    }

    @Transactional
    RegolaDto crea(NuovaRegola in) {
        if (in.tipo() == null) throw new IllegalArgumentException("Scegli il tipo di promemoria.");
        Promemoria p = new Promemoria(in.tipo());
        boolean mancante = in.tipo() == Promemoria.Tipo.TESSERAMENTO_MANCANTE;
        p.modifica(7, mancante ? 3 : null, false, "Promemoria per {nome}", "Buongiorno,\n\n...\n\n{societa}");
        return dto(regole.saveAndFlush(p));
    }

    @Transactional
    RegolaDto modifica(UUID id, ModificaRegola in) {
        Promemoria p = regole.findById(id).orElseThrow(() -> new NonTrovato("Promemoria non trovato."));
        p.modifica(in.giorni(), in.maxInvii(), in.attivo(), in.oggetto(), in.testo());
        return dto(p);
    }

    @Transactional
    void elimina(UUID id) {
        regole.delete(regole.findById(id).orElseThrow(() -> new NonTrovato("Promemoria non trovato.")));
    }

    /** Ogni mattina (orario configurabile; «-» per spegnere). */
    @Scheduled(cron = "${gestionale.promemoria.cron:0 0 9 * * *}", zone = "Europe/Rome")
    @Transactional
    void giroAutomatico() {
        try {
            Resoconto r = esegui();
            log.info("Promemoria del giorno: {} email ({} inviate, {} non inviate, {} senza indirizzo, {} errori)", r.email(), r.inviate(), r.nonInviate(),
                r.senzaEmail(), r.errori());
        } catch (RuntimeException e) {
            log.error("Promemoria del giorno non riusciti", e);
        }
    }

    /** Manda i promemoria dovuti oggi. Una sola email per cosa da sistemare, anche se più regole sono nella finestra. */
    @Transactional
    Resoconto esegui() {
        LocalDate oggi = LocalDate.now(orologio);
        List<Promemoria> attive = regole.findAll().stream().filter(Promemoria::isAttivo).toList();
        if (attive.isEmpty()) return new Resoconto(0, 0, 0, 0, 0);
        List<ScadenzeService.Voce> voci = scadenze.voci(oggi, 366);
        Map<UUID, List<InvioPromemoria>> giaInviati = new java.util.HashMap<>();
        for (Promemoria p : attive) giaInviati.put(p.getId(), invii.findByPromemoriaId(p.getId()));

        // per ogni voce, le regole del suo tipo che la coprono oggi e non sono già state usate
        Map<ScadenzeService.Voce, List<Promemoria>> daMandare = new LinkedHashMap<>();
        for (ScadenzeService.Voce v : voci) {
            Promemoria.Tipo tipo = tipoPromemoria(v.tipo());
            for (Promemoria p : attive) {
                if (p.tipo() != tipo || !dovuto(p, v, giaInviati.get(p.getId()))) continue;
                daMandare.computeIfAbsent(v, k -> new ArrayList<>()).add(p);
            }
        }
        int[] conti = new int[5];
        for (var e : daMandare.entrySet()) {
            Promemoria scelta = e.getValue().stream().min(Comparator.comparingInt(Promemoria::getGiorni)).orElseThrow();
            Posta.Esito esito = manda(e.getKey(), scelta, null);
            for (Promemoria p : e.getValue())
                invii.save(new InvioPromemoria(p.getId(), p.tipo().name(), e.getKey().personaId(), e.getKey().riferimento(), e.getKey().scadenza(), esito.emailId(), false, orologio.instant()));
            conti[0]++;
            conti[1 + esito.stato().ordinal()]++; // l'ordine di Posta.Stato: INVIATA, NON_INVIATA, SENZA_EMAIL, ERRORE
        }
        invii.flush();
        // conti: [totale, INVIATA, NON_INVIATA, SENZA_EMAIL, ERRORE]
        return new Resoconto(conti[0], conti[1], conti[2], conti[3], conti[4]);
    }

    private boolean dovuto(Promemoria p, ScadenzeService.Voce v, List<InvioPromemoria> inviati) {
        if (p.tipo() == Promemoria.Tipo.TESSERAMENTO_MANCANTE) {
            if (!v.tipo().equals("TESSERAMENTO_MANCANTE")) return false;
            List<InvioPromemoria> suoi = inviati.stream().filter(i -> i.getPersonaId().equals(v.personaId()) && i.getRiferimento().equals(v.riferimento())).toList();
            if (suoi.size() >= p.getMaxInvii()) return false;
            return suoi.stream().allMatch(i -> Duration.between(i.getCreatoIl(), orologio.instant()).toDays() >= p.getGiorni());
        }
        // «in scadenza»: dentro la finestra di N giorni prima, non ancora scaduto, non già mandato per questa scadenza
        if (v.giorni() == null || v.giorni() < 0 || v.giorni() > p.getGiorni()) return false;
        if (v.tipo().equals("CARNET") || v.tipo().equals("CERTIFICATO_MANCANTE")) return false;
        return inviati.stream().noneMatch(i -> i.getRiferimento().equals(v.riferimento()));
    }

    /** A mano, dalla pagina delle scadenze: con il testo della regola del tipo (attiva, la più vicina alla scadenza). */
    @Transactional
    Posta.Esito inviaOra(Utente u, InvioManuale in) {
        LocalDate oggi = LocalDate.now(orologio);
        ScadenzeService.Voce v = scadenze.voci(oggi, 366).stream()
            .filter(x -> x.personaId().equals(in.personaId()) && x.tipo().equals(in.tipo()) && x.riferimento().equals(in.riferimento())).findFirst()
            .orElseThrow(() -> new NonTrovato("Questa scadenza non c'è più (forse è già stata sistemata)."));
        Promemoria.Tipo tipo = tipoPromemoria(v.tipo());
        Promemoria regola = regole.findAll().stream().filter(p -> p.tipo() == tipo)
            .min(Comparator.comparing((Promemoria p) -> !p.isAttivo()).thenComparingInt(Promemoria::getGiorni))
            .orElseThrow(() -> new IllegalArgumentException("Manca un testo per questo tipo di promemoria: aggiungilo nei promemoria."));
        Posta.Esito esito = manda(v, regola, u.personaId());
        invii.saveAndFlush(new InvioPromemoria(regola.getId(), tipo.name(), v.personaId(), v.riferimento(), v.scadenza(), esito.emailId(), true, orologio.instant()));
        return esito;
    }

    private Posta.Esito manda(ScadenzeService.Voce v, Promemoria p, UUID da) {
        return posta.invia(v.personaId(), v.email(), riempi(p.getOggetto(), v), riempi(p.getTesto(), v), da);
    }

    /** I segnaposto dei testi: {nome}, {cognome}, {scadenza}, {giorni}, {corso}, {stagione}, {societa}. */
    String riempi(String modello, ScadenzeService.Voce v) {
        return modello
            .replace("{nome}", v.nome())
            .replace("{cognome}", v.cognome())
            .replace("{scadenza}", v.scadenza() == null ? "" : v.scadenza().format(DATA))
            .replace("{giorni}", v.giorni() == null ? "" : String.valueOf(Math.max(0, v.giorni())))
            .replace("{corso}", v.corso() == null ? "" : v.corso())
            .replace("{stagione}", v.stagione() == null ? "" : v.stagione())
            .replace("{societa}", societa);
    }
}
