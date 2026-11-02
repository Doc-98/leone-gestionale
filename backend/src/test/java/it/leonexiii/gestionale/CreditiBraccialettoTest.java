package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Crediti scritti sul braccialetto, con le macchine non collegate (ADR 0025), nel formato di prova del profilo demo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class CreditiBraccialettoTest {

    static final String PHON = "00000000-0000-0000-0000-000000000a01";
    static final String CODICE = "7B7B7B01";

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

    private String crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return (String) json(r).get("id");
    }

    /** Il blocco 4 del formato di prova con quei crediti. */
    private static String blocco(int crediti) {
        var h = HexFormat.of().withUpperCase();
        return "4C473031" + h.toHexDigits(crediti) + h.toHexDigits(~crediti) + "00000000";
    }

    private static String memoria(String uid, String blocco4) {
        return "{\"uid\":\"" + uid + "\",\"blocchi\":{\"4\":\"" + blocco4 + "\"}}";
    }

    private static String memoria(int crediti) {
        return memoria(CODICE, blocco(crediti));
    }

    @Test
    void ricaricaInDueTempiConsumiEAzzeramento() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String regole = admin.get("/api/borsellino/regole").body();
        try {
            assertThat(regole).contains("\"creditiSu\":\"SERVER\"");
            assertThat(admin.put("/api/borsellino/regole", regole.replace("\"creditiSu\":\"SERVER\"", "\"creditiSu\":\"OVUNQUE\"")).statusCode()).isEqualTo(400);
            var r = admin.put("/api/borsellino/regole", regole.replace("\"creditiSu\":\"SERVER\"", "\"creditiSu\":\"BRACCIALETTO\""));
            assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
            assertThat(json(segr.get("/api/crediti-braccialetto/stato"))).containsEntry("sulBraccialetto", true).containsEntry("formato", "PROVA")
                .containsEntry("pronto", true);

            String persona = crea(segr, "/api/clienti", "{\"nome\":\"Chip\",\"cognome\":\"Prova\",\"dataNascita\":\"1990-01-01\"}");
            String braccialetto = (String) json(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + CODICE + "\"}")).get("id");
            @SuppressWarnings("unchecked")
            String contanti = (String) ((List<Object>) (List<?>) JsonParserFactory.getJsonParser().parseList(segr.get("/api/pagamenti/metodi").body())).stream()
                .map(CreditiBraccialettoTest::mappa).filter(m -> "Contanti".equals(m.get("nome"))).findFirst().orElseThrow().get("id");
            String paga = "\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5}]";

            // con i crediti sul braccialetto la ricarica «dal gestionale» non c'è
            assertThat(segr.post("/api/borsellino/persone/" + persona + "/ricariche", "{\"importo\":5," + paga + "}").statusCode()).isEqualTo(409);

            // un braccialetto mai scritto ha zero crediti; uno diverso da quello scelto non va
            String base = "/api/crediti-braccialetto/" + braccialetto;
            assertThat(json(segr.post(base + "/letture", "{\"memoria\":" + memoria(CODICE, "0".repeat(32)) + "}"))).containsEntry("crediti", 0);
            assertThat(segr.post(base + "/letture", "{\"memoria\":" + memoria("AABBCCDD", blocco(3)) + "}").statusCode()).isEqualTo(409);
            assertThat(segr.post(base + "/letture", "{\"memoria\":" + memoria(CODICE, "1".repeat(32)) + "}").statusCode()).isEqualTo(400);

            // primo tempo: dalla memoria letta il server calcola il blocco da scrivere (5 € = 25 crediti)
            var p = json(segr.post(base + "/ricariche", "{\"importo\":5,\"memoria\":" + memoria(CODICE, "0".repeat(32)) + "}"));
            assertThat(p).containsEntry("creditiPrima", 0).containsEntry("creditiDopo", 25).containsEntry("comprati", 25);
            assertThat(mappa(p.get("scrivi"))).containsEntry("4", blocco(25));
            String op = (String) p.get("operazione");
            assertThat(segr.post(base + "/ricariche", "{\"importo\":5,\"memoria\":" + memoria(0) + "}").statusCode()).isEqualTo(409);
            assertThat(segr.get("/api/crediti-braccialetto/persone/" + persona).body()).contains("\"inSospeso\":{\"operazione\":\"" + op);

            // secondo tempo: se la rilettura non torna non si registra niente; se torna, il pagamento e i crediti
            String conferma = "/api/crediti-braccialetto/ricariche/" + op + "/conferma";
            assertThat(segr.post(conferma, "{\"memoria\":" + memoria(0) + "," + paga + "}").statusCode()).isEqualTo(409);
            var c = segr.post(conferma, "{\"memoria\":" + memoria(25) + "," + paga + "}");
            assertThat(c.statusCode()).as(c.body()).isEqualTo(200);
            assertThat(mappa(json(segr.get("/api/borsellino/persone/" + persona)).get("saldo"))).containsEntry("crediti", 25);
            assertThat(segr.post(conferma, "{\"memoria\":" + memoria(25) + "," + paga + "}").statusCode()).isEqualTo(409);

            // la macchina simulata scala dalla memoria e non dice niente al gestionale
            var m = json(segr.post("/api/crediti-braccialetto/macchina-simulata", "{\"servizioId\":\"" + PHON + "\",\"memoria\":" + memoria(25) + "}"));
            assertThat(m).containsEntry("via", true).containsEntry("crediti", 24);
            assertThat(mappa(m.get("scrivi"))).containsEntry("4", blocco(24));
            assertThat(mappa(json(segr.get("/api/borsellino/persone/" + persona)).get("saldo"))).containsEntry("crediti", 25);
            assertThat(json(segr.post("/api/crediti-braccialetto/macchina-simulata", "{\"servizioId\":\"" + PHON + "\",\"memoria\":" + memoria(0) + "}")))
                .containsEntry("via", false);

            // alla lettura dopo, i crediti spariti sono consumati alle macchine; si possono scrivere anche dal display
            assertThat(json(segr.post(base + "/letture", "{\"memoria\":" + memoria(22) + "}"))).containsEntry("consumati", 3).containsEntry("prima", 25);
            assertThat(json(segr.post(base + "/letture", "{\"crediti\":20}"))).containsEntry("consumati", 2);
            assertThat(mappa(json(segr.get("/api/borsellino/persone/" + persona)).get("saldo"))).containsEntry("crediti", 20);
            assertThat(segr.get("/api/crediti-braccialetto/persone/" + persona).body()).contains("\"crediti\":20,\"fonte\":\"A_MANO\"");

            // una ricarica non scritta si annulla; una già scritta no
            String op2 = (String) json(segr.post(base + "/ricariche", "{\"importo\":5,\"memoria\":" + memoria(20) + "}")).get("operazione");
            String annulla = "/api/crediti-braccialetto/ricariche/" + op2 + "/annulla";
            assertThat(segr.post(annulla, "{\"memoria\":" + memoria(45) + "}").statusCode()).isEqualTo(409);
            assertThat(segr.post(annulla, "{\"memoria\":" + memoria(20) + "}").statusCode()).isEqualTo(204);

            // riconsegna: i crediti letti si restituiscono, poi il braccialetto si azzera per chi lo riceverà
            assertThat(segr.post(base + "/azzeramento", "{\"memoria\":" + memoria(20) + "}").statusCode()).isEqualTo(409);
            var ric = segr.post("/api/accessi/braccialetti/" + braccialetto + "/riconsegna", "{\"cauzione\":\"TRATTIENI\",\"crediti\":\"BORSELLINO\"}");
            assertThat(ric.statusCode()).as(ric.body()).isEqualTo(200);
            var a = json(segr.post(base + "/azzeramento", "{\"memoria\":" + memoria(20) + "}"));
            assertThat(a).containsEntry("creditiPrima", 20);
            assertThat(mappa(a.get("scrivi"))).containsEntry("4", blocco(0));
            assertThat(json(segr.post(base + "/letture", "{\"memoria\":" + memoria(0) + "}"))).containsEntry("consumati", 0);
            assertThat(mappa(json(segr.get("/api/borsellino/persone/" + persona)).get("saldo"))).containsEntry("crediti", 0);
        } finally {
            assertThat(admin.put("/api/borsellino/regole", regole).statusCode()).isEqualTo(200);
        }
    }
}
