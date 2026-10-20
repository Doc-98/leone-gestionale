package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

/**
 * Scadenze e promemoria email: finestre di giorni prima della scadenza, una sola email per scadenza, tesseramento
 * mancante ripetuto, invio a mano (ADR 0013). Il tempo è fermo nel 2028 e le email finiscono in una casella finta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gestionale.promemoria.cron=-")
@Import({TestcontainersConfiguration.class, PromemoriaTest.Ambiente.class})
@ActiveProfiles("demo")
class PromemoriaTest {

    static final ZoneId ROMA = ZoneId.of("Europe/Rome");

    static final class Orologio extends Clock {
        volatile Instant adesso = Instant.now();
        void alle(String quando) { adesso = LocalDateTime.parse(quando).atZone(ROMA).toInstant(); }
        @Override public ZoneId getZone() { return ROMA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return adesso; }
    }

    static final class CasellaFinta extends JavaMailSenderImpl {
        final List<SimpleMailMessage> arrivate = new CopyOnWriteArrayList<>();
        @Override public void send(SimpleMailMessage m) { arrivate.add(m); }
        @Override public void send(SimpleMailMessage... m) { arrivate.addAll(List.of(m)); }
        List<SimpleMailMessage> per(String email) { return arrivate.stream().filter(m -> List.of(m.getTo()).contains(email)).toList(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Ambiente {
        @Bean @Primary Orologio orologioPromemoria() { return new Orologio(); }
        @Bean CasellaFinta casellaFinta() { return new CasellaFinta(); }
    }

    @LocalServerPort
    int porta;
    @Autowired
    Orologio orologio;
    @Autowired
    CasellaFinta casella;

    private ClientApi come(String email) throws Exception {
        var c = new ClientApi(porta);
        assertThat(c.login(email, "demo1234").statusCode()).isEqualTo(200);
        return c;
    }

    private static Map<String, Object> json(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseMap(r.body());
    }

    private String crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return (String) json(r).get("id");
    }

    @Test
    void iPromemoriaPartonoUnaVoltaPerScadenzaENelleFinestre() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        orologio.alle("2028-03-01T09:00");
        String corso = crea(coord, "/api/corsi", "{\"impiantoId\":\"00000000-0000-0000-0000-0000000000b1\",\"nome\":\"Corso promemoria\",\"tipo\":\"ALTRO\"}");

        String persona = crea(segr, "/api/clienti", "{\"nome\":\"Nora\",\"cognome\":\"Promemoria\",\"dataNascita\":\"2018-02-02\"}");
        String email = "famiglia.nora@esempio.it";
        assertThat(segr.put("/api/clienti/" + persona + "/contatti", "{\"email\":\"non-valida\"}").statusCode()).isEqualTo(400);
        assertThat(segr.put("/api/clienti/" + persona + "/contatti", "{\"email\":\"" + email + "\",\"telefono\":\"333 1234567\"}").statusCode()).isEqualTo(200);
        crea(segr, "/api/clienti/" + persona + "/abbonamenti",
            "{\"tipo\":\"CORSO\",\"corsoId\":\"" + corso + "\",\"volteASettimana\":2,\"dal\":\"2027-09-01\",\"al\":\"2028-06-30\"}");
        crea(segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-03-20\"}");
        // niente tesseramento 2027/28: abbonamento e certificato in ordine, ma il tornello darebbe rosso

        String senza = crea(segr, "/api/clienti", "{\"nome\":\"Ugo\",\"cognome\":\"Senzaemail\",\"dataNascita\":\"2017-01-01\"}");
        crea(segr, "/api/clienti/" + senza + "/abbonamenti",
            "{\"tipo\":\"CORSO\",\"corsoId\":\"" + corso + "\",\"volteASettimana\":1,\"dal\":\"2027-09-01\",\"al\":\"2028-06-30\"}");
        crea(segr, "/api/clienti/" + senza + "/tesseramenti", "{}");
        crea(segr, "/api/clienti/" + senza + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-03-10\"}");

        // la pagina delle scadenze
        var sc = segr.get("/api/scadenze?entro=30").body();
        assertThat(sc).contains("Nora").contains("\"tipo\":\"CERTIFICATO\"").contains("\"giorni\":19").contains("\"tipo\":\"TESSERAMENTO_MANCANTE\"")
            .contains("\"postaConfigurata\":true").contains(email);
        assertThat(come("staff@demo.leone").get("/api/scadenze").statusCode()).isEqualTo(403);

        // primo giro: certificato (finestra dei 30 giorni) e tesseramento mancante
        assertThat(segr.post("/api/promemoria/esegui", "{}").statusCode()).isEqualTo(200);
        var prime = casella.per(email);
        assertThat(prime).hasSize(2);
        var cert = prime.stream().filter(m -> m.getSubject().contains("certificato")).findFirst().orElseThrow();
        assertThat(cert.getSubject()).isEqualTo("Il certificato medico di Nora scade il 20/03/2028");
        assertThat(cert.getText()).contains("Nora Promemoria").contains("tra 19 giorni").contains("Leone XIII Sport").doesNotContain("{");
        assertThat(prime.stream().anyMatch(m -> m.getSubject().equals("Manca il tesseramento 2027/28 di Nora"))).isTrue();

        // stesso giorno: niente doppioni
        segr.post("/api/promemoria/esegui", "{}");
        assertThat(casella.per(email)).hasSize(2);

        // a 7 giorni: il secondo promemoria del certificato; il tesseramento mancante si ripete (ogni 7 giorni)
        orologio.alle("2028-03-13T09:00");
        segr.post("/api/promemoria/esegui", "{}");
        assertThat(casella.per(email)).hasSize(4);
        assertThat(casella.per(email).stream().anyMatch(m -> m.getSubject().startsWith("Promemoria: il certificato medico di Nora"))).isTrue();

        // pagato il tesseramento: non arriva più; scaduto il certificato: nessun «in scadenza»
        crea(segr, "/api/clienti/" + persona + "/tesseramenti", "{}");
        orologio.alle("2028-03-25T09:00");
        segr.post("/api/promemoria/esegui", "{}");
        assertThat(casella.per(email)).hasSize(4);

        // chi non ha l'email: registrato «senza email», si vede nel registro
        assertThat(segr.get("/api/clienti/" + senza).body()).contains("SENZA_EMAIL").contains("Il certificato medico di Ugo scade il 10/03/2028");
        assertThat(segr.get("/api/promemoria").body()).contains("SENZA_EMAIL");

        // a mano dalla pagina delle scadenze, con il testo modificato
        @SuppressWarnings("unchecked")
        var regole = (List<Map<String, Object>>) json(segr.get("/api/promemoria")).get("regole");
        var settegiorni = regole.stream().filter(r -> "CERTIFICATO".equals(r.get("tipo")) && ((Number) r.get("giorni")).intValue() == 7).findFirst().orElseThrow();
        assertThat(segr.put("/api/promemoria/" + settegiorni.get("id"),
            "{\"giorni\":7,\"attivo\":true,\"oggetto\":\"Certificato di {nome}: scade il {scadenza}\",\"testo\":\"Ciao {nome}, mancano {giorni} giorni. {societa}\"}").statusCode())
            .isEqualTo(200);
        String voce = (String) ((Map<?, ?>) ((List<?>) json(segr.get("/api/scadenze?entro=60")).get("certificati")).stream()
            .filter(v -> "Nora".equals(((Map<?, ?>) v).get("nome"))).findFirst().orElseThrow()).get("riferimento");
        var manuale = segr.post("/api/scadenze/promemoria", "{\"personaId\":\"" + persona + "\",\"tipo\":\"CERTIFICATO\",\"riferimento\":\"" + voce + "\"}");
        assertThat(manuale.statusCode()).as(manuale.body()).isEqualTo(200);
        assertThat(json(manuale).get("stato")).isEqualTo("INVIATA");
        assertThat(casella.per(email).getLast().getSubject()).isEqualTo("Certificato di Nora: scade il 20/03/2028");
        assertThat(segr.get("/api/clienti/" + persona).body()).contains("Certificato di Nora").contains("\"telefono\":\"333 1234567\"");
    }
}
