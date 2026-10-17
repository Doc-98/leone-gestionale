package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class AccessoETurniTest {

    @LocalServerPort
    int porta;

    private static Map<String, Object> mappa(String json) {
        return JsonParserFactory.getJsonParser().parseMap(json);
    }

    private static List<Object> lista(String json) {
        return JsonParserFactory.getJsonParser().parseList(json);
    }

    @Test
    void senzaLoginLeApiRispondono401() throws Exception {
        var c = new ClientApi(porta);
        assertThat(c.get("/api/me").statusCode()).isEqualTo(401);
        assertThat(c.get("/api/turni/miei").statusCode()).isEqualTo(401);
    }

    @Test
    void loginSenzaTokenCsrfVieneRifiutato() throws Exception {
        var c = new ClientApi(porta);
        var r = c.post("/api/auth/login", "{\"email\":\"staff@demo.leone\",\"password\":\"demo1234\"}", false);
        assertThat(r.statusCode()).isEqualTo(403);
    }

    @Test
    void passwordSbagliata401() throws Exception {
        var c = new ClientApi(porta);
        var r = c.login("staff@demo.leone", "sbagliata");
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.body()).contains("Email o password non corretti");
    }

    @Test
    void loginStaffEISuoiTurni() throws Exception {
        var c = new ClientApi(porta);
        var login = c.login("STAFF@demo.leone", "demo1234");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(mappa(login.body())).containsEntry("nome", "Luca");

        var me = c.get("/api/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"STAFF\"").doesNotContain("passwordHash");

        var miei = c.get("/api/turni/miei");
        assertThat(miei.statusCode()).isEqualTo(200);
        List<Object> turni = lista(miei.body());
        // Luca ha tre turni a settimana nei dati demo: nei prossimi 30 giorni almeno dieci
        assertThat(turni).hasSizeGreaterThanOrEqualTo(10);
        @SuppressWarnings("unchecked")
        var primo = (Map<String, Object>) turni.get(0);
        assertThat(primo).containsKeys("data", "inizio", "fine", "ore", "mansione", "postazione");
    }

    @Test
    void elencoDiTuttiSoloPerCoordinatoriEAdmin() throws Exception {
        var staff = new ClientApi(porta);
        staff.login("staff@demo.leone", "demo1234");
        assertThat(staff.get("/api/turni?da=2026-01-01&a=2026-01-31").statusCode()).isEqualTo(403);

        var coord = new ClientApi(porta);
        coord.login("coordinatore@demo.leone", "demo1234");
        var r = coord.get("/api/turni?da=" + java.time.LocalDate.now() + "&a=" + java.time.LocalDate.now().plusDays(13));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"persona\"").contains("Bianchi");
    }

    @Test
    void intervalloNonValido400() throws Exception {
        var c = new ClientApi(porta);
        c.login("staff@demo.leone", "demo1234");
        assertThat(c.get("/api/turni/miei?da=2026-10-10&a=2026-10-01").statusCode()).isEqualTo(400);
        assertThat(c.get("/api/turni/miei?da=2026-01-01&a=2026-12-31").statusCode()).isEqualTo(400);
    }

    @Test
    void logoutChiudeLaSessione() throws Exception {
        var c = new ClientApi(porta);
        c.login("staff@demo.leone", "demo1234");
        assertThat(c.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(c.post("/api/auth/logout", "", true).statusCode()).isEqualTo(200);
        assertThat(c.get("/api/me").statusCode()).isEqualTo(401);
    }
}
