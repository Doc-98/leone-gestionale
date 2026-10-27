package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La giornata vista dal banco del tornello: quanti sono entrati e quanti sono dentro, la linea dei passaggi, gli
 * ultimi passaggi con il corso di oggi di chi passa, chi è rimasto fuori e va sistemato, chi è stato sistemato.
 *
 * <p>«Da sistemare» è chi oggi ha preso un rosso in entrata e da allora non è più passato in verde (una persona o,
 * se nessuno lo porta, un braccialetto). Appena ripassa in verde diventa «sistemato».
 */
@Service
class GiornataAlBanco {

    /** Un segno sulla linea della giornata; per i rossi anche chi e perché (per l'etichetta). */
    record Tacca(UUID id, Instant istante, boolean verde, Verso verso, String chi, String motivo) {}

    /** dentro: entrati e non usciti; null se oggi nessuno è passato dall'uscita (allora non si sa). */
    record Conteggi(int entrati, int conCarnet, Integer dentro, int entrate, int uscite, int fermati) {}

    /** Chi è fuori: l'ultimo rosso (con i suoi motivi), da quando è fuori e quante volte ci ha provato. */
    record DaSistemare(UUID passaggioId, Instant istante, Instant primaVolta, int volte, String codice,
                       VerificaAccessi.PersonaEsito persona, List<Motivo> motivi) {}

    /** Chi era fuori e poi è passato in verde: i motivi del rosso, quando è stato fermato e quando è passato. */
    record Sistemato(UUID passaggioId, Instant fermatoIl, Instant passatoIl, String codice, VerificaAccessi.PersonaEsito persona, List<Motivo> motivi) {}

    /**
     * turni: per le persone degli ultimi passaggi e dei «da sistemare», i turni di oggi; arrivati: per ogni turno di
     * oggi, quante delle persone iscritte sono già entrate.
     */
    record Giornata(Conteggi conteggi, List<Tacca> linea, List<GestioneAccessiService.PassaggioDto> passaggi,
                    Map<UUID, List<Frequenze.TurnoAlBanco>> turni, Map<UUID, Integer> arrivati,
                    List<DaSistemare> daSistemare, List<Sistemato> sistemati) {}

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final PassaggioRepository passaggi;
    private final GestioneAccessiService gestione;
    private final Persone persone;
    private final Frequenze frequenze;
    private final Clock orologio;

    GiornataAlBanco(PassaggioRepository passaggi, GestioneAccessiService gestione, Persone persone, Frequenze frequenze, Clock orologio) {
        this.passaggi = passaggi;
        this.gestione = gestione;
        this.persone = persone;
        this.frequenze = frequenze;
        this.orologio = orologio;
    }

    @Transactional(readOnly = true)
    Giornata oggi(int quanti) {
        LocalDate oggi = LocalDate.now(orologio);
        Instant da = oggi.atStartOfDay(VerificaAccessi.FUSO).toInstant();
        Instant a = oggi.plusDays(1).atStartOfDay(VerificaAccessi.FUSO).toInstant();
        List<Passaggio> tutti = new ArrayList<>(passaggi.tra(da, a, PageRequest.of(0, 20_000)));
        tutti.sort(Comparator.comparing(Passaggio::getIstante)); // dal primo all'ultimo

        Set<UUID> ids = tutti.stream().map(Passaggio::getPersonaId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(ids);

        // ── conteggi ──
        Set<String> entrati = new HashSet<>();
        Map<UUID, Passaggio> ultimoVerde = new HashMap<>();
        int conCarnet = 0, entrate = 0, uscite = 0, fermati = 0;
        for (Passaggio p : tutti) {
            boolean entrata = p.verso() == Verso.ENTRATA;
            if (!entrata) uscite++;
            if (!p.isVerde()) { fermati++; continue; }
            if (p.getPersonaId() != null) ultimoVerde.put(p.getPersonaId(), p);
            if (entrata) {
                entrate++;
                entrati.add(chiave(p));
                if (p.getCarnetId() != null) conCarnet++;
            }
        }
        Integer dentro = uscite == 0 ? null : (int) ultimoVerde.values().stream().filter(p -> p.verso() == Verso.ENTRATA).count();

        // ── linea della giornata ──
        List<Tacca> linea = tutti.stream().map(p -> p.isVerde()
            ? new Tacca(p.getId(), p.getIstante(), true, p.verso(), null, null)
            : new Tacca(p.getId(), p.getIstante(), false, p.verso(), chi(p, schede), primoMotivo(p))).toList();

        // ── da sistemare e sistemati: per persona (o braccialetto), in ordine di tempo ──
        Map<String, List<Passaggio>> perChi = new LinkedHashMap<>();
        for (Passaggio p : tutti) perChi.computeIfAbsent(chiave(p), k -> new ArrayList<>()).add(p);
        List<DaSistemare> fuori = new ArrayList<>();
        List<Sistemato> sistemati = new ArrayList<>();
        for (List<Passaggio> suoi : perChi.values()) {
            Passaggio rosso = null, primo = null;
            int volte = 0;
            for (Passaggio p : suoi) {
                if (p.verso() != Verso.ENTRATA) continue;
                if (!p.isVerde()) {
                    if (rosso == null) primo = p;
                    rosso = p;
                    volte++;
                } else if (rosso != null) {
                    sistemati.add(new Sistemato(rosso.getId(), rosso.getIstante(), p.getIstante(), rosso.getCodice(), persona(rosso, schede, oggi), bloccanti(rosso)));
                    rosso = null;
                    volte = 0;
                }
            }
            if (rosso != null)
                fuori.add(new DaSistemare(rosso.getId(), rosso.getIstante(), primo.getIstante(), volte, rosso.getCodice(), persona(rosso, schede, oggi), bloccanti(rosso)));
        }
        fuori.sort(Comparator.comparing(DaSistemare::istante).reversed());
        sistemati.sort(Comparator.comparing(Sistemato::passatoIl).reversed());

        // ── ultimi passaggi, con i turni di oggi di chi passa ──
        List<Passaggio> ultimi = tutti.reversed().stream().limit(Math.clamp(quanti, 1, 200)).toList();
        Set<UUID> entratiPersone = tutti.stream().filter(p -> p.isVerde() && p.verso() == Verso.ENTRATA && p.getPersonaId() != null)
            .map(Passaggio::getPersonaId).collect(Collectors.toSet());
        Set<UUID> vedere = new HashSet<>();
        ultimi.forEach(p -> { if (p.getPersonaId() != null) vedere.add(p.getPersonaId()); });
        fuori.forEach(f -> { if (f.persona() != null) vedere.add(f.persona().id()); });
        Set<UUID> chiedere = new HashSet<>(entratiPersone);
        chiedere.addAll(vedere);
        Map<UUID, List<Frequenze.TurnoAlBanco>> turniDiTutti = frequenze.alBanco(chiedere, oggi);
        Map<UUID, Integer> arrivati = new HashMap<>();
        for (UUID p : entratiPersone)
            for (Frequenze.TurnoAlBanco t : turniDiTutti.getOrDefault(p, List.of())) arrivati.merge(t.lezioneId(), 1, Integer::sum);
        // anche i turni senza nessuno arrivato, se qualcuno li ha
        turniDiTutti.values().forEach(l -> l.forEach(t -> arrivati.putIfAbsent(t.lezioneId(), 0)));
        Map<UUID, List<Frequenze.TurnoAlBanco>> turni = new HashMap<>();
        vedere.forEach(id -> { if (turniDiTutti.containsKey(id)) turni.put(id, turniDiTutti.get(id)); });

        return new Giornata(new Conteggi(entrati.size(), conCarnet, dentro, entrate, uscite, fermati), linea, gestione.dto(ultimi),
            turni, arrivati, fuori, sistemati);
    }

    private static String chiave(Passaggio p) {
        return p.getPersonaId() != null ? p.getPersonaId().toString() : "braccialetto:" + p.getCodice();
    }

    private static List<Motivo> bloccanti(Passaggio p) {
        List<Motivo> b = p.getMotivi().stream().filter(Motivo::blocca).toList();
        return b.isEmpty() ? p.getMotivi() : b;
    }

    private static String primoMotivo(Passaggio p) {
        return bloccanti(p).stream().map(Motivo::messaggio).findFirst().orElse(null);
    }

    private static String chi(Passaggio p, Map<UUID, Persone.PersonaScheda> schede) {
        Persone.PersonaScheda s = p.getPersonaId() == null ? null : schede.get(p.getPersonaId());
        String nome = s == null ? "Braccialetto " + p.getCodice() : s.nome() + " " + s.cognome();
        return LocalDateTime.ofInstant(p.getIstante(), VerificaAccessi.FUSO).format(HH_MM) + " · " + nome;
    }

    private static VerificaAccessi.PersonaEsito persona(Passaggio p, Map<UUID, Persone.PersonaScheda> schede, LocalDate oggi) {
        Persone.PersonaScheda s = p.getPersonaId() == null ? null : schede.get(p.getPersonaId());
        if (s == null) return null;
        return new VerificaAccessi.PersonaEsito(s.id(), s.nome(), s.cognome(), s.dataNascita() == null ? null : Period.between(s.dataNascita(), oggi).getYears(), 0);
    }
}
