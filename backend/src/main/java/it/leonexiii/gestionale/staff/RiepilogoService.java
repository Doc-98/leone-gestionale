package it.leonexiii.gestionale.staff;

import it.leonexiii.gestionale.identita.PersonaBreve;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ore e compensi del mese, per persona e per mansione, con la tariffa valida nel giorno del turno. */
@Service
class RiepilogoService {

    record Voce(String mansione, BigDecimal euroOra, BigDecimal ore, BigDecimal compenso) {}

    /** gruppoId/gruppo: il gruppo di staff (la mansione) in cui la persona ha fatto più ore nel mese. */
    record Riga(PersonaBreve persona, int turni, BigDecimal ore, BigDecimal compenso, boolean tariffaMancante, List<Voce> voci,
                UUID gruppoId, String gruppo) {}

    /** Un gruppo di staff nel mese (ADR 0022). */
    record Gruppo(UUID id, String nome, List<String> coordinatori, int persone, BigDecimal ore, BigDecimal compenso, int turniScoperti,
                  int tariffeMancanti) {}

    record Riepilogo(String mese, List<Riga> righe, BigDecimal totaleOre, BigDecimal totaleCompenso, int turniScoperti, List<Gruppo> gruppi) {}

    private final TurnoRepository turni;
    private final TariffaRepository tariffe;
    private final Persone persone;
    private final GruppiStaff gruppiStaff;

    RiepilogoService(TurnoRepository turni, TariffaRepository tariffe, Persone persone, GruppiStaff gruppiStaff) {
        this.turni = turni;
        this.tariffe = tariffe;
        this.persone = persone;
        this.gruppiStaff = gruppiStaff;
    }

    /** Lo staff dei gruppi visibili (tutti, o uno solo con {@code gruppo}). */
    @Transactional(readOnly = true)
    Riepilogo diImpianti(Utente u, YearMonth mese, UUID gruppo) {
        List<Mansione> gruppi = gruppiStaff.visibili(u);
        if (gruppo != null) {
            gruppi = gruppi.stream().filter(m -> m.getId().equals(gruppo)).toList();
            if (gruppi.isEmpty()) throw new AccessDeniedException("Non vedi le ore di questo gruppo di staff.");
        }
        return calcola(mese, turniDi(gruppi, mese), gruppi);
    }

    /** I turni del mese nei gruppi indicati. */
    List<Turno> turniDi(List<Mansione> gruppi, YearMonth mese) {
        if (gruppi.isEmpty()) return List.of();
        Set<UUID> impianti = gruppi.stream().map(Mansione::getImpiantoId).collect(Collectors.toSet());
        Set<UUID> ids = gruppi.stream().map(Mansione::getId).collect(Collectors.toSet());
        return turni.nelPeriodo(mese.atDay(1), mese.atEndOfMonth(), impianti).stream()
            .filter(t -> ids.contains(t.getPostazione().getMansione().getId())).toList();
    }

    /** Solo i miei turni. */
    @Transactional(readOnly = true)
    Riepilogo mio(Utente u, YearMonth mese) {
        return calcola(mese, turni.diPersona(u.personaId(), mese.atDay(1), mese.atEndOfMonth()), List.of());
    }

    private Riepilogo calcola(YearMonth mese, List<Turno> elenco, List<Mansione> gruppiVisti) {
        List<Turno> validi = elenco.stream().filter(Turno::pianificato).toList();
        int scoperti = (int) validi.stream().filter(t -> t.getPersonaId() == null).count();
        List<Turno> assegnati = validi.stream().filter(t -> t.getPersonaId() != null).toList();

        Set<UUID> mansioni = assegnati.stream().map(t -> t.getPostazione().getMansione().getId()).collect(Collectors.toSet());
        List<Tariffa> listino = mansioni.isEmpty() ? List.of() : tariffe.findByMansioneIdIn(mansioni);
        Map<UUID, String> nomi = persone.nomiCompleti(assegnati.stream().map(Turno::getPersonaId).distinct().toList());

        List<Riga> righe = new ArrayList<>();
        assegnati.stream().collect(Collectors.groupingBy(Turno::getPersonaId, LinkedHashMap::new, Collectors.toList()))
            .forEach((persona, suoi) -> {
                // per mansione e tariffa (la tariffa può cambiare a metà mese)
                Map<String, List<Turno>> gruppi = new LinkedHashMap<>();
                Map<String, BigDecimal> tariffaGruppo = new LinkedHashMap<>();
                boolean mancante = false;
                for (Turno t : suoi) {
                    BigDecimal euro = tariffa(listino, t);
                    if (euro == null) mancante = true;
                    String chiave = t.getPostazione().getMansione().getNome() + "|" + euro;
                    gruppi.computeIfAbsent(chiave, k -> new ArrayList<>()).add(t);
                    tariffaGruppo.put(chiave, euro);
                }
                List<Voce> voci = new ArrayList<>();
                gruppi.forEach((chiave, ts) -> {
                    BigDecimal ore = somma(ts.stream().map(Turno::ore).toList());
                    BigDecimal euro = tariffaGruppo.get(chiave);
                    voci.add(new Voce(chiave.substring(0, chiave.indexOf('|')), euro, ore,
                        euro == null ? BigDecimal.ZERO : ore.multiply(euro).setScale(2, RoundingMode.HALF_UP)));
                });
                Mansione principale = suoi.stream().collect(Collectors.groupingBy(t -> t.getPostazione().getMansione(),
                        Collectors.reducing(BigDecimal.ZERO, Turno::ore, BigDecimal::add)))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
                righe.add(new Riga(new PersonaBreve(persona, nomi.getOrDefault(persona, "?")), suoi.size(),
                    somma(voci.stream().map(Voce::ore).toList()), somma(voci.stream().map(Voce::compenso).toList()), mancante, voci,
                    principale.getId(), principale.getNome()));
            });
        righe.sort(Comparator.comparing(r -> r.persona().nome()));
        return new Riepilogo(mese.toString(), righe, somma(righe.stream().map(Riga::ore).toList()),
            somma(righe.stream().map(Riga::compenso).toList()), scoperti, gruppi(gruppiVisti, validi, listino));
    }

    /** Ogni gruppo con le sue persone, ore, compensi, turni scoperti e tariffe mancanti (anche se nel mese è vuoto). */
    private List<Gruppo> gruppi(List<Mansione> gruppi, List<Turno> validi, List<Tariffa> listino) {
        if (gruppi.isEmpty()) return List.of();
        Map<UUID, String> nomi = persone.nomiCompleti(gruppi.stream().flatMap(m -> m.getCoordinatori().stream()).distinct().toList());
        List<Gruppo> out = new ArrayList<>();
        for (Mansione m : gruppi) {
            List<Turno> suoi = validi.stream().filter(t -> t.getPostazione().getMansione().getId().equals(m.getId())).toList();
            List<Turno> assegnati = suoi.stream().filter(t -> t.getPersonaId() != null).toList();
            BigDecimal compenso = BigDecimal.ZERO;
            Set<UUID> senzaTariffa = new HashSet<>();
            for (Turno t : assegnati) {
                BigDecimal euro = tariffa(listino, t);
                if (euro == null) senzaTariffa.add(t.getPersonaId());
                else compenso = compenso.add(t.ore().multiply(euro));
            }
            out.add(new Gruppo(m.getId(), m.getNome(), m.getCoordinatori().stream().map(id -> nomi.getOrDefault(id, "?")).sorted().toList(),
                (int) assegnati.stream().map(Turno::getPersonaId).distinct().count(), somma(assegnati.stream().map(Turno::ore).toList()),
                compenso.setScale(2, RoundingMode.HALF_UP), (int) suoi.stream().filter(t -> t.getPersonaId() == null).count(), senzaTariffa.size()));
        }
        return out;
    }

    /** Tariffa valida nel giorno: quella personale vince su quella della mansione. */
    private static BigDecimal tariffa(List<Tariffa> listino, Turno t) {
        UUID mansione = t.getPostazione().getMansione().getId();
        return listino.stream()
            .filter(x -> x.getMansioneId().equals(mansione) && x.validaIl(t.getData()))
            .filter(x -> x.getPersonaId() == null || x.getPersonaId().equals(t.getPersonaId()))
            .min(Comparator.comparing(x -> x.getPersonaId() == null ? 1 : 0))
            .map(Tariffa::getEuroOra).orElse(null);
    }

    private static BigDecimal somma(List<BigDecimal> valori) {
        return valori.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** CSV per Excel (separatore «;», virgola decimale, BOM UTF-8). */
    static String csv(Riepilogo r) {
        StringBuilder sb = new StringBuilder("﻿Persona;Gruppo;Turni;Ore;Compenso €;Tariffa mancante\r\n");
        for (Riga x : r.righe()) {
            sb.append(x.persona().nome().replace(";", ",")).append(';').append(x.gruppo() == null ? "" : x.gruppo().replace(";", ",")).append(';').append(x.turni()).append(';')
                .append(it(x.ore())).append(';').append(it(x.compenso())).append(';').append(x.tariffaMancante() ? "sì" : "").append("\r\n");
        }
        sb.append("Totale;;;").append(it(r.totaleOre())).append(';').append(it(r.totaleCompenso())).append(";\r\n");
        return sb.toString();
    }

    private static String it(BigDecimal n) {
        return n.toPlainString().replace('.', ',');
    }
}
