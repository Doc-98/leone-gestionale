package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Account dello staff: password temporanea da cambiare, disattivazione immediata, reimpostazione (ADR 0012). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class AccountTest {

    @LocalServerPort
    int porta;

    private ClientApi come(String email, String password) throws Exception {
        var c = new ClientApi(porta);
        var r = c.login(email, password);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return c;
    }

    private static Map<String, Object> json(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseMap(r.body());
    }

    @Test
    void unNuovoAccountCambiaLaPasswordESiPuoDisattivare() throws Exception {
        var admin = come("admin@demo.leone", "demo1234");
        String email = "staff." + UUID.randomUUID().toString().substring(0, 8) + "@demo.leone";
        assertThat(come("segreteria@demo.leone", "demo1234").get("/api/account").statusCode()).isEqualTo(403);

        var r = admin.post("/api/account", "{\"nome\":\"Nuovo\",\"cognome\":\"Assunto\",\"email\":\"" + email + "\",\"ruolo\":\"STAFF\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String id = (String) json(r).get("accountId");
        String temporanea = (String) json(r).get("password");
        assertThat(temporanea).matches("[a-z2-9]{4}-[a-z2-9]{4}-[a-z2-9]{4}");
        assertThat(admin.post("/api/account", "{\"nome\":\"Altro\",\"cognome\":\"Doppione\",\"email\":\"" + email.toUpperCase() + "\"}").statusCode()).isEqualTo(409);
        assertThat(admin.get("/api/account").body()).contains(email).contains("\"ruolo\":\"STAFF\"");

        // con la password temporanea si può solo cambiarla
        var nuovo = come(email, temporanea);
        assertThat(nuovo.get("/api/me").body()).contains("\"deveCambiarePassword\":true");
        assertThat(nuovo.get("/api/turni/miei").statusCode()).isEqualTo(403);
        assertThat(nuovo.put("/api/me/password", "{\"attuale\":\"sbagliata\",\"nuova\":\"unaPasswordLunga1\"}").statusCode()).isEqualTo(400);
        assertThat(nuovo.put("/api/me/password", "{\"attuale\":\"" + temporanea + "\",\"nuova\":\"corta\"}").statusCode()).isEqualTo(400);
        assertThat(nuovo.put("/api/me/password", "{\"attuale\":\"" + temporanea + "\",\"nuova\":\"unaPasswordLunga1\"}").statusCode()).isEqualTo(204);
        assertThat(nuovo.get("/api/turni/miei").statusCode()).isEqualTo(200);
        assertThat(nuovo.get("/api/me").body()).contains("\"deveCambiarePassword\":false");

        // disattivato: fuori subito, e non rientra
        assertThat(admin.post("/api/account/" + id + "/disattiva", "{}").statusCode()).isEqualTo(204);
        assertThat(nuovo.get("/api/turni/miei").statusCode()).isEqualTo(401);
        assertThat(new ClientApi(porta).login(email, "unaPasswordLunga1").statusCode()).isEqualTo(401);
        assertThat(admin.post("/api/account/" + id + "/riattiva", "{}").statusCode()).isEqualTo(204);
        come(email, "unaPasswordLunga1");

        // password reimpostata: la vecchia non vale più, la nuova è temporanea
        String altra = (String) json(admin.post("/api/account/" + id + "/password", "{}")).get("password");
        assertThat(new ClientApi(porta).login(email, "unaPasswordLunga1").statusCode()).isEqualTo(401);
        assertThat(come(email, altra).get("/api/me").body()).contains("\"deveCambiarePassword\":true");

        // nessuno disattiva se stesso; l'ultimo accesso si registra
        assertThat(admin.post("/api/account/00000000-0000-0000-0000-000000000201/disattiva", "{}").statusCode()).isEqualTo(409);
        assertThat(admin.get("/api/account").body()).contains("\"ultimoAccesso\":\"20");
    }
}
