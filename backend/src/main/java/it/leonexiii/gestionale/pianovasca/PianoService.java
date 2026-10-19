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

    record SpazioDto(UUID id, UUID impiantoId, String nome, String tipo, UUID padreId, Integer assetto) {}

    record Occorrenza(String chiave, String tipo, String categoria, String colore, String titolo, String dettaglio,
                      LocalDate data, LocalTime inizio, LocalTime fine, List<UUID> spazi, PersonaBreve istruttore,
                      Stato stato, String nota, UUID lezioneId, UUID corsoId, UUID eventoId, UUID eccezioneId,
                      LocalDate dataOriginale, List<String> conflitti, Integer iscritti, Integer posti) {

        boolean attiva() {
            return stato == Stato.REGOLARE || stato == Stato.SPOSTATA_QUI;
        }

        Occorrenza conConflitti(List<String> c) {
            return new Occorrenza(chiave, tipo, categoria, colore, titolo, dettaglio, data, inizio, fine, spazi, istruttore, stato, nota,
                lezioneId, corsoId, eventoId, eccezioneId, dataOriginale, c, iscritti, posti);
        }

        Occorrenza conIscritti(int n) {
            return new Occorrenza(chiave, tipo, categoria, colore, titolo, dettaglio, data, inizio, fine, spazi, istruttore, stato, nota,
                lezioneId, corsoId, eventoId, eccezioneId, dataOriginale, conflitti, n, posti);
        }
    }

    /**
     * impiantiGestiti: dove l'utente modifica il piano; impiantiIscrizioni: dove gestisce le iscrizioni (segreteria,
     * coordinatori); fasce: le fasce orarie definite, valide nel periodo.
     */
    record Piano(LocalDate da, LocalDate a, List<SpazioDto> spazi, List<FasceService.FasciaDto> fasce, List<Occorrenza> occorrenze,
                 List<UUID> impiantiGestiti, List<UUID> impiantiIscrizioni) {}

    private final LezioneRepository lezioni;
    private final EventoRepository eventi;
    private final Impianti impianti;
    private final Persone persone;
    private final AccessoPiano accesso;
    private final IscrizioneRepository iscrizioni;
    private final FasceService fasce;

    PianoService(LezioneRepository lezioni, EventoRepository eventi, Impianti impianti, Persone persone, AccessoPiano accesso,
                 IscrizioneRepository iscrizioni, FasceService fasce) {
        this.iscrizioni = iscrizioni;
        this.fasce = fasce;
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
        if (visibili.isEmpty()) return new Piano(da, a, List.of(), List.of(), List.of(), List.of(), List.of());
        List<SpazioDto> spazi = impianti.spazi(visibili).stream()
            .map(PianoService::dto).toList();
        List<Occorrenza> occ = conIscritti(calcola(visibili, spazi, da, a), da, a);
        return new Piano(da, a, spazi, fasce.fasce(u, da, a), occ, List.copyOf(accesso.gestiti(u)), List.copyOf(accesso.impiantiIscrizioni(u)));
    }

    /** Quanti iscritti attivi ha ogni turno di corso nella sua data. */
    private List<Occorrenza> conIscritti(List<Occorrenza> occ, LocalDate da, LocalDate a) {
        List<UUID> ids = occ.stream().map(Occorrenza::lezioneId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return occ;
        Map<UUID, List<Iscrizione>> perLezione = new HashMap<>();
        iscrizioni.nelPeriodo(ids, da, a).forEach(i -> perLezione.computeIfAbsent(i.getLezioneId(), k -> new ArrayList<>()).add(i));
        return occ.stream().map(o -> o.lezioneId() == null ? o
            : o.conIscritti((int) perLezione.getOrDefault(o.lezioneId(), List.of()).stream().filter(i -> i.attivaIl(o.data())).count())).toList();
    }

    static SpazioDto dto(Impianti.SpazioBreve s) {
        return new SpazioDto(s.id(), s.impiantoId(), s.nome(), s.tipo(), s.padreId(), s.assetto());
    }

    /** Come si incastrano gli spazi: la vasca di ogni corsia e l'assetto della corsia. */
    record Mappa(Map<UUID, UUID> padre, Map<UUID, Integer> assetto) {
        static Mappa di(List<SpazioDto> spazi) {
            Map<UUID, UUID> p = new HashMap<>();
            Map<UUID, Integer> a = new HashMap<>();
            for (SpazioDto s : spazi) {
                if (s.padreId() != null) p.put(s.id(), s.padreId());
                if (s.assetto() != null) a.put(s.id(), s.assetto());
            }
            return new Mappa(p, a);
        }
    }

    /** Le occorrenze del periodo, con stato e sovrapposizioni. */
    List<Occorrenza> calcola(Set<UUID> impiantiIds, List<SpazioDto> spazi, LocalDate da, LocalDate a) {
        List<Lezione> tutteLezioni = lezioni.deiCorsiAttivi(impiantiIds);
        List<Evento> tuttiEventi = eventi.nelPeriodo(impiantiIds, da, a);
        Mappa padre = Mappa.di(spazi);

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
                    nota = "Spostata a " + quando(ecc.getNuovaData(), ecc.getNuovoInizio()) + (ecc.getMotivo() == null ? "" : " · " + ecc.getMotivo());
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
                    ch != null ? Stato.CHIUSO : Stato.SPOSTATA_QUI, ch != null ? ch.getTitolo() : "Spostata da " + quando(e.getData(), l.getInizio()) + (e.getMotivo() == null ? "" : " · " + e.getMotivo()),
                    e.getId(), e.getData()));
            }
        }

        for (Evento e : tuttiEventi) {
            PersonaBreve istruttore = e.getIstruttoreId() == null ? null : new PersonaBreve(e.getIstruttoreId(), nomi.getOrDefault(e.getIstruttoreId(), "?"));
            List<UUID> spaziEvento = e.getSpazi().stream().sorted().toList();
            for (LocalDate g = da.isBefore(e.getData()) ? e.getData() : da; !g.isAfter(a) && e.occupa(g); g = g.plusDays(1)) {
                out.add(new Occorrenza("E:" + e.getId() + ":" + g, "EVENTO", e.getTipo().name(), null, e.getTitolo(), e.getNote(), g,
                    e.getInizio(), e.getFine(), spaziEvento, istruttore, e.isAnnullato() ? Stato.ANNULLATO : Stato.REGOLARE, null,
                    null, null, e.getId(), null, null, List.of(), null, null));
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
        return new Occorrenza(chiave, "LEZIONE", c.getTipo().name(), c.getColore(), c.getNome(), dettaglio, g, inizio, fine, spazi, istruttore, stato, nota,
            l.getId(), c.getId(), null, eccezioneId, originale, List.of(), null, l.getPosti());
    }

    private static final java.time.format.DateTimeFormatter GIORNO = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALIAN);

    /** «lunedì 19 ottobre alle 19:00» */
    static String quando(LocalDate data, LocalTime ora) {
        return data.format(GIORNO) + " alle " + ora;
    }

    static String eta(Integer da, Integer a) {
        if (da == null && a == null) return null;
        if (a == null) return "dai " + da + " anni";
        if (da == null) return "fino a " + a + " anni";
        return da + "–" + a + " anni";
    }

    /**
     * Due insiemi di spazi si toccano se hanno uno spazio in comune, se uno contiene l'altro (vasca e sua corsia),
     * o se sono corsie della stessa vasca in assetti diversi (a 6 e a 5 corsie insieme non si può).
     */
    static boolean spaziToccati(List<UUID> a, List<UUID> b, Mappa m) {
        for (UUID x : a) for (UUID y : b) {
            if (x.equals(y) || x.equals(m.padre().get(y)) || y.equals(m.padre().get(x))) return true;
            UUID px = m.padre().get(x);
            Integer ax = m.assetto().get(x), ay = m.assetto().get(y);
            if (px != null && px.equals(m.padre().get(y)) && ax != null && ay != null && !ax.equals(ay)) return true;
        }
        return false;
    }

    /** La chiusura che sospende questa occupazione (tutto l'impianto, oppure gli stessi spazi), se c'è. */
    private static Evento chiusuraSu(List<Evento> chiusure, UUID impiantoId, LocalDate g, LocalTime inizio, LocalTime fine, Set<UUID> spazi, Mappa padre) {
        for (Evento ch : chiusure) {
            if (!ch.getImpiantoId().equals(impiantoId) || !ch.occupa(g) || !ch.getInizio().isBefore(fine) || !inizio.isBefore(ch.getFine())) continue;
            if (ch.getSpazi().isEmpty() || spaziToccati(List.copyOf(ch.getSpazi()), List.copyOf(spazi), padre)) return ch;
        }
        return null;
    }
}
