package it.leonexiii.gestionale.pianovasca;

import it.leonexiii.gestionale.condiviso.Conflitto;
import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.struttura.Impianti;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le fasce della giornata per vasca e giorno: orari liberi, ognuna con l'assetto delle corsie (5 o 6). Cambiando
 * l'assetto, i corsi della fascia si riportano sulle corsie con lo stesso numero; cambiando l'orario, i corsi che
 * avevano esattamente l'orario della fascia la seguono.
 */
@Service
class FasceService {

    private static final Pattern NUMERO = Pattern.compile("(\\d+)\\s*$");

    record FasciaDto(UUID id, UUID vascaId, int giornoSettimana, LocalTime inizio, LocalTime fine, Integer assetto,
                     LocalDate validaDal, LocalDate validaAl) {
        static FasciaDto di(FasciaOraria f) {
            return new FasciaDto(f.getId(), f.getVascaId(), f.getGiornoSettimana(), f.getInizio(), f.getFine(), f.getAssetto(),
                f.getValidaDal(), f.getValidaAl());
        }
    }

    record NuoveFasce(UUID vascaId, List<Integer> giorni, LocalTime inizio, LocalTime fine, Integer assetto, LocalDate validaDal, LocalDate validaAl) {}

    record ModificaFascia(LocalTime inizio, LocalTime fine, LocalDate validaDal, LocalDate validaAl) {}

    record NuovoAssetto(Integer assetto) {}

    /** Esito di una modifica: la fascia e quanti turni dei corsi sono stati spostati con lei. */
    record EsitoFascia(FasciaDto fascia, int turniAggiornati) {}

    private final FasciaRepository fasce;
    private final LezioneRepository lezioni;
    private final Impianti impianti;
    private final AccessoPiano accesso;

    FasceService(FasciaRepository fasce, LezioneRepository lezioni, Impianti impianti, AccessoPiano accesso) {
        this.fasce = fasce;
        this.lezioni = lezioni;
        this.impianti = impianti;
        this.accesso = accesso;
    }

    /** Le fasce delle vasche visibili, valide nel periodo (tutte, se il periodo manca). */
    @Transactional(readOnly = true)
    List<FasciaDto> fasce(Utente u, LocalDate da, LocalDate a) {
        Set<UUID> visibili = accesso.visibili(u);
        if (visibili.isEmpty()) return List.of();
        List<UUID> vasche = impianti.spazi(visibili).stream().filter(s -> s.padreId() == null).map(Impianti.SpazioBreve::id).toList();
        if (vasche.isEmpty()) return List.of();
        return fasce.findByVascaIdInOrderByGiornoSettimanaAscInizioAsc(vasche).stream()
            .filter(f -> da == null || f.valeTra(da, a)).map(FasciaDto::di).toList();
    }

    /** Una fascia uguale in più giorni della settimana (es. 17:00–17:45 il lunedì, martedì e giovedì). */
    @Transactional
    List<FasciaDto> crea(Utente u, NuoveFasce in) {
        Vasca v = vasca(u, in.vascaId());
        if (in.giorni() == null || in.giorni().isEmpty()) throw new IllegalArgumentException("Scegli almeno un giorno.");
        Integer assetto = v.assettoValido(in.assetto());
        List<FasciaDto> out = new ArrayList<>();
        for (Integer g : new LinkedHashSet<>(in.giorni())) {
            FasciaOraria f = fasce.save(new FasciaOraria(v.id(), g, in.inizio(), in.fine(), assetto, in.validaDal(), in.validaAl()));
            out.add(FasciaDto.di(f));
        }
        fasce.flush();
        return out;
    }

    @Transactional
    EsitoFascia modifica(Utente u, UUID id, ModificaFascia in) {
        FasciaOraria f = fascia(u, id);
        LocalTime vecchioInizio = f.getInizio(), vecchiaFine = f.getFine();
        f.orari(in.inizio(), in.fine(), in.validaDal(), in.validaAl());
        int spostati = 0;
        if (!vecchioInizio.equals(f.getInizio()) || !vecchiaFine.equals(f.getFine())) {
            for (Lezione l : dentro(f, vecchioInizio, vecchiaFine)) {
                if (l.getInizio().equals(vecchioInizio) && l.getFine().equals(vecchiaFine)) {
                    l.orario(f.getInizio(), f.getFine());
                    spostati++;
                }
            }
        }
        fasce.flush();
        return new EsitoFascia(FasciaDto.di(f), spostati);
    }

    /** Passa la fascia a un altro assetto (es. da 6 a 5 corsie): i corsi restano sulla corsia con lo stesso numero. */
    @Transactional
    EsitoFascia cambiaAssetto(Utente u, UUID id, NuovoAssetto in) {
        FasciaOraria f = fascia(u, id);
        Vasca v = vasca(u, f.getVascaId());
        Integer nuovo = v.assettoValido(in.assetto());
        if (nuovo.equals(f.getAssetto())) return new EsitoFascia(FasciaDto.di(f), 0);
        Map<Integer, UUID> corsieNuove = v.corsie(nuovo);
        List<String> senzaPosto = new ArrayList<>();
        Map<Lezione, Set<UUID>> spostamenti = new HashMap<>();
        for (Lezione l : dentro(f, f.getInizio(), f.getFine())) {
            Set<UUID> nuovi = new HashSet<>();
            for (UUID s : l.getSpazi()) {
                Integer numero = v.numero(s);
                if (numero == null) { nuovi.add(s); continue; }   // la vasca intera resta com'è
                UUID c = corsieNuove.get(numero);
                if (c == null) senzaPosto.add(l.getCorso().getNome() + " (corsia " + numero + ")");
                else nuovi.add(c);
            }
            spostamenti.put(l, nuovi);
        }
        if (!senzaPosto.isEmpty())
            throw new Conflitto("A " + nuovo + " corsie quelle corsie non esistono: prima sposta " + String.join(", ", senzaPosto) + ".");
        spostamenti.forEach(Lezione::spostaSu);
        f.assetto(nuovo);
        fasce.flush();
        return new EsitoFascia(FasciaDto.di(f), spostamenti.size());
    }

    /** Si elimina solo una fascia vuota; con dei corsi dentro va prima svuotata (o chiusa con «valida fino al»). */
    @Transactional
    void elimina(Utente u, UUID id) {
        FasciaOraria f = fascia(u, id);
        List<Lezione> qui = dentro(f, f.getInizio(), f.getFine());
        if (!qui.isEmpty())
            throw new Conflitto("Nella fascia ci sono " + qui.size() + (qui.size() == 1 ? " turno di corso" : " turni di corsi") + ": spostali o toglili prima, oppure indica fino a quando vale.");
        fasce.delete(f);
    }

    /** L'assetto della fascia in cui cade un turno di corso in quella vasca, se le fasce sono definite. */
    Integer assettoPer(UUID vascaId, int giorno, LocalTime inizio, LocalTime fine, LocalDate dal, LocalDate al) {
        LocalDate fineValidita = al == null ? LocalDate.of(9999, 12, 31) : al;
        return fasce.findByVascaIdInOrderByGiornoSettimanaAscInizioAsc(List.of(vascaId)).stream()
            .filter(f -> f.getGiornoSettimana() == giorno && f.tocca(inizio, fine) && f.valeTra(dal, fineValidita) && f.getAssetto() != null)
            .map(FasciaOraria::getAssetto).findFirst().orElse(null);
    }

    // ── Supporto ────────────────────────────────────────────────────────────

    /** I turni dei corsi che stanno nella fascia: stessa vasca (o sue corsie), stesso giorno, orario che si accavalla. */
    private List<Lezione> dentro(FasciaOraria f, LocalTime inizio, LocalTime fine) {
        UUID impianto = impianti.spazi(accessoImpianti()).stream().filter(s -> s.id().equals(f.getVascaId())).map(Impianti.SpazioBreve::impiantoId).findFirst().orElse(null);
        if (impianto == null) return List.of();
        Set<UUID> spaziVasca = new HashSet<>();
        for (Impianti.SpazioBreve s : impianti.spazi(Set.of(impianto)))
            if (s.id().equals(f.getVascaId()) || f.getVascaId().equals(s.padreId())) spaziVasca.add(s.id());
        LocalDate fineValidita = f.getValidaAl() == null ? LocalDate.of(9999, 12, 31) : f.getValidaAl();
        return lezioni.deiCorsiAttivi(Set.of(impianto)).stream()
            .filter(l -> l.getGiornoSettimana() == f.getGiornoSettimana())
            .filter(l -> l.getInizio().isBefore(fine) && inizio.isBefore(l.getFine()))
            .filter(l -> !l.getValidaDal().isAfter(fineValidita) && (l.getValidaAl() == null || !l.getValidaAl().isBefore(f.getValidaDal())))
            .filter(l -> l.getSpazi().stream().anyMatch(spaziVasca::contains))
            .toList();
    }

    private Set<UUID> accessoImpianti() {
        Set<UUID> tutti = new HashSet<>();
        impianti.tutti().forEach(i -> tutti.add(i.id()));
        return tutti;
    }

    private FasciaOraria fascia(Utente u, UUID id) {
        FasciaOraria f = fasce.findById(id).orElseThrow(() -> new NonTrovato("Fascia non trovata."));
        vasca(u, f.getVascaId());
        return f;
    }

    /** Una vasca con le sue corsie raggruppate per assetto. */
    private record Vasca(UUID id, UUID impiantoId, Map<UUID, Integer> numeroCorsia, Map<Integer, Map<Integer, UUID>> perAssetto) {
        Integer assettoValido(Integer a) {
            if (perAssetto.isEmpty()) return null;
            if (a == null) return perAssetto.keySet().stream().max(Integer::compare).orElseThrow();
            if (!perAssetto.containsKey(a)) throw new IllegalArgumentException("Questa vasca non si divide in " + a + " corsie.");
            return a;
        }

        Map<Integer, UUID> corsie(Integer assetto) {
            return perAssetto.getOrDefault(assetto, Map.of());
        }

        Integer numero(UUID corsia) {
            return numeroCorsia.get(corsia);
        }
    }

    private Vasca vasca(Utente u, UUID vascaId) {
        if (vascaId == null) throw new IllegalArgumentException("Scegli la vasca.");
        Impianti.SpazioBreve v = impianti.spazi(accesso.visibili(u)).stream().filter(s -> s.id().equals(vascaId)).findFirst()
            .orElseThrow(() -> new NonTrovato("Vasca non trovata."));
        if (v.padreId() != null) throw new IllegalArgumentException("Le fasce si definiscono per vasca, non per corsia.");
        accesso.richiediGestione(u, v.impiantoId());
        Map<UUID, Integer> numeri = new HashMap<>();
        Map<Integer, Map<Integer, UUID>> perAssetto = new HashMap<>();
        for (Impianti.SpazioBreve s : impianti.spazi(Set.of(v.impiantoId()))) {
            if (!vascaId.equals(s.padreId()) || s.assetto() == null) continue;
            Matcher m = NUMERO.matcher(s.nome());
            if (!m.find()) continue;
            int n = Integer.parseInt(m.group(1));
            numeri.put(s.id(), n);
            perAssetto.computeIfAbsent(s.assetto(), k -> new HashMap<>()).put(n, s.id());
        }
        return new Vasca(v.id(), v.impiantoId(), numeri, perAssetto);
    }
}
