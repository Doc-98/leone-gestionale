package it.leonexiii.gestionale.servizi;

import it.leonexiii.gestionale.condiviso.Importi;
import it.leonexiii.gestionale.accessi.Braccialetti;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.pagamenti.Borsellino;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servizi a crediti, dispositivi e utilizzi; la logica di quando una macchina dice sì o no. */
@Service
class ServiziService {

    static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    /**
     * «eliminabile» (sempre vero: un servizio si elimina anche se è stato usato, ADR 0030), «motivoBlocco» (sempre vuoto:
     * non c'è niente «in corso» che lo fermi), «utilizzi» e «macchine» (quanti ne ha adesso, per il messaggio dopo
     * l'eliminazione) li ha solo chi gestisce le impostazioni; per gli altri sono vuoti.
     */
    record ServizioDto(UUID id, String nome, int crediti, int gratuitiAlGiorno, Integer minuti, boolean attivo, int ordine,
                       Boolean eliminabile, String motivoBlocco, Long utilizzi, Long macchine) {}

    record NuovoServizio(String nome, Integer crediti, Integer gratuitiAlGiorno, Integer minuti, Boolean attivo) {}

    /** Cosa c'era al momento dell'eliminazione («macchine» solo per un servizio): per il messaggio dopo. */
    record Eliminato(long utilizzi, Long macchine) {}

    record Ordine(List<UUID> ids) {}

    /**
     * Una macchina senza servizio («servizioId» vuoto, tipo SERVIZIO) ha perso il suo servizio: non lascia partire nessuno.
     * «eliminabile», «motivoBlocco» e «utilizzi» come per il servizio.
     */
    record DispositivoDto(UUID id, String nome, String tipo, UUID servizioId, String servizio, boolean attivo, Instant creatoIl, Instant ultimoContatto,
                          long utilizziOggi, Boolean eliminabile, String motivoBlocco, Long utilizzi) {}

    record NuovoDispositivo(String nome, String tipo, UUID servizioId, Boolean attivo) {}

    /** La chiave si vede solo qui: va scritta nella configurazione della macchina. */
    record DispositivoCreato(DispositivoDto dispositivo, String chiave) {}

    record Lettura(String codice, String postazione) {}

    record Prova(String codice, String azione, String postazione) {}

    /**
     * La risposta alla macchina: «via» = può partire (phon, doccia); il messaggio è corto, da display; il nome è solo il
     * nome di battesimo (dati di minori: niente cognome sul display).
     */
    record Risposta(boolean via, String esito, String messaggio, String nome, Integer crediti, int creditiUsati, Integer minuti, BigDecimal euro) {}

    /** «dispositivoId» è vuoto se la macchina è stata eliminata: «dispositivo» e «servizio» sono i nomi di allora. */
    record UtilizzoDto(UUID id, Instant istante, UUID dispositivoId, String dispositivo, String servizio, String codice, UUID personaId, String persona,
                       String esito, int crediti, String postazione, boolean simulato) {}

    private static final Set<String> CONTANO = Set.of(Utilizzo.Esito.OK.name(), Utilizzo.Esito.GRATIS.name());

    private final ServizioRepository servizi;
    private final DispositivoRepository dispositivi;
    private final UtilizzoRepository utilizzi;
    private final Braccialetti braccialetti;
    private final Borsellino borsellino;
    private final Persone persone;
    private final Clock orologio;
    private final SecureRandom caso = new SecureRandom();

    ServiziService(ServizioRepository servizi, DispositivoRepository dispositivi, UtilizzoRepository utilizzi, Braccialetti braccialetti,
                   Borsellino borsellino, Persone persone, Clock orologio) {
        this.servizi = servizi;
        this.dispositivi = dispositivi;
        this.utilizzi = utilizzi;
        this.braccialetti = braccialetti;
        this.borsellino = borsellino;
        this.persone = persone;
        this.orologio = orologio;
    }

    // ── Le macchine ─────────────────────────────────────────────────────────

    /** Una macchina dice chi è con la chiave; se la chiave non va, null (risposta 401). */
    @Transactional
    Dispositivo autentica(UUID id, String chiave) {
        if (chiave == null || chiave.isBlank()) return null;
        Dispositivo d = dispositivi.findById(id).orElse(null);
        if (d == null || !MessageDigest.isEqual(d.getChiaveHash().getBytes(StandardCharsets.US_ASCII), impronta(chiave).getBytes(StandardCharsets.US_ASCII))) return null;
        d.contatto(orologio.instant());
        return d;
    }

    /** Il braccialetto passato su una macchina: si può usare il servizio? Se sì, scala i crediti (o è gratis). */
    @Transactional
    Risposta usa(Dispositivo d, Lettura in, boolean simulato) {
        if (d.tipo() != Dispositivo.Tipo.SERVIZIO) return saldo(d, in.codice());
        String codice = braccialetti.normalizza(in.codice());
        Instant ora = orologio.instant();
        UUID id = UUID.randomUUID();
        // il servizio della macchina è stato eliminato: non scala crediti e non lascia partire nessuno (esito «servizio spento»)
        if (d.getServizioId() == null) return registra(new Utilizzo(id, d, null, codice, null, ora, Utilizzo.Esito.SERVIZIO_SPENTO, 0, in.postazione(), simulato),
            "Macchina non configurata", null, null, null);
        Servizio s = servizi.findById(d.getServizioId()).orElseThrow();
        if (!d.isAttivo() || !s.isAttivo()) return registra(new Utilizzo(id, d, s, codice, null, ora, Utilizzo.Esito.SERVIZIO_SPENTO, 0, in.postazione(), simulato),
            "Servizio non disponibile", null, null, s);
        var t = braccialetti.titolare(in.codice());
        if (t.isEmpty()) return registra(new Utilizzo(id, d, s, codice, null, ora, Utilizzo.Esito.SCONOSCIUTO, 0, in.postazione(), simulato),
            "Braccialetto non registrato: chiedi in segreteria", null, null, s);
        var tit = t.get();
        if (!tit.attivo()) return registra(new Utilizzo(id, d, s, codice, tit.personaId(), ora, Utilizzo.Esito.NON_ATTIVO, 0, in.postazione(), simulato),
            "Braccialetto non attivo: chiedi in segreteria", tit.nome(), null, s);
        long oggi = utilizzi.contaDal(tit.personaId(), s.getId(), inizioGiorno(), CONTANO);
        if (oggi < s.getGratuitiAlGiorno() || s.getCrediti() == 0) {
            int residui = borsellino.saldo(tit.personaId()).crediti();
            return registra(new Utilizzo(id, d, s, codice, tit.personaId(), ora, Utilizzo.Esito.GRATIS, 0, in.postazione(), simulato),
                s.getCrediti() == 0 ? s.getNome() + " gratis" : "Oggi " + s.getNome().toLowerCase() + " gratis", tit.nome(), residui, s);
        }
        String descr = (d.getNome().toLowerCase().contains(s.getNome().toLowerCase()) ? "" : s.getNome() + " · ") + d.getNome()
            + (in.postazione() == null || in.postazione().isBlank() ? "" : " " + in.postazione().strip());
        var c = borsellino.consuma(tit.personaId(), s.getCrediti(), descr, id);
        if (!c.fatto()) return registra(new Utilizzo(id, d, s, codice, tit.personaId(), ora, Utilizzo.Esito.CREDITI_FINITI, 0, in.postazione(), simulato),
            "Crediti finiti: ricarica in segreteria", tit.nome(), c.residui(), s);
        return registra(new Utilizzo(id, d, s, codice, tit.personaId(), ora, Utilizzo.Esito.OK, s.getCrediti(), in.postazione(), simulato),
            s.getNome() + " · restano " + c.residui() + " crediti", tit.nome(), c.residui(), s);
    }

    /** Solo il saldo (lettore all'ingresso, o il display di una macchina prima di scegliere). */
    @Transactional(readOnly = true)
    Risposta saldo(Dispositivo d, String codice) {
        // una macchina spenta (di qualunque tipo) o senza servizio non mostra niente: né nome né crediti né euro
        if (!d.isAttivo())
            return new Risposta(false, Utilizzo.Esito.SERVIZIO_SPENTO.name(),
                d.tipo() == Dispositivo.Tipo.SALDO ? "Lettore non disponibile" : "Servizio non disponibile", null, null, 0, null, null);
        if (d.tipo() == Dispositivo.Tipo.SERVIZIO && d.getServizioId() == null)
            return new Risposta(false, Utilizzo.Esito.SERVIZIO_SPENTO.name(), "Macchina non configurata", null, null, 0, null, null);
        var t = braccialetti.titolare(codice);
        if (t.isEmpty()) return new Risposta(false, Utilizzo.Esito.SCONOSCIUTO.name(), "Braccialetto non registrato", null, null, 0, null, null);
        var s = borsellino.saldo(t.get().personaId());
        return new Risposta(false, t.get().attivo() ? "SALDO" : Utilizzo.Esito.NON_ATTIVO.name(),
            t.get().attivo() ? "Crediti: " + s.crediti() + (s.euro().signum() > 0 ? " · credito " + Importi.testo(s.euro()).replace('\u00a0', ' ') : "") : "Braccialetto non attivo",
            t.get().nome(), s.crediti(), 0, null, s.euro());
    }

    private Risposta registra(Utilizzo u, String messaggio, String nome, Integer crediti, Servizio s) {
        utilizzi.saveAndFlush(u);
        boolean via = u.esito() == Utilizzo.Esito.OK || u.esito() == Utilizzo.Esito.GRATIS;
        return new Risposta(via, u.esito().name(), messaggio, nome, crediti, u.getCrediti(), via && s != null ? s.getMinuti() : null, null);
    }

    /** Il simulatore: come una macchina vera, ma da un utente collegato (gli utilizzi restano segnati come simulati). */
    @Transactional
    Risposta prova(UUID dispositivoId, Prova in) {
        Dispositivo d = dispositivi.findById(dispositivoId).orElseThrow(() -> new NonTrovato("Dispositivo non trovato."));
        if (in.codice() == null || in.codice().isBlank()) throw new IllegalArgumentException("Scrivi o leggi il codice del braccialetto.");
        return "SALDO".equals(in.azione()) ? saldo(d, in.codice()) : usa(d, new Lettura(in.codice(), in.postazione()), true);
    }

    // ── Gestione ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    List<ServizioDto> servizi(boolean conBlocchi) {
        Map<UUID, Long> usi = conBlocchi ? conteggi(utilizzi.contaPerServizio()) : Map.of();
        Map<UUID, Long> macchine = conBlocchi ? conteggi(dispositivi.contaPerServizio()) : Map.of();
        return servizi.findAllByOrderByOrdineAscNomeAsc().stream()
            .map(s -> dto(s, conBlocchi, usi.getOrDefault(s.getId(), 0L), macchine.getOrDefault(s.getId(), 0L))).toList();
    }

    @Transactional
    ServizioDto creaServizio(NuovoServizio in) {
        int ordine = servizi.findAll().stream().mapToInt(Servizio::getOrdine).max().orElse(-1) + 1;
        return dtoCompleto(servizi.saveAndFlush(new Servizio(in, ordine)));
    }

    @Transactional
    ServizioDto modificaServizio(UUID id, NuovoServizio in) {
        Servizio s = servizi.findById(id).orElseThrow(() -> new NonTrovato("Servizio non trovato."));
        s.modifica(in);
        return dtoCompleto(servizi.saveAndFlush(s));
    }

    /** Il nuovo ordine dell'elenco: vanno elencati tutti i servizi, una volta sola. Risponde con l'elenco aggiornato. */
    @Transactional
    List<ServizioDto> ordinaServizi(Ordine in) {
        List<Servizio> tutti = servizi.findAll();
        List<UUID> ids = in == null || in.ids() == null ? List.of() : in.ids();
        if (ids.size() != tutti.size() || new HashSet<>(ids).size() != ids.size() || !tutti.stream().map(Servizio::getId).allMatch(ids::contains))
            throw new IllegalArgumentException("L'ordine deve contenere, una volta sola, tutti i servizi.");
        Map<UUID, Servizio> perId = tutti.stream().collect(Collectors.toMap(Servizio::getId, x -> x));
        for (int i = 0; i < ids.size(); i++) perId.get(ids.get(i)).ordina(i);
        servizi.flush();
        return servizi(true);
    }

    /**
     * Un servizio si elimina sempre (ADR 0030): gli utilizzi tengono la copia del nome e le sue macchine restano senza
     * servizio. Non c'è niente «in corso» che lo fermi: gli utilizzi sono registrazioni di fatti già avvenuti.
     */
    @Transactional
    Eliminato eliminaServizio(UUID id) {
        Servizio s = servizi.findById(id).orElseThrow(() -> new NonTrovato("Servizio non trovato."));
        Eliminato eliminato = new Eliminato(utilizzi.countByServizioId(id), dispositivi.countByServizioId(id));
        servizi.delete(s);
        servizi.flush();
        int n = 0;
        for (Servizio x : servizi.findAllByOrderByOrdineAscNomeAsc()) x.ordina(n++);
        return eliminato;
    }

    @Transactional(readOnly = true)
    List<DispositivoDto> dispositivi(boolean conBlocchi) {
        Map<UUID, String> nomi = nomiServizi();
        Map<UUID, Long> oggi = new HashMap<>();
        utilizzi.perDispositivoDal(inizioGiorno()).forEach(r -> oggi.put((UUID) r[0], (Long) r[1]));
        Map<UUID, Long> usi = conBlocchi ? conteggi(utilizzi.contaPerDispositivo()) : Map.of();
        return dispositivi.perTipoENome().stream()
            .map(d -> dto(d, nomi, oggi.getOrDefault(d.getId(), 0L), conBlocchi, usi.getOrDefault(d.getId(), 0L))).toList();
    }

    @Transactional
    DispositivoCreato creaDispositivo(NuovoDispositivo in) {
        Dispositivo.Tipo tipo = tipo(in.tipo());
        if (tipo == Dispositivo.Tipo.SERVIZIO && in.servizioId() == null) throw new IllegalArgumentException("Scegli il servizio della macchina (es. phon).");
        String chiave = nuovaChiave();
        Dispositivo d = new Dispositivo(in.nome(), tipo, servizio(in), in.attivo() == null || in.attivo(), impronta(chiave), orologio.instant());
        dispositivi.saveAndFlush(d);
        return new DispositivoCreato(dtoCompleto(d), chiave);
    }

    @Transactional
    DispositivoDto modificaDispositivo(UUID id, NuovoDispositivo in) {
        Dispositivo d = dispositivi.findById(id).orElseThrow(() -> new NonTrovato("Dispositivo non trovato."));
        Dispositivo.Tipo tipo = in.tipo() == null ? d.tipo() : tipo(in.tipo());
        if (tipo != d.tipo()) throw new IllegalArgumentException("Il tipo di una macchina non si cambia: creane una nuova.");
        d.modifica(in.nome(), tipo, in.servizioId() != null ? servizio(in) : d.getServizioId(), in.attivo() == null ? d.isAttivo() : in.attivo());
        return dtoCompleto(dispositivi.saveAndFlush(d));
    }

    /**
     * Una macchina si elimina sempre (ADR 0030), anche con utilizzi veri o simulati: ognuno tiene la copia del nome. Il
     * lettore del saldo non scrive utilizzi. Non c'è niente «in corso» che la fermi.
     */
    @Transactional
    Eliminato eliminaDispositivo(UUID id) {
        Dispositivo d = dispositivi.findById(id).orElseThrow(() -> new NonTrovato("Dispositivo non trovato."));
        Eliminato eliminato = new Eliminato(utilizzi.countByDispositivoId(id), null);
        dispositivi.delete(d);
        dispositivi.flush();
        return eliminato;
    }

    /** Chiave nuova (es. la vecchia è finita in mani sbagliate): la macchina va riconfigurata. */
    @Transactional
    DispositivoCreato nuovaChiave(UUID id) {
        Dispositivo d = dispositivi.findById(id).orElseThrow(() -> new NonTrovato("Dispositivo non trovato."));
        String chiave = nuovaChiave();
        d.nuovaChiave(impronta(chiave));
        return new DispositivoCreato(dtoCompleto(dispositivi.saveAndFlush(d)), chiave);
    }

    @Transactional(readOnly = true)
    List<UtilizzoDto> utilizzi(UUID personaId, UUID dispositivoId, int quanti) {
        var pagina = PageRequest.of(0, Math.clamp(quanti, 1, 200));
        var lista = personaId != null ? utilizzi.findByPersonaIdOrderByIstanteDesc(personaId, pagina)
            : dispositivoId != null ? utilizzi.findByDispositivoIdOrderByIstanteDesc(dispositivoId, pagina) : utilizzi.findAllByOrderByIstanteDesc(pagina);
        Set<UUID> ids = new HashSet<>();
        lista.forEach(u -> { if (u.getPersonaId() != null) ids.add(u.getPersonaId()); });
        Map<UUID, String> nomi = ids.isEmpty() ? Map.of() : persone.nomiCompleti(ids);
        // i nomi della macchina e del servizio sono la copia presa all'utilizzo: reggono anche se poi sono stati eliminati
        return lista.stream().map(u -> new UtilizzoDto(u.getId(), u.getIstante(), u.getDispositivoId(), u.getDispositivoNome(),
            u.getServizioNome(), u.getCodice(), u.getPersonaId(), u.getPersonaId() == null ? null : nomi.get(u.getPersonaId()),
            u.esito().name(), u.getCrediti(), u.getPostazione(), u.isSimulato())).toList();
    }

    // ── Aiuti ───────────────────────────────────────────────────────────────

    private ServizioDto dto(Servizio s, boolean conBlocchi, long usi, long macchine) {
        return new ServizioDto(s.getId(), s.getNome(), s.getCrediti(), s.getGratuitiAlGiorno(), s.getMinuti(), s.isAttivo(), s.getOrdine(),
            conBlocchi ? true : null, null, conBlocchi ? usi : null, conBlocchi ? macchine : null);
    }

    /** Per le risposte a chi modifica (sempre con IMPOSTAZIONI): con i numeri veri. */
    private ServizioDto dtoCompleto(Servizio s) {
        return dto(s, true, utilizzi.countByServizioId(s.getId()), dispositivi.countByServizioId(s.getId()));
    }

    private DispositivoDto dto(Dispositivo d, Map<UUID, String> servizi, long oggi, boolean conBlocchi, long usi) {
        return new DispositivoDto(d.getId(), d.getNome(), d.tipo().name(), d.getServizioId(), d.getServizioId() == null ? null : servizi.get(d.getServizioId()),
            d.isAttivo(), d.getCreatoIl(), d.getUltimoContatto(), oggi, conBlocchi ? true : null, null, conBlocchi ? usi : null);
    }

    private DispositivoDto dtoCompleto(Dispositivo d) {
        return dto(d, nomiServizi(), utilizzi.countByDispositivoIdAndIstanteGreaterThanEqual(d.getId(), inizioGiorno()), true, utilizzi.countByDispositivoId(d.getId()));
    }

    private Instant inizioGiorno() {
        return LocalDate.now(orologio).atStartOfDay(FUSO).toInstant();
    }

    private static Map<UUID, Long> conteggi(List<Object[]> righe) {
        Map<UUID, Long> m = new HashMap<>();
        righe.forEach(r -> m.put((UUID) r[0], (Long) r[1]));
        return m;
    }

    private Map<UUID, String> nomiServizi() {
        return servizi.findAll().stream().collect(Collectors.toMap(Servizio::getId, Servizio::getNome));
    }

    private static Dispositivo.Tipo tipo(String t) {
        try {
            return Dispositivo.Tipo.valueOf(String.valueOf(t));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo di dispositivo: SERVIZIO (phon, docce) o SALDO (mostra i crediti).");
        }
    }

    private UUID servizio(NuovoDispositivo in) {
        if (in.servizioId() == null) return null;
        return servizi.findById(in.servizioId()).map(Servizio::getId).orElseThrow(() -> new NonTrovato("Servizio non trovato."));
    }

    private String nuovaChiave() {
        byte[] b = new byte[24];
        caso.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** SHA-256 in esadecimale: in archivio c'è solo questa, non la chiave. */
    static String impronta(String chiave) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chiave.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
