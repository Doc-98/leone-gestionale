package it.leonexiii.gestionale.accessi;

import it.leonexiii.gestionale.clienti.Clienti;
import it.leonexiii.gestionale.identita.Persone;
import it.leonexiii.gestionale.identita.Utente;
import it.leonexiii.gestionale.pianovasca.Frequenze;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il passaggio al tornello: carica il contesto, valuta tutti i controlli attivi (saltando quelli che il golden pass
 * scavalca), registra il passaggio e, se serve, scala un ingresso dal carnet.
 */
@Service
class VerificaAccessi {

    static final ZoneId FUSO = ZoneId.of("Europe/Rome");

    record PersonaEsito(UUID id, String nome, String cognome, Integer eta, long braccialettiAttivi) {}

    record CarnetEsito(UUID abbonamentoId, int ingressi, int usati, boolean scalato, boolean rientro) {}

    /** L'esito da mostrare al tornellista: sempre il nome della persona e tutti i motivi. */
    record Esito(UUID passaggioId, Instant istante, String codice, boolean verde, PersonaEsito persona, String statoBraccialetto,
                 boolean goldenPass, List<Motivo> motivi, CarnetEsito carnet) {}

    /** Il risultato dei controlli, prima di registrare il passaggio. */
    record Valutazione(Contesto contesto, List<Motivo> motivi, boolean verde, Clienti.AbbonamentoBreve carnetDaUsare) {}

    private final List<ControlloAccesso> controlli;
    private final RegolaRepository regole;
    private final BraccialettoRepository braccialetti;
    private final BloccoRepository blocchi;
    private final GoldenPassRepository golden;
    private final PassaggioRepository passaggi;
    private final Persone persone;
    private final Clienti clienti;
    private final Frequenze frequenze;
    private final Clock orologio;

    VerificaAccessi(List<ControlloAccesso> controlli, RegolaRepository regole, BraccialettoRepository braccialetti, BloccoRepository blocchi,
                    GoldenPassRepository golden, PassaggioRepository passaggi, Persone persone, Clienti clienti, Frequenze frequenze, Clock orologio) {
        this.controlli = controlli.stream().sorted(Comparator.comparingInt(ControlloAccesso::ordine)).toList();
        this.regole = regole;
        this.braccialetti = braccialetti;
        this.blocchi = blocchi;
        this.golden = golden;
        this.passaggi = passaggi;
        this.persone = persone;
        this.clienti = clienti;
        this.frequenze = frequenze;
        this.orologio = orologio;
    }

    List<ControlloAccesso> controlli() {
        return controlli;
    }

    /** I parametri effettivi di un controllo: i predefiniti, sovrascritti da quelli salvati. */
    Parametri parametriDi(ControlloAccesso c, Map<String, RegolaAccesso> salvate) {
        Map<String, Object> v = new HashMap<>();
        c.parametri().forEach(p -> v.put(p.nome(), p.predefinito()));
        RegolaAccesso r = salvate.get(c.regola());
        if (r != null) v.putAll(r.getParametri());
        return new Parametri(v);
    }

    /** Un controllo senza riga in tabella (appena aggiunto) vale come attivo con i parametri predefiniti. */
    static boolean attivo(ControlloAccesso c, Map<String, RegolaAccesso> salvate) {
        RegolaAccesso r = salvate.get(c.regola());
        return r == null || r.isAttiva();
    }

    Map<String, RegolaAccesso> regoleSalvate() {
        return regole.findAll().stream().collect(Collectors.toMap(RegolaAccesso::getCodice, Function.identity()));
    }

    @Transactional
    Esito passa(Utente u, String codiceLetto) {
        String codice = Codici.normalizza(codiceLetto);
        Instant istante = orologio.instant();
        List<Braccialetto> conCodice = braccialetti.findByCodiceOrderByAssegnatoIlDesc(codice);
        Braccialetto b = conCodice.stream().filter(x -> x.stato() != Braccialetto.Stato.DISATTIVATO).findFirst()
            .orElse(conCodice.isEmpty() ? null : conCodice.getFirst());
        if (b == null) {
            // un codice sconosciuto è sempre rosso, qualunque regola sia attiva
            List<Motivo> m = List.of(Motivo.blocca("BRACCIALETTO_SCONOSCIUTO", "BRACCIALETTO", "Braccialetto non registrato.",
                "Assegnarlo a una persona dalla scheda cliente."));
            Passaggio p = passaggi.saveAndFlush(new Passaggio(istante, codice, null, null, false, false, m, u.personaId()));
            return new Esito(p.getId(), istante, codice, false, null, null, false, m, null);
        }
        Valutazione v = valuta(b.getPersonaId(), b, istante);
        Passaggio p = passaggi.save(new Passaggio(istante, codice, b.getId(), b.getPersonaId(), v.verde(), v.contesto().goldenPass(), v.motivi(), u.personaId()));
        CarnetEsito carnet = null;
        if (v.verde() && v.carnetDaUsare() != null) {
            Clienti.IngressoScalato r = clienti.usaCarnet(v.carnetDaUsare().id(), istante, v.contesto().minutiRientro(), p.getId());
            if (r.esito() == Clienti.EsitoCarnet.ESAURITO)
                p.respinto(Motivo.blocca("CARNET_ESAURITO", "ABBONAMENTO", "Carnet esaurito: usati " + r.usati() + " ingressi su " + r.ingressi() + ".",
                    "Acquistare un nuovo carnet in segreteria."));
            else if (r.esito() == Clienti.EsitoCarnet.SCALATO) p.carnetScalato(v.carnetDaUsare().id());
            carnet = new CarnetEsito(v.carnetDaUsare().id(), r.ingressi(), r.usati(), r.esito() == Clienti.EsitoCarnet.SCALATO, r.esito() == Clienti.EsitoCarnet.RIENTRO);
        }
        passaggi.flush();
        return new Esito(p.getId(), istante, codice, p.isVerde(), personaEsito(v.contesto()), b.stato().name(), v.contesto().goldenPass(),
            List.copyOf(p.getMotivi()), carnet);
    }

    /** Come andrebbe adesso al tornello, per la scheda: con il primo braccialetto attivo, senza registrare nulla. */
    @Transactional(readOnly = true)
    Esito adesso(UUID personaId) {
        Instant istante = orologio.instant();
        Braccialetto b = braccialetti.findByPersonaIdOrderByAssegnatoIlDesc(personaId).stream().filter(Braccialetto::attivo).findFirst().orElse(null);
        Valutazione v = valuta(personaId, b, istante);
        List<Motivo> motivi = new ArrayList<>();
        if (b == null) motivi.add(Motivo.blocca("NESSUN_BRACCIALETTO", "BRACCIALETTO", "Nessun braccialetto attivo.", "Assegnare un braccialetto dalla scheda."));
        motivi.addAll(v.motivi());
        CarnetEsito carnet = v.carnetDaUsare() == null ? null : new CarnetEsito(v.carnetDaUsare().id(), v.carnetDaUsare().ingressi(), v.carnetDaUsare().usati(),
            false, v.carnetDaUsare().rientroAlle(istante, v.contesto().minutiRientro()));
        return new Esito(null, istante, b == null ? null : b.getCodice(), motivi.stream().noneMatch(Motivo::blocca), personaEsito(v.contesto()),
            b == null ? null : b.stato().name(), v.contesto().goldenPass(), motivi, carnet);
    }

    Valutazione valuta(UUID personaId, Braccialetto b, Instant istante) {
        LocalDateTime locale = LocalDateTime.ofInstant(istante, FUSO);
        LocalDate oggi = locale.toLocalDate();
        Map<String, RegolaAccesso> salvate = regoleSalvate();
        Persone.PersonaScheda persona = persone.schede(List.of(personaId)).get(personaId);
        List<Braccialetto> suoi = braccialetti.findByPersonaIdOrderByAssegnatoIlDesc(personaId);
        List<Blocco> attivi = blocchi.diPersonaOBraccialetti(personaId, b == null ? List.of(UUID.randomUUID()) : List.of(b.getId())).stream()
            .filter(x -> x.attivoIl(oggi)).toList();
        boolean goldenPass = golden.findByPersonaIdOrderByDalDesc(personaId).stream().anyMatch(g -> g.attivoIl(oggi));
        ControlloAccesso abbonamento = controlli.stream().filter(c -> c.regola().equals("ABBONAMENTO")).findFirst().orElse(null);
        int rientro = abbonamento == null ? 90 : parametriDi(abbonamento, salvate).intero("minutiRientro", 90);
        Contesto c = new Contesto(b, suoi, persona, istante, oggi, locale.toLocalTime(), attivi, goldenPass, clienti.situazione(personaId),
            frequenze.turniIl(personaId, oggi), rientro);

        List<Motivo> motivi = new ArrayList<>();
        for (ControlloAccesso k : controlli) {
            if (!attivo(k, salvate) || (goldenPass && !k.valeConGoldenPass())) continue;
            motivi.addAll(k.verifica(c, parametriDi(k, salvate)));
        }
        boolean verde = motivi.stream().noneMatch(Motivo::blocca);

        // Il carnet si scala solo se è lui a far entrare: non con il golden pass, non se c'è un abbonamento al corso
        // valido e si è nella fascia di un turno (o la fascia non si controlla).
        Clienti.AbbonamentoBreve carnet = null;
        if (abbonamento != null && attivo(abbonamento, salvate) && !goldenPass) {
            ControlloAccesso fascia = controlli.stream().filter(k -> k.regola().equals("FASCIA_ORARIA")).findFirst().orElse(null);
            boolean fasciaControllata = fascia != null && attivo(fascia, salvate);
            boolean copertoDalCorso = c.corsoValido() && (!fasciaControllata || c.turniOggi().stream().anyMatch(t -> c.inFinestra(t, parametriDi(fascia, salvate))));
            if (!copertoDalCorso) carnet = c.carnetUtilizzabile().orElse(null);
        }
        return new Valutazione(c, motivi, verde, carnet);
    }

    private static PersonaEsito personaEsito(Contesto c) {
        var p = c.persona();
        Integer eta = p.dataNascita() == null ? null : Period.between(p.dataNascita(), c.oggi()).getYears();
        return new PersonaEsito(p.id(), p.nome(), p.cognome(), eta, c.braccialettiAttivi());
    }
}
