package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Scheda completa: anagrafica con codice fiscale, tutori e nucleo famiglia, consensi, delegati all'uscita (ADR 0014). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class SchedaCompletaTest {

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

    private String crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return (String) json(r).get("id");
    }

    @Test
    void anagraficaTutoriFamigliaEConsensi() throws Exception {
        var segr = come("segreteria@demo.leone");
        String figlio = crea(segr, "/api/clienti", "{\"nome\":\"Nereo\",\"cognome\":\"Prova\",\"dataNascita\":\"2018-05-10\"}");

        // anagrafica: il codice fiscale si controlla (anche l'ultimo carattere) e non si duplica
        String anag = """
            {"nome":"Nereo","cognome":"Prova","dataNascita":"2018-05-10","sesso":"M","luogoNascita":"Pescara","codiceFiscale":"%s",
             "indirizzo":"Via dei Pini 3","cap":"65100","citta":"Pescara","provincia":"pe","nazione":"Italia"}""";
        assertThat(segr.put("/api/clienti/" + figlio + "/anagrafica", anag.formatted("PRVNRE18E10G482A")).statusCode()).isEqualTo(400);
        var ok = segr.put("/api/clienti/" + figlio + "/anagrafica", anag.formatted("prvnre18e10g482y"));
        assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);
        assertThat(json(ok).get("codiceFiscale")).isEqualTo("PRVNRE18E10G482Y");
        assertThat(json(ok).get("provincia")).isEqualTo("PE");
        assertThat(segr.get("/api/persone?q=PRVNRE18E10G482Y").body()).contains("Nereo");
        String altro = crea(segr, "/api/clienti", "{\"nome\":\"Doppio\",\"cognome\":\"Prova\"}");
        assertThat(segr.put("/api/clienti/" + altro + "/anagrafica", anag.formatted("PRVNRE18E10G482Y").replace("Nereo", "Doppio")).statusCode()).isEqualTo(409);

        // tutore nuovo: la madre riceve le comunicazioni ed è delegata al ritiro
        crea(segr, "/api/clienti/" + figlio + "/tutori", """
            {"nome":"Paola","cognome":"Prova","codiceFiscale":"PRVPLA85M41G482T","email":"paola.prova@example.org","telefono":"333 7654321","relazione":"MADRE"}""");
        assertThat(segr.post("/api/clienti/" + figlio + "/tutori", "{\"codiceFiscale\":\"PRVPLA85M41G482T\",\"relazione\":\"MADRE\"}").statusCode()).isEqualTo(409);
        var scheda = segr.get("/api/clienti/" + figlio).body();
        assertThat(scheda).contains("\"relazione\":\"MADRE\"").contains("paola.prova@example.org").contains("\"destinatari\":[\"paola.prova@example.org\"]")
            .contains("\"sesso\":\"M\"").contains("Via dei Pini 3");

        // una sorella con lo stesso tutore (trovato per codice fiscale): nucleo famiglia
        String sorella = crea(segr, "/api/clienti", "{\"nome\":\"Lia\",\"cognome\":\"Prova\",\"dataNascita\":\"2020-06-15\"}");
        String legame = crea(segr, "/api/clienti/" + sorella + "/tutori", "{\"codiceFiscale\":\"PRVPLA85M41G482T\",\"relazione\":\"MADRE\",\"delegatoRitiro\":false}");
        assertThat(segr.get("/api/clienti/" + figlio).body()).contains("\"fratelli\":[{").contains("\"nome\":\"Lia\"");
        @SuppressWarnings("unchecked")
        var famiglia = (Map<String, Object>) json(segr.get("/api/clienti/" + sorella)).get("famiglia");
        String madre = (String) ((Map<?, ?>) ((java.util.List<?>) famiglia.get("tutori")).getFirst()).get("personaId");
        assertThat(segr.get("/api/clienti/" + madre).body()).contains("\"assistiti\":[{").contains("Nereo").contains("Lia");

        // senza comunicazioni al tutore, si scrive alla persona (se ha un'email)
        assertThat(segr.put("/api/clienti/legami/" + legame, "{\"relazione\":\"MADRE\",\"comunicazioni\":false,\"delegatoRitiro\":false}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/clienti/" + sorella).body()).contains("\"destinatari\":[]");
        assertThat(segr.post("/api/clienti/legami/" + legame + "/chiudi", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/clienti/" + sorella).body()).contains("\"tutori\":[]");

        // consensi
        crea(segr, "/api/clienti/" + figlio + "/consensi", "{\"tipo\":\"TRATTAMENTO\",\"firmatoDa\":\"Paola Prova\",\"validoFino\":\"2099-12-31\"}");
        assertThat(segr.post("/api/clienti/" + figlio + "/consensi", "{\"tipo\":\"MARKETING\",\"firmatoDa\":\"Paola Prova\"}").statusCode()).isEqualTo(400);
        String imm = crea(segr, "/api/clienti/" + figlio + "/consensi", "{\"tipo\":\"IMMAGINI\",\"firmatoDa\":\"Paola Prova\"}");
        assertThat(segr.post("/api/clienti/consensi/" + imm + "/revoca", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/clienti/" + figlio).body()).contains("\"tipo\":\"TRATTAMENTO\"").contains("\"valido\":true").contains("\"valido\":false");
        assertThat(come("staff@demo.leone").put("/api/clienti/" + figlio + "/anagrafica", anag.formatted("PRVNRE18E10G482Y")).statusCode()).isEqualTo(403);

        // all'uscita il tornello dice con chi può uscire
        var admin = come("admin@demo.leone");
        crea(segr, "/api/accessi/persone/" + figlio + "/braccialetti", "{\"codice\":\"ABCD1234EF\"}");
        assertThat(admin.put("/api/accessi/regole/USCITA", "{\"attiva\":true}").statusCode()).isEqualTo(204);
        try {
            var r = come("giulia@demo.leone").post("/api/accessi/passaggi", "{\"codice\":\"ABCD1234EF\",\"verso\":\"USCITA\"}");
            assertThat(r.body()).contains("USCITA_NON_AUTORIZZATA").contains("Può uscire con: Paola Prova (madre).");
        } finally {
            admin.put("/api/accessi/regole/USCITA", "{\"attiva\":false}");
        }
    }
}
