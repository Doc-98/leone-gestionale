package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il piano vasca di un periodo: le lezioni ricorrenti espanse giorno per giorno (con salti e spostamenti),
 * gli eventi, le chiusure che sospendono le lezioni, e le sovrapposizioni sugli stessi spazi.
 */
@Service
class PianoService {

    static final long GIORNI_MASSIMI = 62;

    enum Stato {
        /** Si tiene come previsto. */
        REGOLARE,
        /** Lezione spostata qui da un altro giorno o orario. */
        SPOSTATA_QUI,
        /** Lezione che oggi non si tiene: saltata, spostata altrove, o sospesa per una chiusura. */
        SALTATA, SPOSTATA_VIA, CHIUSO,
        /** Evento annullato. */
        ANNULLATO
    }

    record SpazioDto(UUID id, UUID impiantoId, String nome, String tipo, UUID padreId) {}

    record Occorrenza(String chiave, String tipo, String categoria, String titolo, String dettaglio,
                      LocalDate data, LocalTime inizio, LocalTime fine, List<UUID> spazi, PersonaBreve istruttore,
                      Stato stato, String nota, UUID lezioneId, UUID corsoId, UUID eventoId, UUID eccezioneId,
                      LocalDate dataOriginale, List<String> conflitti) {

        boolean attiva() {
            return stato == Stato.REGOLARE || stato == Stato.SPOSTATA_QUI;
        }

        Occorrenza conConflitti(List<String> c) {
            return new Occorrenza(chiave, tipo, categoria, titolo, dettaglio, data, inizio, fine, spazi, istruttore, stato, nota,
                lezioneId, corsoId, eventoId, eccezioneId, dataOriginale, c);
        }
    }

    record Piano(LocalDate da, LocalDate a, List<SpazioDto> spazi, List<Occorrenza> occorrenze, List<UUID> impiantiGestiti) {}

    private final LezioneRepository lezioni;
    private final EventoRepository eventi;
    private final Impianti impianti;
    private final Persone persone;
    private final AccessoPiano accesso;

    PianoService(LezioneRepository lezioni, EventoRepository eventi, Impianti impianti, Persone persone, AccessoPiano accesso) {
        this.lezioni = lezioni;
        this.eventi = eventi;
        this.impianti = impianti;
        this.persone = persone;
        this.accesso = accesso;
    }

    @Transactional(readOnly = true)
    Piano piano(Utente u, LocalDate da, LocalDate a) {
        if (a.isBefore(da)) throw new IllegalArgumentException("La data finale è prima di quella iniziale.");
        if (ChronoUnit.DAYS.between(da, a) > GIORNI_MASSIMI) throw new IllegalArgumentException("Intervallo troppo lungo: al massimo due mesi.");
        Set<UUID> visibili = accesso.visibili(u);
        if (visibili.isEmpty()) return new Piano(da, a, List.of(), List.of(), List.of());
        List<SpazioDto> spazi = impianti.spazi(visibili).stream()
            .map(s -> new SpazioDto(s.id(), s.impiantoId(), s.nome(), s.tipo(), s.padreId())).toList();
        List<Occorrenza> occ = calcola(visibili, spazi, da, a);
        return new Piano(da, a, spazi, occ, List.copyOf(accesso.gestiti(u)));
    }

    /** Le occorrenze del periodo, con stato e sovrapposizioni. */
    List<Occorrenza> calcola(Set<UUID> impiantiIds, List<SpazioDto> spazi, LocalDate da, LocalDate a) {
        List<Lezione> tutteLezioni = lezioni.deiCorsiAttivi(impiantiIds);
        List<Evento> tuttiEventi = eventi.nelPeriodo(impiantiIds, da, a);
        Map<UUID, UUID> padre = new HashMap<>();
        spazi.forEach(s -> { if (s.padreId() != null) padre.put(s.id(), s.padreId()); });

        Map<UUID, String> nomi = persone.nomiCompleti(Stream.concat(
                tutteLezioni.stream().flatMap(l -> Stream.of(l.getIstruttoreId(), l.getCorso().getIstruttoreId())),
                tuttiEventi.stream().map(Evento::getIstruttoreId))
            .filter(Objects::nonNull).distinct().toList());

        List<Evento> chiusure = tuttiEventi.stream().filter(e -> e.getTipo() == TipoEvento.CHIUSURA && !e.isAnnullato()).toList();
        List<Occorrenza> out = new ArrayList<>();

        for (Lezione l : tutteLezioni) {
            Corso c = l.getCorso();
            UUID istr = l.getIstruttoreId() != null ? l.getIstruttoreId() : c.getIstruttoreId();
            PersonaBreve istruttore = istr == null ? null : new PersonaBreve(istr, nomi.getOrDefault(istr, "?"));
            List<UUID> spaziLezione = l.getSpazi().stream().sorted().toList();
            for (LocalDate g = da; !g.isAfter(a); g = g.plusDays(1)) {
                if (!l.cadeIl(g)) continue;
                var ecc = l.eccezioneIl(g).orElse(null);
                Stato stato = Stato.REGOLARE;
                String nota = null;
                if (ecc != null && ecc.getTipo() == TipoEccezione.SALTATA) {
                    stato = Stato.SALTATA;
                    nota = ecc.getMotivo();
                } else if (ecc != null) {
                    stato = Stato.SPOSTATA_VIA;
                    nota = "Spostata a " + ecc.getNuovaData() + " " + ecc.getNuovoInizio() + (ecc.getMotivo() == null ? "" : " · " + ecc.getMotivo());
                } else {
                    Evento ch = chiusuraSu(chiusure, c.getImpiantoId(), g, l.getInizio(), l.getFine(), l.getSpazi(), padre);
                    if (ch != null) {
                        stato = Stato.CHIUSO;
                        nota = ch.getTitolo();
                    }
                }
                out.add(lezione(l, c, g, l.getInizio(), l.getFine(), spaziLezione, istruttore, stato, nota, ecc == null ? null : ecc.getId(), null));
            }
            // spostate dentro il periodo da un giorno qualsiasi
            for (Eccezione e : l.getEccezioni()) {
                if (e.getTipo() != TipoEccezione.SPOSTATA || e.getNuovaData().isBefore(da) || e.getNuovaData().isAfter(a)) continue;
                Evento ch = chiusuraSu(chiusure, c.getImpiantoId(), e.getNuovaData(), e.getNuovoInizio(), e.getNuovoFine(), l.getSpazi(), padre);
                out.add(lezione(l, c, e.getNuovaData(), e.getNuovoInizio(), e.getNuovoFine(), spaziLezione, istruttore,
                    ch != null ? Stato.CHIUSO : Stato.SPOSTATA_QUI, ch != null ? ch.getTitolo() : "Spostata dal " + e.getData(), e.getId(), e.getData()));
            }
        }

        for (Evento e : tuttiEventi) {
            PersonaBreve istruttore = e.getIstruttoreId() == null ? null : new PersonaBreve(e.getIstruttoreId(), nomi.getOrDefault(e.getIstruttoreId(), "?"));
            List<UUID> spaziEvento = e.getSpazi().stream().sorted().toList();
            for (LocalDate g = da.isBefore(e.getData()) ? e.getData() : da; !g.isAfter(a) && e.occupa(g); g = g.plusDays(1)) {
                out.add(new Occorrenza("E:" + e.getId() + ":" + g, "EVENTO", e.getTipo().name(), e.getTitolo(), e.getNote(), g,
                    e.getInizio(), e.getFine(), spaziEvento, istruttore, e.isAnnullato() ? Stato.ANNULLATO : Stato.REGOLARE, null,
                    null, null, e.getId(), null, null, List.of()));
            }
        }

        // sovrapposizioni tra occorrenze attive (le chiusure non contano: sospendono, non si sovrappongono)
        Map<LocalDate, List<Integer>> perGiorno = new HashMap<>();
        for (int i = 0; i < out.size(); i++) {
            Occorrenza o = out.get(i);
            if (o.attiva() && !"CHIUSURA".equals(o.categoria()) && !o.spazi().isEmpty())
                perGiorno.computeIfAbsent(o.data(), k -> new ArrayList<>()).add(i);
        }
        Map<Integer, List<String>> conflitti = new HashMap<>();
        for (List<Integer> indici : perGiorno.values()) {
            for (int x = 0; x < indici.size(); x++) for (int y = x + 1; y < indici.size(); y++) {
                Occorrenza p = out.get(indici.get(x)), q = out.get(indici.get(y));
                if (p.inizio().isBefore(q.fine()) && q.inizio().isBefore(p.fine()) && spaziToccati(p.spazi(), q.spazi(), padre)) {
                    conflitti.computeIfAbsent(indici.get(x), k -> new ArrayList<>()).add(q.titolo() + " " + q.inizio() + "–" + q.fine());
                    conflitti.computeIfAbsent(indici.get(y), k -> new ArrayList<>()).add(p.titolo() + " " + p.inizio() + "–" + p.fine());
                }
            }
        }
        conflitti.forEach((i, c) -> out.set(i, out.get(i).conConflitti(List.copyOf(c))));

        out.sort(Comparator.comparing(Occorrenza::data).thenComparing(Occorrenza::inizio).thenComparing(Occorrenza::titolo));
        return out;
    }

    private static Occorrenza lezione(Lezione l, Corso c, LocalDate g, LocalTime inizio, LocalTime fine, List<UUID> spazi,
                                      PersonaBreve istruttore, Stato stato, String nota, UUID eccezioneId, LocalDate originale) {
        String dettaglio = Stream.of(c.getLivello(), eta(c.getEtaDa(), c.getEtaA())).filter(Objects::nonNull).reduce((x, y) -> x + " · " + y).orElse(null);
        String chiave = "L:" + l.getId() + ":" + (originale != null ? originale + ">" : "") + g;
        return new Occorrenza(chiave, "LEZIONE", c.getTipo().name(), c.getNome(), dettaglio, g, inizio, fine, spazi, istruttore, stato, nota,
            l.getId(), c.getId(), null, eccezioneId, originale, List.of());
    }

    static String eta(Integer da, Integer a) {
        if (da == null && a == null) return null;
        if (a == null) return "dai " + da + " anni";
        if (da == null) return "fino a " + a + " anni";
        return da + "–" + a + " anni";
    }

    /** Due insiemi di spazi si toccano se hanno uno spazio in comune o se uno contiene l'altro (vasca e sua corsia). */
    static boolean spaziToccati(List<UUID> a, List<UUID> b, Map<UUID, UUID> padre) {
        for (UUID x : a) for (UUID y : b)
            if (x.equals(y) || x.equals(padre.get(y)) || y.equals(padre.get(x))) return true;
        return false;
    }

    /** La chiusura che sospende questa occupazione (tutto l'impianto, oppure gli stessi spazi), se c'è. */
    private static Evento chiusuraSu(List<Evento> chiusure, UUID impiantoId, LocalDate g, LocalTime inizio, LocalTime fine, Set<UUID> spazi, Map<UUID, UUID> padre) {
        for (Evento ch : chiusure) {
            if (!ch.getImpiantoId().equals(impiantoId) || !ch.occupa(g) || !ch.getInizio().isBefore(fine) || !inizio.isBefore(ch.getFine())) continue;
            if (ch.getSpazi().isEmpty() || spaziToccati(List.copyOf(ch.getSpazi()), List.copyOf(spazi), padre)) return ch;
        }
        return null;
    }
}
