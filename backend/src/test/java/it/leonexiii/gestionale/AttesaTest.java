package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Lista d'attesa dei turni pieni, avviso alla famiglia, iscrizione dalla lista e cambio di turno (ADR 0015). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class AttesaTest {

    static final String IMPIANTO = "00000000-0000-0000-0000-0000000000b1";
    static final String CORSIA_4 = "00000000-0000-0000-0000-000000000104";

    @LocalServerPort
    int porta;

    private ClientApi come(String email) throws Exception {
        var c = new ClientApi(porta);
        assertThat(c.login(email, "demo1234").statusCode()).isEqualTo(200);
        return c;
    }

    private static Map<String, Object> json(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseMap(r.body());
    }

    private Map<String, Object> crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return json(r);
    }

    @SuppressWarnings("unchecked")
    private String turno(ClientApi coord, String corso, String inizio, String fine) throws Exception {
        var r = crea(coord, "/api/corsi/" + corso + "/lezioni", """
            {"giornoSettimana":7,"inizio":"%s","fine":"%s","validaDal":"2026-01-04","spazi":["%s"],"posti":1}""".formatted(inizio, fine, CORSIA_4));
        return (String) ((Map<String, Object>) r.get("valore")).get("id");
    }

    @Test
    void listaDAttesaECambioTurno() throws Exception {
        var coord = come("coordinatore@demo.leone");
        var segr = come("segreteria@demo.leone");
        LocalDate oggi = LocalDate.now(ZoneId.of("Europe/Rome"));
        String fra7 = oggi.plusDays(7).toString();
        String corso = (String) crea(coord, "/api/corsi", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"Prova attesa\",\"tipo\":\"ALTRO\"}").get("id");
        String a = turno(coord, corso, "20:00", "20:45");
        String b = turno(coord, corso, "21:00", "21:45");

        // il turno A ha un posto: Primo lo prende, Secondo no
        var primo = crea(segr, "/api/lezioni/" + a + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Primo\",\"cognome\":\"Attesa\"}}");
        var pieno = segr.post("/api/lezioni/" + a + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Secondo\",\"cognome\":\"Attesa\"}}");
        assertThat(pieno.statusCode()).isEqualTo(409);
        assertThat(pieno.body()).contains("lista d'attesa");

        // in lista d'attesa in ordine di arrivo, senza doppioni né iscritti
        var secondo = crea(segr, "/api/lezioni/" + a + "/attesa", "{\"nuovaPersona\":{\"nome\":\"Secondo\",\"cognome\":\"Attesa\",\"dataNascita\":\"2018-03-01\"},\"note\":\"Solo la domenica\"}");
        assertThat(secondo).containsEntry("posizione", 1).containsEntry("note", "Solo la domenica");
        var terzo = crea(segr, "/api/lezioni/" + a + "/attesa", "{\"nuovaPersona\":{\"nome\":\"Terzo\",\"cognome\":\"Attesa\"}}");
        assertThat(terzo).containsEntry("posizione", 2);
        assertThat(segr.post("/api/lezioni/" + a + "/attesa", "{\"personaId\":\"" + secondo.get("personaId") + "\"}").statusCode()).isEqualTo(409);
        assertThat(segr.post("/api/lezioni/" + a + "/attesa", "{\"personaId\":\"" + primo.get("personaId") + "\"}").statusCode()).isEqualTo(409);
        assertThat(come("staff@demo.leone").get("/api/lezioni/" + a + "/attesa").statusCode()).isEqualTo(403);

        String pagina = segr.get("/api/attesa").body();
        assertThat(pagina).contains("\"lezioneId\":\"" + a + "\"").contains("\"inAttesa\":2").contains("\"liberoDal\":null");
        assertThat(segr.get("/api/clienti/" + secondo.get("personaId")).body()).contains("\"attesa\":[{").contains("\"posizione\":1");
        assertThat(segr.get("/api/corsi/" + corso + "/turni").body()).contains("\"occupati\":1").contains("\"liberi\":0");

        // Primo passa al turno B tra una settimana: in A si libera un posto da quel giorno
        assertThat(segr.post("/api/iscrizioni/" + primo.get("iscrizioneId") + "/cambia", "{\"lezioneId\":\"" + a + "\"}").statusCode()).isEqualTo(400);
        var cambio = segr.post("/api/iscrizioni/" + primo.get("iscrizioneId") + "/cambia", "{\"lezioneId\":\"" + b + "\",\"dal\":\"" + fra7 + "\"}");
        assertThat(cambio.statusCode()).as(cambio.body()).isEqualTo(200);
        assertThat(json(cambio)).containsEntry("corsoCambiato", false);
        assertThat(segr.get("/api/attesa").body()).contains("\"liberoDal\":\"" + fra7 + "\"");

        // avviso: senza email non parte; con l'email si registra (posta non configurata) e si segna «avvisata»
        String avviso = "{\"oggetto\":\"Un posto per Secondo\",\"testo\":\"Si è liberato un posto.\"}";
        assertThat(json(segr.post("/api/attesa/" + secondo.get("id") + "/avvisa", avviso))).containsEntry("stato", "SENZA_EMAIL");
        assertThat(segr.put("/api/clienti/" + secondo.get("personaId") + "/contatti", "{\"email\":\"secondo.attesa@example.org\"}").statusCode()).isEqualTo(200);
        assertThat(json(segr.post("/api/attesa/" + secondo.get("id") + "/avvisa", avviso))).containsEntry("stato", "NON_INVIATA");
        assertThat(segr.get("/api/lezioni/" + a + "/attesa").body()).contains("\"avvisataIl\":\"").contains("secondo.attesa@example.org");

        // oggi il turno è ancora pieno; dal giorno in cui si libera, Secondo si iscrive ed esce dalla lista
        assertThat(segr.post("/api/attesa/" + secondo.get("id") + "/iscrivi", "{}").statusCode()).isEqualTo(409);
        crea(segr, "/api/attesa/" + secondo.get("id") + "/iscrivi", "{\"dal\":\"" + fra7 + "\"}");
        assertThat(segr.post("/api/attesa/" + secondo.get("id") + "/rinuncia", "{}").statusCode()).isEqualTo(409);
        String lista = segr.get("/api/lezioni/" + a + "/attesa").body();
        assertThat(lista).doesNotContain("Secondo").contains("Terzo").contains("\"posizione\":1");
        assertThat(segr.post("/api/attesa/" + terzo.get("id") + "/rinuncia", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/lezioni/" + a + "/attesa").body()).isEqualTo("[]");
        assertThat(segr.get("/api/attesa").body()).doesNotContain(a);
    }
}
