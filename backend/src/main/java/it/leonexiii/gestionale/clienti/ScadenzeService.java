package it.leonexiii.gestionale.clienti;

import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.notifiche.Posta;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le scadenze per la segreteria (ADR 0013): certificati, abbonamenti e carnet, tesseramento in scadenza o mancante, per
 * chi frequenta (ha un abbonamento valido, in arrivo o finito da poco). Le stesse voci alimentano i promemoria email.
 */
@Service
class ScadenzeService {

    /**
     * Una cosa da sistemare. tipo: CERTIFICATO, CERTIFICATO_MANCANTE, ABBONAMENTO, CARNET, TESSERAMENTO,
     * TESSERAMENTO_MANCANTE. giorni: alla scadenza (negativo = già scaduto; null = senza data).
     */
    record Voce(String tipo, UUID personaId, String nome, String cognome, Integer eta, String descrizione, String corso, String stagione,
                LocalDate scadenza, Long giorni, String riferimento, String email, Instant ultimoPromemoria) {}

    record Scadenze(LocalDate oggi, int entro, List<Voce> certificati, List<Voce> abbonamenti, List<Voce> tesseramento, boolean postaConfigurata) {}

    static final int GIORNI_SCADUTI = 60;

    private final Clienti clienti;
    private final CertificatoRepository certificati;
    private final TesseramentoRepository tesseramenti;
    private final InvioPromemoriaRepository invii;
    private final Persone persone;
    private final Posta posta;
    private final Clock orologio;

    ScadenzeService(Clienti clienti, CertificatoRepository certificati, TesseramentoRepository tesseramenti, InvioPromemoriaRepository invii,
                    Persone persone, Posta posta, Clock orologio) {
        this.clienti = clienti;
        this.certificati = certificati;
        this.tesseramenti = tesseramenti;
        this.invii = invii;
        this.persone = persone;
        this.posta = posta;
        this.orologio = orologio;
    }

    @Transactional(readOnly = true)
    Scadenze scadenze(int entro) {
        LocalDate oggi = LocalDate.now(orologio);
        int e = Math.clamp(entro, 1, 365);
        List<Voce> tutte = voci(oggi, e);
        return new Scadenze(oggi, e,
            tutte.stream().filter(v -> v.tipo().startsWith("CERTIFICATO")).toList(),
            tutte.stream().filter(v -> v.tipo().equals("ABBONAMENTO") || v.tipo().equals("CARNET")).toList(),
            tutte.stream().filter(v -> v.tipo().startsWith("TESSERAMENTO")).toList(),
            posta.configurata());
    }

    /** Tutte le voci da oggi a «entro» giorni (e scadute da poco), ordinate per urgenza. */
    @Transactional(readOnly = true)
    List<Voce> voci(LocalDate oggi, int entro) {
        LocalDate limite = oggi.plusDays(entro);
        Map<UUID, List<Clienti.AbbonamentoBreve>> abbonamenti = clienti.abbonamentiDiTutti();
        Map<UUID, CertificatoMedico> migliori = new HashMap<>();
        for (CertificatoMedico c : certificati.findAll())
            if (c.getAnnullatoIl() == null) migliori.merge(c.getPersonaId(), c, (a, b) -> a.getScadenza().isAfter(b.getScadenza()) ? a : b);
        Map<UUID, List<Tesseramento>> tess = tesseramenti.findAll().stream().filter(t -> t.getAnnullatoIl() == null)
            .collect(Collectors.groupingBy(Tesseramento::getPersonaId));

        record Grezza(String tipo, UUID personaId, String descrizione, String corso, String stagione, LocalDate scadenza, String riferimento) {}
        List<Grezza> grezze = new ArrayList<>();
        for (var entry : abbonamenti.entrySet()) {
            UUID p = entry.getKey();
            List<Clienti.AbbonamentoBreve> suoi = entry.getValue();
            boolean frequentaOra = suoi.stream().anyMatch(a -> a.validoIl(oggi) || (a.dal().isAfter(oggi) && !a.dal().isAfter(limite)));
            boolean frequenta = frequentaOra || suoi.stream().anyMatch(a -> a.alEffettivo() != null && !a.alEffettivo().isBefore(oggi.minusDays(30)));
            if (!frequenta) continue;

            // certificato
            CertificatoMedico c = migliori.get(p);
            if (c == null) {
                if (frequentaOra) grezze.add(new Grezza("CERTIFICATO_MANCANTE", p, "Nessun certificato medico", null, null, null, "nessun-certificato"));
            } else if (!c.getScadenza().isAfter(limite) && (frequentaOra || !c.getScadenza().isBefore(oggi)) && !c.getScadenza().isBefore(oggi.minusDays(GIORNI_SCADUTI))) {
                grezze.add(new Grezza("CERTIFICATO", p, "PROVVISORIO".equals(c.getTipo()) ? "Certificato provvisorio" : "Certificato medico", null, null,
                    c.getScadenza(), c.getId().toString()));
            }

            // abbonamenti e carnet
            for (Clienti.AbbonamentoBreve a : suoi) {
                boolean rinnovato = suoi.stream().anyMatch(b -> b != a && b.tipo().equals(a.tipo()) && java.util.Objects.equals(b.corsoId(), a.corsoId()) && b.dal().isAfter(a.dal()));
                String nome = a.carnet() ? "Carnet da " + a.ingressi() + " ingressi" : "Abbonamento " + (a.corso() == null ? "" : a.corso());
                LocalDate fine = a.alEffettivo();
                if (!rinnovato && fine != null && !fine.isAfter(limite) && !fine.isBefore(oggi.minusDays(30)))
                    grezze.add(new Grezza("ABBONAMENTO", p, nome, a.carnet() ? "carnet" : a.corso(), null, fine, a.id().toString()));
                else if (a.carnet() && a.validoIl(oggi) && a.residui() <= 2 && !rinnovato)
                    grezze.add(new Grezza("CARNET", p, a.residui() == 0 ? "Carnet esaurito" : "Carnet quasi finito: " + (a.residui() == 1 ? "1 ingresso" : a.residui() + " ingressi"),
                        "carnet", null, null, a.id().toString()));
            }

            // tesseramento
            if (frequentaOra) {
                List<Tesseramento> suoiT = tess.getOrDefault(p, List.of());
                Tesseramento valido = suoiT.stream().filter(t -> t.validoIl(oggi)).findFirst().orElse(null);
                Stagione s = Stagione.di(oggi);
                if (valido == null) {
                    grezze.add(new Grezza("TESSERAMENTO_MANCANTE", p, "Manca il tesseramento " + s.nome(), null, s.nome(), null, s.nome()));
                } else if (!valido.getAl().isAfter(limite) && suoiT.stream().noneMatch(t -> t.getDal().isAfter(valido.getAl()))) {
                    grezze.add(new Grezza("TESSERAMENTO", p, "Tesseramento " + valido.getStagione(), null, valido.getStagione(), valido.getAl(), valido.getId().toString()));
                }
            }
        }

        Set<UUID> ids = new HashSet<>();
        grezze.forEach(g -> ids.add(g.personaId()));
        Map<UUID, Persone.PersonaScheda> schede = persone.schede(ids);
        Map<UUID, List<String>> destinatari = persone.destinatari(ids);
        Map<String, Instant> ultimi = new HashMap<>();
        for (InvioPromemoria i : invii.findByCreatoIlAfter(orologio.instant().minus(180, ChronoUnit.DAYS)))
            ultimi.merge(i.getPersonaId() + "|" + i.getTipo(), i.getCreatoIl(), (a, b) -> a.isAfter(b) ? a : b);

        List<Voce> out = new ArrayList<>();
        for (Grezza g : grezze) {
            Persone.PersonaScheda s = schede.get(g.personaId());
            if (s == null) continue;
            Integer eta = s.dataNascita() == null ? null : Period.between(s.dataNascita(), oggi).getYears();
            Long giorni = g.scadenza() == null ? null : ChronoUnit.DAYS.between(oggi, g.scadenza());
            List<String> a = destinatari.getOrDefault(g.personaId(), List.of());
            out.add(new Voce(g.tipo(), g.personaId(), s.nome(), s.cognome(), eta, g.descrizione(), g.corso(), g.stagione(), g.scadenza(), giorni,
                g.riferimento(), a.isEmpty() ? null : String.join(", ", a), ultimi.get(g.personaId() + "|" + PromemoriaService.tipoPromemoria(g.tipo()).name())));
        }
        out.sort(Comparator.comparing((Voce v) -> v.giorni() == null ? Long.MIN_VALUE : v.giorni()).thenComparing(Voce::cognome).thenComparing(Voce::nome));
        return out;
    }
}
