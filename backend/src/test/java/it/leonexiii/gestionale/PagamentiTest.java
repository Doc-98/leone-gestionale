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

/** Addebiti e pagamenti (ADR 0018, parte 5): quota e abbonamento addebitati, pagamento misto del genitore, bonifico da incassare, annullamenti. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class PagamentiTest {

    static final String DELFINO_GIALLO = "00000000-0000-0000-0000-000000000615";

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
    private static List<Map<String, Object>> lista(Object o) {
        return (List<Map<String, Object>>) o;
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

    private Map<String, Object> conto(ClientApi c, String persona) throws Exception {
        var r = c.get("/api/pagamenti/persone/" + persona);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return json(r);
    }

    private static String addebito(Map<String, Object> conto, String inizioDescrizione) {
        return (String) lista(conto.get("addebiti")).stream().filter(a -> ((String) a.get("descrizione")).startsWith(inizioDescrizione))
            .findFirst().orElseThrow(() -> new AssertionError("Manca l'addebito " + inizioDescrizione)).get("id");
    }

    private String metodo(ClientApi c, String nome) throws Exception {
        return (String) JsonParserFactory.getJsonParser().parseList(c.get("/api/pagamenti/metodi").body()).stream().map(PagamentiTest::mappa)
            .filter(m -> nome.equals(m.get("nome"))).findFirst().orElseThrow().get("id");
    }

    @Test
    void addebitiPagamentiMistiEAnnullamenti() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String bimbo = crea(segr, "/api/clienti", "{\"nome\":\"Conto\",\"cognome\":\"Prova\",\"dataNascita\":\"2018-03-03\"}");
        String mamma = crea(segr, "/api/clienti", "{\"nome\":\"Mamma\",\"cognome\":\"Prova\"}");
        crea(segr, "/api/clienti/" + bimbo + "/tutori", "{\"tutoreId\":\"" + mamma + "\",\"relazione\":\"MADRE\",\"comunicazioni\":true}");
        String sorella = crea(segr, "/api/clienti", "{\"nome\":\"Sorella\",\"cognome\":\"Prova\",\"dataNascita\":\"2016-04-04\"}");
        crea(segr, "/api/clienti/" + sorella + "/tutori", "{\"tutoreId\":\"" + mamma + "\",\"relazione\":\"MADRE\"}");

        // tesseramento e abbonamento diventano addebiti; più un addebito scritto a mano
        String tess = crea(segr, "/api/clienti/" + bimbo + "/tesseramenti", "{}");
        crea(segr, "/api/clienti/" + bimbo + "/abbonamenti", "{\"tipo\":\"CORSO\",\"corsoId\":\"" + DELFINO_GIALLO + "\",\"volteASettimana\":1,\"importo\":300}");
        crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + bimbo + "\",\"descrizione\":\"Cuffia\",\"importo\":8}");
        crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + sorella + "\",\"descrizione\":\"Ingresso extra\",\"importo\":5}");
        var c = conto(segr, bimbo);
        assertThat(mappa(c.get("totali"))).containsEntry("daPagare", 343.00);
        String quota = addebito(c, "Quota associativa"), corso = addebito(c, "Delfino Giallo"), cuffia = addebito(c, "Cuffia");
        // la famiglia: la sorella ha 5 € da pagare; può pagare la mamma
        var fam = lista(c.get("famiglia"));
        assertThat(fam.stream().filter(f -> sorella.equals(f.get("personaId"))).findFirst().orElseThrow().get("totali").toString()).contains("daPagare=5.0");
        assertThat(c.get("paganti").toString()).contains(mamma);
        String extra = (String) lista(fam.stream().filter(f -> sorella.equals(f.get("personaId"))).findFirst().orElseThrow().get("aperti")).getFirst().get("id");

        String contanti = metodo(segr, "Contanti"), bonifico = metodo(segr, "Bonifico");
        // i metodi devono fare l'importo; non si paga più di quanto si deve
        assertThat(segr.post("/api/pagamenti", "{\"addebiti\":[\"" + quota + "\"],\"importo\":35,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":30}]}")
            .statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/pagamenti", "{\"addebiti\":[\"" + quota + "\"],\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":36}]}")
            .statusCode()).isEqualTo(400);
        assertThat(come("staff@demo.leone").post("/api/pagamenti", "{}").statusCode()).isEqualTo(403);

        // la mamma paga 205 €: 55 in contanti e 150 con bonifico; si salda prima la quota, poi la sorella, poi parte del corso
        String pag = crea(segr, "/api/pagamenti", """
            {"paganteId":"%s","addebiti":["%s","%s","%s"],"metodi":[{"metodoId":"%s","importo":55},{"metodoId":"%s","importo":150,"riferimento":"CRO 123"}]}"""
            .formatted(mamma, quota, extra, corso, contanti, bonifico));
        c = conto(segr, bimbo);
        assertThat(mappa(c.get("totali"))).containsEntry("daPagare", 143.00).containsEntry("daIncassare", 150.00);
        var voceCorso = lista(c.get("addebiti")).stream().filter(a -> corso.equals(a.get("id"))).findFirst().orElseThrow();
        assertThat(voceCorso).containsEntry("pagato", 165.00).containsEntry("residuo", 135.00).containsEntry("stato", "DA_PAGARE");
        assertThat(lista(c.get("pagamenti")).getFirst().get("voci").toString()).contains("Sorella Prova");
        // il tesseramento ora risulta pagato
        assertThat(segr.get("/api/clienti/" + bimbo).body()).contains("\"id\":\"" + tess + "\"").doesNotContain("\"pagatoIl\":null");
        // un addebito pagato non si annulla, l'abbonamento con pagamenti nemmeno
        assertThat(coord.post("/api/pagamenti/addebiti/" + quota + "/annulla", "{\"motivo\":\"Errore\"}").statusCode()).isEqualTo(409);
        assertThat(coord.post("/api/pagamenti/addebiti/" + cuffia + "/annulla", "{\"motivo\":\"Già data\"}").statusCode()).isEqualTo(204);

        // bonifico da incassare: compare nell'elenco finché non si segna incassato
        assertThat(segr.get("/api/pagamenti/da-incassare").body()).contains("CRO 123");
        var incassi = json(segr.get("/api/pagamenti/incassi"));
        assertThat(incassi.get("pagamenti").toString()).contains(pag).contains("riferimento=CRO 123");
        assertThat(incassi.get("perMetodo").toString()).contains("metodo=Bonifico").contains("metodo=Contanti");
        String voceBonifico = (String) lista(lista(c.get("pagamenti")).getFirst().get("metodi")).stream().filter(m -> bonifico.equals(m.get("metodoId")))
            .findFirst().orElseThrow().get("id");
        assertThat(segr.post("/api/pagamenti/metodi-usati/" + voceBonifico + "/incassa", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/pagamenti/da-incassare").body()).doesNotContain("CRO 123");

        // annullare: serve il permesso e il motivo; poi tutto torna da pagare e il tesseramento non è più pagato
        assertThat(segr.post("/api/pagamenti/" + pag + "/annulla", "{\"motivo\":\"Errore\"}").statusCode()).isEqualTo(403);
        assertThat(coord.post("/api/pagamenti/" + pag + "/annulla", "{}").statusCode()).isEqualTo(400);
        assertThat(coord.post("/api/pagamenti/" + pag + "/annulla", "{\"motivo\":\"Registrato due volte\"}").statusCode()).isEqualTo(204);
        assertThat(mappa(conto(segr, bimbo).get("totali"))).containsEntry("daPagare", 335.00);
        assertThat(segr.get("/api/clienti/" + bimbo).body()).contains("\"pagatoIl\":null");
        assertThat(coord.post("/api/clienti/tesseramenti/" + tess + "/annulla", "").statusCode()).isEqualTo(204);
        assertThat(mappa(conto(segr, bimbo).get("totali"))).containsEntry("daPagare", 300.00);
    }

    @Test
    void famigliaInsiemeOPerPersonaEPagamentiSemplici() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String papa = crea(segr, "/api/clienti", "{\"nome\":\"Papà\",\"cognome\":\"Famiglia\"}");
        String uno = crea(segr, "/api/clienti", "{\"nome\":\"Uno\",\"cognome\":\"Famiglia\",\"dataNascita\":\"2017-01-01\"}");
        String due = crea(segr, "/api/clienti", "{\"nome\":\"Due\",\"cognome\":\"Famiglia\",\"dataNascita\":\"2019-01-01\"}");
        for (String figlio : List.of(uno, due))
            crea(segr, "/api/clienti/" + figlio + "/tutori", "{\"tutoreId\":\"" + papa + "\",\"relazione\":\"PADRE\"}");
        String a1 = crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + uno + "\",\"descrizione\":\"Corso uno\",\"importo\":100}");
        String a2 = crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + due + "\",\"descrizione\":\"Corso due\",\"importo\":80}");
        String b1 = crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + uno + "\",\"descrizione\":\"Cuffia uno\",\"importo\":10}");
        String b2 = crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + due + "\",\"descrizione\":\"Cuffia due\",\"importo\":10}");
        String contanti = metodo(segr, "Contanti"), carta = metodo(segr, "Carta di credito");

        // di partenza si sceglie ogni volta: separati = un pagamento per figlio; i metodi si consumano in ordine
        var r = json(segr.post("/api/pagamenti", """
            {"paganteId":"%s","addebiti":["%s","%s"],"separati":true,"metodi":[{"metodoId":"%s","importo":120},{"metodoId":"%s","importo":60}]}"""
            .formatted(papa, a1, a2, contanti, carta)));
        assertThat((List<?>) r.get("ids")).hasSize(2);
        var pagUno = lista(conto(segr, uno).get("pagamenti")).getFirst();
        assertThat(pagUno).containsEntry("importo", 100.00);
        assertThat(pagUno.get("metodi").toString()).contains("metodo=Contanti, importo=100.0").doesNotContain("Carta");
        var pagDue = lista(conto(segr, due).get("pagamenti")).getFirst();
        assertThat(pagDue.get("metodi").toString()).contains("metodo=Contanti, importo=20.0").contains("metodo=Carta di credito, importo=60.0");

        // con l'impostazione «insieme» il pagamento resta unico anche se si chiede di separarlo
        String dati = admin.get("/api/societa").body();
        assertThat(dati).contains("\"pagamentiFamiglia\":\"A_SCELTA\"");
        assertThat(admin.put("/api/societa", dati.replace("\"A_SCELTA\"", "\"INSIEME\"")).statusCode()).isEqualTo(200);
        var unico = json(segr.post("/api/pagamenti", """
            {"paganteId":"%s","addebiti":["%s","%s"],"separati":true,"metodi":[{"metodoId":"%s","importo":20}]}""".formatted(papa, b1, b2, contanti)));
        assertThat((List<?>) unico.get("ids")).hasSize(1);
        assertThat(admin.put("/api/societa", dati).statusCode()).isEqualTo(200);

        // senza il permesso «Acconti e pagamenti misti»: per intero e con un solo metodo
        String nuovo = crea(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + uno + "\",\"descrizione\":\"Gara\",\"importo\":30}");
        var ruolo = json(admin.post("/api/ruoli", "{\"nome\":\"Cassa semplice\",\"copiaDa\":\"STAFF\"}"));
        String codice = (String) ruolo.get("codice");
        assertThat(admin.put("/api/ruoli/" + codice, "{\"nome\":\"Cassa semplice\",\"permessi\":[\"CLIENTI\",\"PAGAMENTI\"]}").statusCode()).isEqualTo(204);
        var silvia = JsonParserFactory.getJsonParser().parseList(admin.get("/api/account").body()).stream().map(PagamentiTest::mappa)
            .filter(x -> "silvia.brambilla@demo.leone".equals(x.get("email"))).findFirst().orElseThrow();
        assertThat(admin.post("/api/ruoli/" + codice + "/assegnazioni", "{\"accountId\":\"" + silvia.get("id") + "\"}").statusCode()).isEqualTo(201);
        var cassa = come("silvia.brambilla@demo.leone");
        var acconto = cassa.post("/api/pagamenti", "{\"addebiti\":[\"" + nuovo + "\"],\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":10}]}");
        assertThat(acconto.statusCode()).isEqualTo(403);
        assertThat(acconto.body()).contains("Acconti e pagamenti misti");
        assertThat(cassa.post("/api/pagamenti", "{\"addebiti\":[\"" + nuovo + "\"],\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":20},{\"metodoId\":\"" + carta + "\",\"importo\":10}]}")
            .statusCode()).isEqualTo(403);
        crea(cassa, "/api/pagamenti", "{\"addebiti\":[\"" + nuovo + "\"],\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":30}]}");
    }

    @Test
    void metodiDiPagamentoModificabili() throws Exception {
        var admin = come("admin@demo.leone");
        assertThat(come("segreteria@demo.leone").post("/api/pagamenti/metodi", "{\"nome\":\"Buono welfare\"}").statusCode()).isEqualTo(403);
        var nuovo = json(admin.post("/api/pagamenti/metodi", "{\"nome\":\"Buono welfare\",\"daIncassare\":true}"));
        assertThat(nuovo).containsEntry("daIncassare", true).containsEntry("attivo", true);
        assertThat(admin.post("/api/pagamenti/metodi", "{\"nome\":\"buono WELFARE\"}").statusCode()).isEqualTo(409);
        var spento = admin.put("/api/pagamenti/metodi/" + nuovo.get("id"), "{\"nome\":\"Buono welfare\",\"attivo\":false}");
        assertThat(json(spento)).containsEntry("attivo", false).containsEntry("daIncassare", true);
    }
}
