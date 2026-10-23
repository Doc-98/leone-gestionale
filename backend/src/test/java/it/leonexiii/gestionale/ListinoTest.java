package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Listino per stagione, prezzi per frequenza e gruppo, regole di prezzo, importo proposto e cambiato (ADR 0018, parti 3–4). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class ListinoTest {

    static final String DELFINO_GIALLO = "00000000-0000-0000-0000-000000000615";
    static final String ALUNNI = "00000000-0000-0000-0000-000000000901";

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
    void listinoRegoleEImportoProposto() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String stagione = crea(admin, "/api/stagioni", "{\"nome\":\"2050/51\",\"dal\":\"2050-09-01\",\"al\":\"2051-08-31\",\"quota\":35}");

        // offerte: un corso con prezzo per frequenza e per gruppo; il carnet e gli «altri importi»
        String corso = """
            {"stagioneId":"%s","nome":"Prova listino","tipo":"CORSO","corsi":[%s],"riduzioneTardiva":true,
             "prezzi":[{"volte":1,"importo":500},{"volte":2,"importo":800},{"volte":1,"gruppoId":"%s","importo":450}]}""";
        assertThat(segr.post("/api/listino/offerte", corso.formatted(stagione, "\"" + DELFINO_GIALLO + "\"", ALUNNI)).statusCode()).isEqualTo(403);
        assertThat(admin.post("/api/listino/offerte", corso.formatted(stagione, "", ALUNNI)).statusCode()).isEqualTo(400);
        String offerta = (String) json(admin.post("/api/listino/offerte", corso.formatted(stagione, "\"" + DELFINO_GIALLO + "\"", ALUNNI))).get("id");
        String altro = crea(admin, "/api/listino/offerte", "{\"stagioneId\":\"" + stagione + "\",\"nome\":\"Quota gara\",\"tipo\":\"ALTRO\",\"prezzi\":[{\"importo\":50}]}");
        crea(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Carnet prova","tipo":"CARNET","ingressi":10,"validitaMesi":3,"prezzi":[{"importo":130}]}""".formatted(stagione));

        // regole della stagione
        assertThat(admin.put("/api/listino/regole/" + stagione + "/RIDUZIONE_TARDIVA",
            "{\"attiva\":true,\"parametri\":{\"scaglioni\":[{\"dal\":\"2050-11-01\",\"percentuale\":150}]}}").statusCode()).isEqualTo(400);
        assertThat(admin.put("/api/listino/regole/" + stagione + "/RIDUZIONE_TARDIVA",
            "{\"attiva\":true,\"parametri\":{\"scaglioni\":[{\"dal\":\"2051-01-01\",\"percentuale\":30},{\"dal\":\"2050-11-01\",\"percentuale\":10}]}}").statusCode()).isEqualTo(200);
        assertThat(admin.put("/api/listino/regole/" + stagione + "/RATE",
            "{\"attiva\":true,\"parametri\":{\"numero\":2,\"primaPercentuale\":50,\"scadenze\":[\"2050-09-15\"]}}").statusCode()).isEqualTo(200);
        assertThat(admin.put("/api/listino/regole/" + stagione + "/RECESSO",
            "{\"attiva\":true,\"parametri\":{\"nelBorsellino\":true,\"scaglioni\":[{\"finoAl\":\"2050-09-30\",\"percentuale\":90},{\"finoAl\":\"2050-12-31\",\"percentuale\":50}]}}").statusCode()).isEqualTo(200);
        String pagina = segr.get("/api/listino?stagione=" + stagione).body();
        assertThat(pagina).contains("Prova listino").contains("\"tipo\":\"RIDUZIONE_TARDIVA\",\"attiva\":true").contains("{\"dal\":\"2050-11-01\",\"percentuale\":10}");

        // importo proposto: listino, riduzione per iscrizione tardiva, rate
        String persona = crea(segr, "/api/clienti", "{\"nome\":\"Listino\",\"cognome\":\"Prova\",\"dataNascita\":\"2018-02-02\"}");
        var p1 = json(segr.get("/api/clienti/" + persona + "/proposta?offertaId=" + offerta + "&volte=1&dal=2050-09-10"));
        assertThat(p1).containsEntry("listino", 500.00).containsEntry("proposto", 500.00).containsEntry("riduzionePercentuale", 0);
        assertThat(p1.get("rate").toString()).contains("importo=250.0").contains("scadenza=2050-09-15");
        var tardiva = json(segr.get("/api/clienti/" + persona + "/proposta?offertaId=" + offerta + "&volte=1&dal=2050-11-15"));
        assertThat(tardiva).containsEntry("riduzionePercentuale", 10).containsEntry("proposto", 450.00);
        // la scadenza della seconda rata è già passata: si paga tutto subito
        assertThat(tardiva.get("rate").toString()).isEqualTo("[]");
        assertThat(json(segr.get("/api/clienti/" + persona + "/proposta?offertaId=" + offerta + "&volte=2&dal=2051-01-10")))
            .containsEntry("proposto", 560.00);

        // negli «Alunni dell'Istituto» vale il loro prezzo, se più basso
        crea(segr, "/api/clienti/" + persona + "/gruppi", "{\"gruppoId\":\"" + ALUNNI + "\"}");
        var p2 = json(segr.get("/api/clienti/" + persona + "/proposta?offertaId=" + offerta + "&volte=1&dal=2050-09-10"));
        assertThat(p2).containsEntry("proposto", 450.00).containsEntry("prezzoBase", 500.00).containsEntry("gruppoId", ALUNNI);

        // vendita dal listino: l'importo proposto; uno diverso solo con il motivo
        String abb = crea(segr, "/api/clienti/" + persona + "/abbonamenti", "{\"offertaId\":\"" + offerta + "\",\"volteASettimana\":1,\"dal\":\"2050-09-10\"}");
        assertThat(segr.post("/api/clienti/" + persona + "/abbonamenti", "{\"offertaId\":\"" + offerta + "\",\"volteASettimana\":2,\"dal\":\"2050-09-10\",\"importo\":600}").statusCode()).isEqualTo(400);
        crea(segr, "/api/clienti/" + persona + "/abbonamenti", "{\"offertaId\":\"" + offerta + "\",\"volteASettimana\":2,\"dal\":\"2050-09-10\",\"importo\":600,\"motivoImporto\":\"Fratello iscritto\"}");
        assertThat(segr.post("/api/clienti/" + persona + "/abbonamenti", "{\"offertaId\":\"" + altro + "\",\"dal\":\"2050-09-10\"}").statusCode()).isEqualTo(400);
        String scheda = segr.get("/api/clienti/" + persona).body();
        assertThat(scheda).contains("\"importo\":450.00").contains("\"importoListino\":450.00").contains("\"motivoImporto\":\"Fratello iscritto\"")
            .contains("\"al\":\"2051-08-31\"");

        // senza il permesso «Cambiare l'importo proposto» l'importo resta quello proposto
        var ruolo = admin.post("/api/ruoli", "{\"nome\":\"Cassa senza sconti\",\"copiaDa\":\"STAFF\"}");
        String codice = (String) json(ruolo).get("codice");
        assertThat(admin.put("/api/ruoli/" + codice, "{\"nome\":\"Cassa senza sconti\",\"permessi\":[\"CLIENTI\"]}").statusCode()).isEqualTo(204);
        @SuppressWarnings("unchecked")
        var marta = ((List<Map<String, Object>>) (List<?>) JsonParserFactory.getJsonParser().parseList(admin.get("/api/account").body())).stream()
            .filter(a -> "marta@demo.leone".equals(a.get("email"))).findFirst().orElseThrow();
        assertThat(admin.post("/api/ruoli/" + codice + "/assegnazioni", "{\"accountId\":\"" + marta.get("id") + "\"}").statusCode()).isEqualTo(201);
        var cassa = come("marta@demo.leone");
        assertThat(cassa.post("/api/clienti/" + persona + "/abbonamenti",
            "{\"offertaId\":\"" + offerta + "\",\"volteASettimana\":1,\"dal\":\"2050-10-01\",\"importo\":1,\"motivoImporto\":\"Sconto\"}").statusCode()).isEqualTo(403);

        // recesso: rimborso secondo la regola, nel borsellino
        var rimborso = json(segr.get("/api/clienti/abbonamenti/" + abb + "/recesso?data=2050-10-15"));
        assertThat(rimborso).containsEntry("percentuale", 50).containsEntry("importo", 225.00).containsEntry("nelBorsellino", true);
        assertThat(segr.get("/api/clienti/abbonamenti/" + abb + "/recesso?data=2051-02-01").body()).contains("\"percentuale\":0");

        // un'offerta già venduta non si elimina; il listino si copia nella stagione nuova con l'aumento
        assertThat(admin.delete("/api/listino/offerte/" + offerta).statusCode()).isEqualTo(409);
        String nuova = crea(admin, "/api/stagioni", "{\"nome\":\"2051/52\",\"dal\":\"2051-09-01\",\"al\":\"2052-08-31\"}");
        var copia = admin.post("/api/listino/copia", "{\"da\":\"" + stagione + "\",\"a\":\"" + nuova + "\",\"aumentoPercentuale\":10}");
        assertThat(copia.statusCode()).as(copia.body()).isEqualTo(200);
        assertThat(json(copia)).containsEntry("offerte", 3);
        assertThat(admin.get("/api/listino?stagione=" + nuova).body()).contains("\"importo\":550.00").contains("\"importo\":143.00")
            .contains("{\"dal\":\"2051-11-01\",\"percentuale\":10}").contains("\"2051-09-15\"");
    }
}
