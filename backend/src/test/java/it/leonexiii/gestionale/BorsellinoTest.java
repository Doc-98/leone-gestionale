package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Borsellino, cauzione del braccialetto e macchine di phon e docce (ADR 0018, parti 7–9). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class BorsellinoTest {

    static final String PHON_M = "00000000-0000-0000-0000-000000000a11";
    static final String DOCCE_M = "00000000-0000-0000-0000-000000000a14";
    static final String SALDO = "00000000-0000-0000-0000-000000000a16";

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

    private String metodo(ClientApi c, String nome) throws Exception {
        return (String) JsonParserFactory.getJsonParser().parseList(c.get("/api/pagamenti/metodi").body()).stream().map(BorsellinoTest::mappa)
            .filter(m -> nome.equals(m.get("nome"))).findFirst().orElseThrow().get("id");
    }

    private Map<String, Object> saldo(ClientApi c, String persona) throws Exception {
        return mappa(json(c.get("/api/borsellino/persone/" + persona)).get("saldo"));
    }

    /** Come una macchina vera: niente sessione, solo la chiave del dispositivo. */
    private HttpResponse<String> macchina(String dispositivo, String chiave, String codice) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/macchine/" + dispositivo + "/utilizzi"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"codice\":\"" + codice + "\",\"postazione\":\"3\"}"));
        if (chiave != null) b.header("X-Chiave-Dispositivo", chiave);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ricaricaMacchineCauzioneERiconsegna() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String persona = crea(segr, "/api/clienti", "{\"nome\":\"Phon\",\"cognome\":\"Prova\",\"dataNascita\":\"1990-01-01\"}");
        // il braccialetto: la cauzione (10 € nelle regole demo) diventa un addebito
        var b = json(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"7A7A7A01\"}"));
        String braccialetto = (String) b.get("id");
        var conto = json(segr.get("/api/pagamenti/persone/" + persona));
        @SuppressWarnings("unchecked")
        var addebiti = (List<Map<String, Object>>) conto.get("addebiti");
        var voceCauzione = addebiti.stream().filter(a -> "CAUZIONE".equals(a.get("origine"))).findFirst().orElseThrow();
        assertThat(voceCauzione).containsEntry("importo", 10.00);
        String contanti = metodo(segr, "Contanti");
        crea(segr, "/api/pagamenti", "{\"addebiti\":[\"" + voceCauzione.get("id") + "\"],\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":10}]}");

        // ricarica: minimo 5 €, multipli di 0,20 €
        String ricarica = "/api/borsellino/persone/" + persona + "/ricariche";
        assertThat(segr.post(ricarica, "{\"importo\":3,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":3}]}").statusCode()).isEqualTo(400);
        assertThat(segr.post(ricarica, "{\"importo\":5.10,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5.10}]}").statusCode()).isEqualTo(400);
        assertThat(segr.post(ricarica, "{\"importo\":1.00,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":1}]}").statusCode()).isEqualTo(400);
        assertThat(segr.post(ricarica, "{\"importo\":5,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5}]}").statusCode()).isEqualTo(201);
        assertThat(saldo(segr, persona)).containsEntry("crediti", 25);

        // le macchine: senza chiave no; doccia: la prima del giorno gratis, poi un credito; phon: un credito
        assertThat(macchina(PHON_M, null, "7A7A7A01").statusCode()).isEqualTo(401);
        assertThat(macchina(PHON_M, "sbagliata", "7A7A7A01").statusCode()).isEqualTo(401);
        var d1 = json(macchina(DOCCE_M, "demo-docce-m", "7A:7A:7A:01"));
        assertThat(d1).containsEntry("via", true).containsEntry("esito", "GRATIS").containsEntry("crediti", 25).containsEntry("nome", "Phon");
        assertThat(json(macchina(DOCCE_M, "demo-docce-m", "7A7A7A01"))).containsEntry("esito", "OK").containsEntry("crediti", 24);
        assertThat(json(macchina(PHON_M, "demo-phon-m", "7A7A7A01"))).containsEntry("esito", "OK").containsEntry("crediti", 23).containsEntry("minuti", 5);
        assertThat(json(macchina(PHON_M, "demo-phon-m", "FFFFFF99"))).containsEntry("via", false).containsEntry("esito", "SCONOSCIUTO");
        // il lettore del saldo
        var s = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/macchine/" + SALDO + "/saldo?codice=7A7A7A01"))
            .header("X-Chiave-Dispositivo", "demo-saldo").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(json(s)).containsEntry("crediti", 23);
        // il simulatore (utente collegato) fa come la macchina e segna l'utilizzo come simulato
        var prova = json(segr.post("/api/dispositivi/" + PHON_M + "/prova", "{\"codice\":\"7A7A7A01\",\"azione\":\"USA\"}"));
        assertThat(prova).containsEntry("crediti", 22);
        assertThat(segr.get("/api/utilizzi?personaId=" + persona).body()).contains("\"simulato\":true").contains("\"esito\":\"GRATIS\"");

        // pagare un addebito con il credito in euro: serve il credito
        String borsellino = metodo(segr, "Borsellino");
        String cuffia = crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Cuffia\",\"importo\":8}");
        var senza = segr.post("/api/pagamenti", "{\"addebiti\":[\"" + cuffia + "\"],\"metodi\":[{\"metodoId\":\"" + borsellino + "\",\"importo\":8}]}");
        assertThat(senza.statusCode()).isEqualTo(400);
        assertThat(senza.body()).contains("non bastano");
        assertThat(coord.post("/api/borsellino/persone/" + persona + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":20,\"motivo\":\"Rimborso a mano\"}").statusCode()).isEqualTo(204);
        assertThat(segr.post("/api/borsellino/persone/" + persona + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":20,\"motivo\":\"x\"}").statusCode()).isEqualTo(403);
        String pag = crea(segr, "/api/pagamenti", "{\"addebiti\":[\"" + cuffia + "\"],\"metodi\":[{\"metodoId\":\"" + borsellino + "\",\"importo\":8}]}");
        assertThat(saldo(segr, persona)).containsEntry("euro", 12.00);
        assertThat(coord.post("/api/pagamenti/" + pag + "/annulla", "{\"motivo\":\"Prova\"}").statusCode()).isEqualTo(204);
        assertThat(saldo(segr, persona)).containsEntry("euro", 20.00);

        // restituire in denaro: le regole demo non lo permettono
        assertThat(coord.post("/api/borsellino/persone/" + persona + "/restituzioni", "{\"importo\":5}").statusCode()).isEqualTo(409);

        // riconsegna dell'ultimo braccialetto: cauzione in denaro, crediti nel borsellino (22 × 0,20 = 4,40 €)
        var esito = json(segr.post("/api/accessi/braccialetti/" + braccialetto + "/riconsegna", "{\"cauzione\":\"DENARO\",\"crediti\":\"BORSELLINO\"}"));
        assertThat(esito).containsEntry("cauzione", 10.00).containsEntry("crediti", 22).containsEntry("valoreCrediti", 4.40);
        assertThat(saldo(segr, persona)).containsEntry("crediti", 0).containsEntry("euro", 24.40);
        assertThat(json(segr.get("/api/pagamenti/incassi")).get("uscite").toString()).contains("Restituzione cauzione");
        assertThat(json(macchina(PHON_M, "demo-phon-m", "7A7A7A01"))).containsEntry("esito", "NON_ATTIVO");
        // il codice è di nuovo libero
        assertThat(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"7A7A7A01\",\"cauzione\":false}").statusCode()).isEqualTo(201);
    }

    @Test
    void trasferimentiTraFamiliariESmarrimento() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String mamma = crea(segr, "/api/clienti", "{\"nome\":\"Mamma\",\"cognome\":\"Borsellino\"}");
        String figlio = crea(segr, "/api/clienti", "{\"nome\":\"Figlio\",\"cognome\":\"Borsellino\",\"dataNascita\":\"2016-01-01\"}");
        String estraneo = crea(segr, "/api/clienti", "{\"nome\":\"Altro\",\"cognome\":\"Borsellino\"}");
        crea(segr, "/api/clienti/" + figlio + "/tutori", "{\"tutoreId\":\"" + mamma + "\",\"relazione\":\"MADRE\"}");
        coord.post("/api/borsellino/persone/" + mamma + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":30,\"motivo\":\"Rimborso\"}");
        assertThat(segr.post("/api/borsellino/persone/" + mamma + "/trasferimenti", "{\"aPersonaId\":\"" + estraneo + "\",\"importo\":10}").statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/borsellino/persone/" + mamma + "/trasferimenti", "{\"aPersonaId\":\"" + figlio + "\",\"importo\":50}").statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/borsellino/persone/" + mamma + "/trasferimenti", "{\"aPersonaId\":\"" + figlio + "\",\"importo\":10}").statusCode()).isEqualTo(204);
        assertThat(saldo(segr, mamma)).containsEntry("euro", 20.00);
        var delFiglio = saldo(segr, figlio);
        assertThat(delFiglio).containsEntry("euro", 10.00);
        assertThat(delFiglio.get("prossimaScadenza")).isNotNull();

        // smarrito con la regola «crediti persi»: i crediti dei servizi si perdono (se non ha altri braccialetti attivi)
        var b = json(segr.post("/api/accessi/persone/" + figlio + "/braccialetti", "{\"codice\":\"7B7B7B01\",\"cauzione\":false}"));
        coord.post("/api/borsellino/persone/" + figlio + "/correzioni", "{\"saldo\":\"CREDITI\",\"quantita\":15,\"motivo\":\"Prova\"}");
        var smarrito = json(segr.put("/api/accessi/braccialetti/" + b.get("id") + "/stato", "{\"stato\":\"SMARRITO\"}"));
        assertThat(smarrito).containsEntry("creditiPersi", 15);
        assertThat(saldo(segr, figlio)).containsEntry("crediti", 0);
        assertThat(segr.get("/api/borsellino/persone/" + figlio).body()).contains("SMARRIMENTO").contains("Ricevuto da Mamma Borsellino");
    }
}
