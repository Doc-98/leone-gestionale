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

/** Il telefono come lettore NFC del PC: abbinamento, letture inviate senza accesso, solo il PC le riceve (ADR 0017). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class LettoreTelefonoTest {

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

    @Test
    void ilTelefonoMandaICodiciAlPc() throws Exception {
        var pc = come("segreteria@demo.leone");
        var r = pc.post("/api/lettori", "{}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String id = (String) json(r).get("id");
        String token = (String) json(r).get("token");
        String codice = (String) json(r).get("codice");
        assertThat(codice).matches("[0-9]{6}");
        assertThat(token).hasSizeGreaterThanOrEqualTo(30);

        // il telefono non ha fatto l'accesso: trova il token dal codice, si presenta e manda i codici letti
        var telefono = new ClientApi(porta);
        assertThat(json(telefono.get("/api/lettori/codice/" + codice))).containsEntry("token", token);
        assertThat(telefono.get("/api/lettori/codice/000000x").statusCode()).isEqualTo(404);
        assertThat(pc.get("/api/lettori/" + id + "/letture").body()).contains("\"telefonoVistoIl\":null");
        assertThat(telefono.post("/api/lettori/telefono/" + token + "/presente", "{}").statusCode()).isEqualTo(204);
        assertThat(telefono.post("/api/lettori/telefono/" + token + "/letture", "{\"codice\":\"d0:00:00:02\"}").statusCode()).isEqualTo(204);
        assertThat(telefono.post("/api/lettori/telefono/" + token + "/letture", "{\"codice\":\"04:a7:c3:1b:5e:6f:80\"}").statusCode()).isEqualTo(204);
        assertThat(telefono.post("/api/lettori/telefono/" + token + "/letture", "{\"codice\":\"?\"}").statusCode()).isEqualTo(400);
        assertThat(telefono.post("/api/lettori/telefono/sbagliato/letture", "{\"codice\":\"D0000002\"}").statusCode()).isEqualTo(404);
        // ma non vede niente del gestionale
        assertThat(telefono.get("/api/lettori/" + id + "/letture").statusCode()).isEqualTo(401);
        assertThat(telefono.get("/api/accessi/passaggi").statusCode()).isEqualTo(401);

        // il PC riceve i codici in ordine, normalizzati; poi solo i nuovi
        String tutte = pc.get("/api/lettori/" + id + "/letture").body();
        assertThat(tutte).contains("\"numero\":1,\"codice\":\"D0000002\"").contains("\"numero\":2,\"codice\":\"04A7C31B5E6F80\"")
            .doesNotContain("\"telefonoVistoIl\":null");
        assertThat(pc.get("/api/lettori/" + id + "/letture?dopo=2").body()).contains("\"letture\":[]");

        // un altro utente non vede l'abbinamento; chiuso dal PC, il telefono non può più mandare
        assertThat(come("staff@demo.leone").get("/api/lettori/" + id + "/letture").statusCode()).isEqualTo(404);
        assertThat(pc.delete("/api/lettori/" + id).statusCode()).isEqualTo(204);
        assertThat(telefono.post("/api/lettori/telefono/" + token + "/letture", "{\"codice\":\"D0000002\"}").statusCode()).isEqualTo(404);
        assertThat(telefono.get("/api/lettori/codice/" + codice).statusCode()).isEqualTo(404);
    }
}
