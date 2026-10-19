package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Ruoli come dati: permessi modificabili, ruoli nuovi, assegnazioni che valgono subito (ADR 0010). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class RuoliTest {

    static final String ACCOUNT_LUCA = "00000000-0000-0000-0000-000000000203";
    static final String ACCOUNT_ADMIN = "00000000-0000-0000-0000-000000000201";

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mappa(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void laSegreteriaGestisceTuttoTranneRuoliERegole() throws Exception {
        var segr = come("segreteria@demo.leone");
        var me = segr.get("/api/me").body();
        assertThat(me).contains("\"PIANIFICAZIONE\"").contains("\"GOLDEN_PASS\"").contains("\"CLIENTI\"").contains("\"admin\":false")
            .doesNotContain("\"RUOLI\"").doesNotContain("\"REGOLE_TORNELLO\"");
        LocalDate oggi = LocalDate.now(ZoneId.of("Europe/Rome"));
        assertThat(segr.get("/api/turni?da=" + oggi + "&a=" + oggi.plusDays(6)).statusCode()).isEqualTo(200);
        assertThat(segr.get("/api/riepilogo?mese=" + oggi.toString().substring(0, 7)).statusCode()).isEqualTo(200);
        assertThat(segr.get("/api/ruoli").statusCode()).isEqualTo(403);
        assertThat(segr.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":true}").statusCode()).isEqualTo(403);
        assertThat(come("coordinatore@demo.leone").get("/api/ruoli").statusCode()).isEqualTo(403);
        assertThat(come("admin@demo.leone").get("/api/me").body()).contains("\"admin\":true").contains("\"RUOLI\"");
    }

    @Test
    void unRuoloNuovoConIlSuoPermessoValeSubito() throws Exception {
        var admin = come("admin@demo.leone");
        var luca = come("staff@demo.leone");
        var pagina = json(admin.get("/api/ruoli"));
        assertThat(pagina.get("ruoli").toString()).contains("codice=ADMIN").contains("codice=SEGRETERIA");
        assertThat(pagina.get("permessi").toString()).contains("codice=PRESENZE");

        // un turno di un altro istruttore (Delfino Verde, l'ultimo lunedì): Luca non ne vede la lista
        LocalDate lunedi = LocalDate.now(ZoneId.of("Europe/Rome")).with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        @SuppressWarnings("unchecked")
        var verde = ((List<Object>) json(luca.get("/api/piano?da=" + lunedi + "&a=" + lunedi)).get("occorrenze")).stream().map(RuoliTest::mappa)
            .filter(o -> "Delfino Verde".equals(o.get("titolo"))).findFirst().orElseThrow();
        String lista = "/api/lezioni/" + verde.get("lezioneId") + "/iscritti?data=" + lunedi;
        assertThat(luca.get(lista).statusCode()).isEqualTo(403);

        var r = admin.post("/api/ruoli", "{\"nome\":\"Istruttore senior\",\"descrizione\":\"Segna le presenze di tutti\",\"copiaDa\":\"STAFF\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String codice = (String) json(r).get("codice");
        assertThat(codice).startsWith("ISTRUTTORE_SENIOR");
        assertThat(admin.put("/api/ruoli/" + codice, "{\"nome\":\"Istruttore senior\",\"permessi\":[\"TURNI\",\"PRESENZE\"]}").statusCode()).isEqualTo(204);
        var a = admin.post("/api/ruoli/" + codice + "/assegnazioni", "{\"accountId\":\"" + ACCOUNT_LUCA + "\"}");
        assertThat(a.statusCode()).as(a.body()).isEqualTo(201);
        assertThat(admin.post("/api/ruoli/" + codice + "/assegnazioni", "{\"accountId\":\"" + ACCOUNT_LUCA + "\"}").statusCode()).isEqualTo(409);

        // vale subito, senza rifare il login
        var ora = luca.get(lista);
        assertThat(ora.statusCode()).isEqualTo(200);
        assertThat(ora.body()).contains("\"puoSegnarePresenze\":true").contains("\"puoIscrivere\":false");

        // tolto il ruolo, torna come prima; il ruolo resta nello storico e non si elimina
        assertThat(admin.delete("/api/assegnazioni/" + json(a).get("id")).statusCode()).isEqualTo(204);
        assertThat(luca.get(lista).statusCode()).isEqualTo(403);
        assertThat(admin.delete("/api/ruoli/STAFF").statusCode()).isEqualTo(409);

        // un ruolo mai assegnato si elimina
        String vuoto = (String) json(admin.post("/api/ruoli", "{\"nome\":\"Prova vuota\"}")).get("codice");
        assertThat(admin.delete("/api/ruoli/" + vuoto).statusCode()).isEqualTo(204);

        // ADMIN ha sempre tutto; nessuno toglie a se stesso il ruolo di admin
        assertThat(admin.put("/api/ruoli/ADMIN", "{\"nome\":\"Admin\",\"permessi\":[\"CLIENTI\"]}").statusCode()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        var admins = ((List<Object>) json(admin.get("/api/ruoli")).get("ruoli")).stream().map(RuoliTest::mappa)
            .filter(x -> "ADMIN".equals(x.get("codice"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        String mia = ((List<Object>) admins.get("persone")).stream().map(RuoliTest::mappa)
            .filter(x -> ACCOUNT_ADMIN.equals(x.get("accountId"))).map(x -> (String) x.get("assegnazioneId")).findFirst().orElseThrow();
        assertThat(admin.delete("/api/assegnazioni/" + mia).statusCode()).isEqualTo(409);
    }
}
