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

    public record Certificato(UUID id, String tipo, LocalDate scadenza) {}

    public record TesseramentoBreve(UUID id, String stagione, LocalDate dal, LocalDate al) {}

    /** Abbonamento non annullato; per i carnet gli ingressi usati e l'ultimo scalato. */
    public record AbbonamentoBreve(UUID id, String tipo, UUID corsoId, String corso, Integer volteASettimana, Integer ingressi,
                                   int usati, Instant ultimoIngresso, LocalDate dal, LocalDate al) {
        public boolean carnet() { return Abbonamento.CARNET.equals(tipo); }

        public boolean validoIl(LocalDate g) { return !g.isBefore(dal) && (al == null || !g.isAfter(al)); }

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

    private final CertificatoRepository certificati;
    private final TesseramentoRepository tesseramenti;
    private final AbbonamentoRepository abbonamenti;
    private final IngressoCarnetRepository ingressi;
    private final Frequenze frequenze;

    Clienti(CertificatoRepository certificati, TesseramentoRepository tesseramenti, AbbonamentoRepository abbonamenti,
            IngressoCarnetRepository ingressi, Frequenze frequenze) {
        this.certificati = certificati;
        this.tesseramenti = tesseramenti;
        this.abbonamenti = abbonamenti;
        this.ingressi = ingressi;
        this.frequenze = frequenze;
    }

    @Transactional(readOnly = true)
    public Situazione situazione(UUID personaId) {
        var cert = certificati.findByPersonaIdOrderByScadenzaDesc(personaId).stream().filter(c -> c.getAnnullatoIl() == null)
            .map(c -> new Certificato(c.getId(), c.getTipo(), c.getScadenza())).toList();
        var tess = tesseramenti.findByPersonaIdOrderByDalDesc(personaId).stream().filter(t -> t.getAnnullatoIl() == null)
            .map(t -> new TesseramentoBreve(t.getId(), t.getStagione(), t.getDal(), t.getAl())).toList();
        return new Situazione(personaId, cert, tess, abbonamentiDi(personaId));
    }

    List<AbbonamentoBreve> abbonamentiDi(UUID personaId) {
        List<Abbonamento> suoi = abbonamenti.findByPersonaIdOrderByDalDesc(personaId).stream().filter(a -> a.getAnnullatoIl() == null).toList();
        List<UUID> carnet = suoi.stream().filter(Abbonamento::isCarnet).map(Abbonamento::getId).toList();
        Map<UUID, List<IngressoCarnet>> usati = carnet.isEmpty() ? Map.of()
            : ingressi.findByAbbonamentoIdInOrderByIstanteDesc(carnet).stream().collect(Collectors.groupingBy(IngressoCarnet::getAbbonamentoId));
        Map<UUID, String> nomiCorsi = suoi.stream().anyMatch(a -> a.getCorsoId() != null)
            ? frequenze.corsi().stream().collect(Collectors.toMap(Frequenze.CorsoBreve::id, Frequenze.CorsoBreve::nome)) : Map.of();
        List<AbbonamentoBreve> out = new ArrayList<>();
        for (Abbonamento a : suoi) {
            List<IngressoCarnet> u = usati.getOrDefault(a.getId(), List.of());
            out.add(new AbbonamentoBreve(a.getId(), a.getTipo(), a.getCorsoId(), a.getCorsoId() == null ? null : nomiCorsi.get(a.getCorsoId()),
                a.getVolteASettimana(), a.getIngressi(), u.size(), u.isEmpty() ? null : u.getFirst().getIstante(), a.getDal(), a.getAl()));
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
