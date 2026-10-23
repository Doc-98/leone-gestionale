package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.condiviso.NonTrovato;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pubblica del modulo per gli altri moduli (es. il tornello): la situazione di un cliente — certificati,
 * tesseramenti, abbonamenti con gli ingressi usati — e lo scalare un ingresso da un carnet.
 */
@Service
public class Clienti {

    /** Per i provvisori conta anche quando è stata firmata la dichiarazione (vale al massimo N giorni). */
    public record Certificato(UUID id, String tipo, LocalDate rilasciatoIl, LocalDate scadenza) {
        public boolean provvisorio() { return "PROVVISORIO".equals(tipo); }

        /** Ultimo giorno di validità: la scadenza, e per un provvisorio non oltre N giorni dalla firma. */
        public LocalDate validoFino(int giorniMaxProvvisorio) {
            if (!provvisorio() || rilasciatoIl == null) return scadenza;
            LocalDate limite = rilasciatoIl.plusDays(giorniMaxProvvisorio - 1L);
            return limite.isBefore(scadenza) ? limite : scadenza;
        }
    }

    public record Periodo(UUID id, LocalDate dal, LocalDate al, String motivo) {}

    public record TesseramentoBreve(UUID id, String stagione, LocalDate dal, LocalDate al) {}

    /** Abbonamento non annullato; per i carnet gli ingressi usati e l'ultimo scalato. */
    public record AbbonamentoBreve(UUID id, String tipo, UUID corsoId, String corso, Integer volteASettimana, Integer ingressi,
                                   int usati, Instant ultimoIngresso, LocalDate dal, LocalDate al, List<Periodo> sospensioni) {

        /** La fine allungata dei giorni di sospensione (null = senza fine). */
        public LocalDate alEffettivo() {
            if (al == null) return null;
            long giorni = sospensioni.stream().mapToLong(s -> java.time.temporal.ChronoUnit.DAYS.between(s.dal(), s.al()) + 1).sum();
            return al.plusDays(giorni);
        }

        public java.util.Optional<Periodo> sospesoIl(LocalDate g) {
            return sospensioni.stream().filter(s -> !g.isBefore(s.dal()) && !g.isAfter(s.al())).findFirst();
        }

        public boolean carnet() { return Abbonamento.CARNET.equals(tipo); }

        /** Un carnet con un corso vale solo negli orari dei turni di quel corso; senza, a entrata libera. */
        public boolean aOrario() { return carnet() && corsoId != null; }

        /** Nel periodo (allungato delle sospensioni) e non sospeso quel giorno. */
        public boolean validoIl(LocalDate g) { return nelPeriodo(g) && sospesoIl(g).isEmpty(); }

        public boolean nelPeriodo(LocalDate g) {
            LocalDate fine = alEffettivo();
            return !g.isBefore(dal) && (fine == null || !g.isAfter(fine));
        }

        public Integer residui() { return ingressi == null ? null : Math.max(0, ingressi - usati); }

        /** Un passaggio a quest'ora è un rientro (non scala) se l'ultimo ingresso è di meno di N minuti fa. */
        public boolean rientroAlle(Instant istante, int minutiRientro) {
            return ultimoIngresso != null && !istante.isBefore(ultimoIngresso) && Duration.between(ultimoIngresso, istante).toMinutes() < minutiRientro;
        }
    }

    /** Certificati, tesseramenti e abbonamenti validi (non annullati), dal più recente. */
    public record Situazione(UUID personaId, List<Certificato> certificati, List<TesseramentoBreve> tesseramenti, List<AbbonamentoBreve> abbonamenti) {}

    public enum EsitoCarnet { SCALATO, RIENTRO, ESAURITO }

    public record IngressoScalato(EsitoCarnet esito, int usati, int ingressi) {}

    /** Un gruppo di clienti di cui la persona fa parte (es. per prezzi diversi o la precedenza nei rinnovi). */
    public record GruppoDi(UUID appartenenzaId, UUID gruppoId, String nome, LocalDate dal) {}

    private final CertificatoRepository certificati;
    private final TesseramentoRepository tesseramenti;
    private final AbbonamentoRepository abbonamenti;
    private final IngressoCarnetRepository ingressi;
    private final SospensioneRepository sospensioni;
    private final Frequenze frequenze;
    private final GruppoClientiRepository gruppi;
    private final AppartenenzaGruppoRepository appartenenze;

    Clienti(CertificatoRepository certificati, TesseramentoRepository tesseramenti, AbbonamentoRepository abbonamenti,
            IngressoCarnetRepository ingressi, SospensioneRepository sospensioni, Frequenze frequenze,
            GruppoClientiRepository gruppi, AppartenenzaGruppoRepository appartenenze) {
        this.gruppi = gruppi;
        this.appartenenze = appartenenze;
        this.sospensioni = sospensioni;
        this.certificati = certificati;
        this.tesseramenti = tesseramenti;
        this.abbonamenti = abbonamenti;
        this.ingressi = ingressi;
        this.frequenze = frequenze;
    }

    /** I gruppi di clienti di cui la persona fa parte oggi. */
    @Transactional(readOnly = true)
    public List<GruppoDi> gruppiDi(UUID personaId) {
        var nomi = new java.util.HashMap<UUID, String>();
        gruppi.findAll().forEach(g -> nomi.put(g.getId(), g.getNome()));
        return appartenenze.attiveDi(personaId).stream()
            .map(a -> new GruppoDi(a.getId(), a.getGruppoId(), nomi.get(a.getGruppoId()), a.getDal())).toList();
    }

    @Transactional(readOnly = true)
    public Situazione situazione(UUID personaId) {
        var cert = certificati.findByPersonaIdOrderByScadenzaDesc(personaId).stream().filter(c -> c.getAnnullatoIl() == null)
            .map(c -> new Certificato(c.getId(), c.getTipo(), c.getRilasciatoIl(), c.getScadenza())).toList();
        var tess = tesseramenti.findByPersonaIdOrderByDalDesc(personaId).stream().filter(t -> t.getAnnullatoIl() == null)
            .map(t -> new TesseramentoBreve(t.getId(), t.getStagione(), t.getDal(), t.getAl())).toList();
        return new Situazione(personaId, cert, tess, abbonamentiDi(personaId));
    }

    /** Gli abbonamenti non annullati di tutti, per persona (per le scadenze e i promemoria): poche query in tutto. */
    Map<UUID, List<AbbonamentoBreve>> abbonamentiDiTutti() {
        List<Abbonamento> tutti = abbonamenti.findAll().stream().filter(a -> a.getAnnullatoIl() == null)
            .sorted(java.util.Comparator.comparing(Abbonamento::getDal).reversed()).toList();
        Map<UUID, long[]> conteggi = new java.util.HashMap<>();
        Map<UUID, Instant> ultimi = new java.util.HashMap<>();
        for (Object[] r : ingressi.riepilogo()) {
            conteggi.put((UUID) r[0], new long[] {((Number) r[1]).longValue()});
            ultimi.put((UUID) r[0], (Instant) r[2]);
        }
        Map<UUID, String> nomiCorsi = frequenze.corsi().stream().collect(Collectors.toMap(Frequenze.CorsoBreve::id, Frequenze.CorsoBreve::nome));
        Map<UUID, List<Periodo>> sospesi = sospensioni.findAll().stream().filter(s -> s.getAnnullataIl() == null)
            .collect(Collectors.groupingBy(Sospensione::getAbbonamentoId, Collectors.mapping(s -> new Periodo(s.getId(), s.getDal(), s.getAl(), s.getMotivo()), Collectors.toList())));
        Map<UUID, List<AbbonamentoBreve>> out = new java.util.HashMap<>();
        for (Abbonamento a : tutti) {
            long usati = conteggi.containsKey(a.getId()) ? conteggi.get(a.getId())[0] : 0;
            out.computeIfAbsent(a.getPersonaId(), k -> new ArrayList<>()).add(new AbbonamentoBreve(a.getId(), a.getTipo(), a.getCorsoId(),
                a.getCorsoId() == null ? null : nomiCorsi.get(a.getCorsoId()), a.getVolteASettimana(), a.getIngressi(), (int) usati, ultimi.get(a.getId()),
                a.getDal(), a.getAl(), sospesi.getOrDefault(a.getId(), List.of())));
        }
        return out;
    }

    List<AbbonamentoBreve> abbonamentiDi(UUID personaId) {
        List<Abbonamento> suoi = abbonamenti.findByPersonaIdOrderByDalDesc(personaId).stream().filter(a -> a.getAnnullatoIl() == null).toList();
        List<UUID> carnet = suoi.stream().filter(Abbonamento::isCarnet).map(Abbonamento::getId).toList();
        Map<UUID, List<IngressoCarnet>> usati = carnet.isEmpty() ? Map.of()
            : ingressi.findByAbbonamentoIdInOrderByIstanteDesc(carnet).stream().collect(Collectors.groupingBy(IngressoCarnet::getAbbonamentoId));
        Map<UUID, String> nomiCorsi = suoi.stream().anyMatch(a -> a.getCorsoId() != null)
            ? frequenze.corsi().stream().collect(Collectors.toMap(Frequenze.CorsoBreve::id, Frequenze.CorsoBreve::nome)) : Map.of();
        Map<UUID, List<Periodo>> sospesi = suoi.isEmpty() ? Map.of()
            : sospensioni.findByAbbonamentoIdInOrderByDal(suoi.stream().map(Abbonamento::getId).toList()).stream().filter(s -> s.getAnnullataIl() == null)
                .collect(Collectors.groupingBy(Sospensione::getAbbonamentoId, Collectors.mapping(s -> new Periodo(s.getId(), s.getDal(), s.getAl(), s.getMotivo()), Collectors.toList())));
        List<AbbonamentoBreve> out = new ArrayList<>();
        for (Abbonamento a : suoi) {
            List<IngressoCarnet> u = usati.getOrDefault(a.getId(), List.of());
            out.add(new AbbonamentoBreve(a.getId(), a.getTipo(), a.getCorsoId(), a.getCorsoId() == null ? null : nomiCorsi.get(a.getCorsoId()),
                a.getVolteASettimana(), a.getIngressi(), u.size(), u.isEmpty() ? null : u.getFirst().getIstante(), a.getDal(), a.getAl(),
                sospesi.getOrDefault(a.getId(), List.of())));
        }
        return out;
    }

    /**
     * Scala un ingresso dal carnet, salvo rientro (l'ultimo ingresso è di meno di {@code minutiRientro} fa).
     * L'abbonamento si blocca in scrittura: due passaggi simultanei non scalano due volte.
     */
    @Transactional
    public IngressoScalato usaCarnet(UUID abbonamentoId, Instant istante, int minutiRientro, UUID passaggioId) {
        Abbonamento a = abbonamenti.bloccato(abbonamentoId).orElseThrow(() -> new NonTrovato("Carnet non trovato."));
        if (!a.isCarnet()) throw new IllegalArgumentException("Non è un carnet.");
        List<IngressoCarnet> usati = ingressi.findByAbbonamentoIdInOrderByIstanteDesc(List.of(abbonamentoId));
        int totale = a.getIngressi();
        if (!usati.isEmpty() && Duration.between(usati.getFirst().getIstante(), istante).toMinutes() < minutiRientro)
            return new IngressoScalato(EsitoCarnet.RIENTRO, usati.size(), totale);
        if (usati.size() >= totale) return new IngressoScalato(EsitoCarnet.ESAURITO, usati.size(), totale);
        ingressi.saveAndFlush(new IngressoCarnet(abbonamentoId, istante, passaggioId));
        return new IngressoScalato(EsitoCarnet.SCALATO, usati.size() + 1, totale);
    }

}
