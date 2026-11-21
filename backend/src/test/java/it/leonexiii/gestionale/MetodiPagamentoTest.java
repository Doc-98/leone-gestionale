package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Metodi di pagamento (blocco 6, lotto 0): ordine in una chiamata sola, metodo che nasce spento, eliminazione con la copia
 * del metodo nei pagamenti. La parte con le casse (metodo usato e cassa chiusa, cassa non chiusa) è in {@link CassaTest}.
 */
class MetodiPagamentoTest extends ProvaApi {

    private static String nomeProva(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    private static List<String> ids(ClientApi c) throws Exception {
        return lista(c.get("/api/pagamenti/metodi")).stream().map(m -> (String) mappa(m).get("id")).toList();
    }

    private static List<String> idsDi(List<Object> elenco) {
        return elenco.stream().map(m -> (String) mappa(m).get("id")).toList();
    }

    private static String corpoOrdine(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    /** Un addebito e un pagamento con quel metodo; restituisce {persona, pagamento}. */
    private String[] paga(ClientApi segr, String metodo, int importo) throws Exception {
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Metodo\",\"cognome\":\"Prova\",\"dataNascita\":\"1985-02-03\"}");
        String addebito = creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Ingresso\",\"importo\":" + importo
            + ",\"scadenza\":\"2030-12-31\"}");
        String pagamento = creaId(segr, "/api/pagamenti", "{\"paganteId\":\"" + persona + "\",\"addebiti\":[\"" + addebito + "\"],\"metodi\":[{\"metodoId\":\""
            + metodo + "\",\"importo\":" + importo + "}]}");
        return new String[] {persona, pagamento};
    }

    @Test
    void ordineInUnaChiamataSola() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        List<String> prima = ids(admin);
        assertThat(prima).hasSizeGreaterThan(2);
        List<String> rovesciato = new ArrayList<>(prima);
        Collections.reverse(rovesciato);

        // chi non ha il permesso «Impostazioni» non riordina
        assertThat(segr.put("/api/pagamenti/metodi/ordine", corpoOrdine(rovesciato)).statusCode()).isEqualTo(403);
        assertThat(ids(admin)).isEqualTo(prima);

        // convalida: elenco vuoto, id che manca, id che non c'è, doppione
        assertThat(admin.put("/api/pagamenti/metodi/ordine", "{\"ids\":[]}").statusCode()).isEqualTo(400);
        var manca = admin.put("/api/pagamenti/metodi/ordine", corpoOrdine(prima.subList(1, prima.size())));
        assertThat(manca.statusCode()).isEqualTo(400);
        assertThat(manca.body()).contains("L'elenco dei metodi è cambiato");
        List<String> sconosciuto = new ArrayList<>(prima);
        sconosciuto.set(0, UUID.randomUUID().toString());
        assertThat(admin.put("/api/pagamenti/metodi/ordine", corpoOrdine(sconosciuto)).statusCode()).isEqualTo(400);
        List<String> doppione = new ArrayList<>(prima);
        doppione.set(0, prima.get(1));
        var doppio = admin.put("/api/pagamenti/metodi/ordine", corpoOrdine(doppione));
        assertThat(doppio.statusCode()).isEqualTo(400);
        assertThat(doppio.body()).contains("compare due volte");
        assertThat(ids(admin)).isEqualTo(prima);

        // l'ordine nuovo: risponde con l'elenco aggiornato e resta
        var r = admin.put("/api/pagamenti/metodi/ordine", corpoOrdine(rovesciato));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(idsDi(lista(r))).isEqualTo(rovesciato);
        assertThat(ids(segr)).isEqualTo(rovesciato);

        // com'era prima
        assertThat(admin.put("/api/pagamenti/metodi/ordine", corpoOrdine(prima)).statusCode()).isEqualTo(200);
        assertThat(ids(admin)).isEqualTo(prima);
    }

    @Test
    void unMetodoPuoNascereSpento() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String nome = nomeProva("Buono spento");
        var spento = crea(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + nome + "\",\"attivo\":false}");
        assertThat(spento).containsEntry("attivo", false).containsEntry("eliminabile", true).containsEntry("motivoBlocco", null);
        var acceso = crea(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + nomeProva("Buono acceso") + "\"}");
        assertThat(acceso).containsEntry("attivo", true);
        var accesoEsplicito = crea(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + nomeProva("Buono acceso") + "\",\"attivo\":true}");
        assertThat(accesoEsplicito).containsEntry("attivo", true);

        // spento non si propone: non si paga con lui
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Spento\",\"cognome\":\"Prova\",\"dataNascita\":\"1985-02-03\"}");
        String addebito = creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Ingresso\",\"importo\":5}");
        assertThat(segr.post("/api/pagamenti", "{\"addebiti\":[\"" + addebito + "\"],\"metodi\":[{\"metodoId\":\"" + spento.get("id") + "\",\"importo\":5}]}")
            .statusCode()).isEqualTo(400);
        // si accende: allora sì
        assertThat(json(admin.put("/api/pagamenti/metodi/" + spento.get("id"), "{\"nome\":\"" + nome + "\",\"attivo\":true}"))).containsEntry("attivo", true);

        for (var m : List.of(spento, acceso, accesoEsplicito)) assertThat(admin.delete("/api/pagamenti/metodi/" + m.get("id")).statusCode()).isEqualTo(204);
    }

    @Test
    void eliminareUnMetodoMaiUsatoELimiti() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var nuovo = crea(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + nomeProva("Mai usato") + "\"}");
        String id = (String) nuovo.get("id");
        assertThat(ids(segr)).contains(id);
        // chi non gestisce le impostazioni non riceve se e perché si può eliminare (né paga il calcolo)
        assertThat(lista(segr.get("/api/pagamenti/metodi")).stream().map(ProvaApi::mappa)).allSatisfy(m -> assertThat(m.get("eliminabile")).isNull());
        assertThat(lista(segr.get("/api/pagamenti/metodi")).stream().map(ProvaApi::mappa)).allSatisfy(m -> assertThat(m.get("motivoBlocco")).isNull());

        assertThat(segr.delete("/api/pagamenti/metodi/" + id).statusCode()).isEqualTo(403);
        assertThat(anonimo().delete("/api/pagamenti/metodi/" + id).statusCode()).isIn(401, 403);
        assertThat(admin.delete("/api/pagamenti/metodi/" + id).statusCode()).isEqualTo(204);
        assertThat(ids(admin)).doesNotContain(id);
        assertThat(admin.delete("/api/pagamenti/metodi/" + id).statusCode()).isEqualTo(404);
        // il nome torna libero
        assertThat(admin.post("/api/pagamenti/metodi", "{\"nome\":\"" + nuovo.get("nome") + "\"}").statusCode()).isEqualTo(201);

        // il borsellino non si elimina mai
        var borsellino = lista(admin.get("/api/pagamenti/metodi")).stream().map(ProvaApi::mappa).filter(m -> Boolean.TRUE.equals(m.get("borsellino"))).findFirst().orElseThrow();
        assertThat(borsellino).containsEntry("eliminabile", false);
        assertThat((String) borsellino.get("motivoBlocco")).contains("borsellino").contains("spegnilo");
        var no = admin.delete("/api/pagamenti/metodi/" + borsellino.get("id"));
        assertThat(no.statusCode()).isEqualTo(409);
        assertThat(no.body()).contains("Il borsellino non si elimina");
        assertThat(ids(admin)).contains((String) borsellino.get("id"));

        // pulizia: il metodo ricreato
        String ricreato = (String) lista(admin.get("/api/pagamenti/metodi")).stream().map(ProvaApi::mappa).filter(m -> nuovo.get("nome").equals(m.get("nome")))
            .findFirst().orElseThrow().get("id");
        assertThat(admin.delete("/api/pagamenti/metodi/" + ricreato).statusCode()).isEqualTo(204);
    }

    @Test
    void unPagamentoDaIncassareBloccaLEliminazione() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String nome = nomeProva("Satispay prova");
        var metodo = crea(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + nome + "\",\"daIncassare\":true}");
        String id = (String) metodo.get("id");
        var uno = paga(segr, id, 20);
        var due = paga(segr, id, 30);

        var bloccato = admin.delete("/api/pagamenti/metodi/" + id);
        assertThat(bloccato.statusCode()).isEqualTo(409);
        assertThat(bloccato.body()).contains(nome).contains("ha ancora 2 pagamenti da incassare");
        var inElenco = lista(admin.get("/api/pagamenti/metodi")).stream().map(ProvaApi::mappa).filter(m -> id.equals(m.get("id"))).findFirst().orElseThrow();
        assertThat(inElenco).containsEntry("eliminabile", false);
        assertThat((String) inElenco.get("motivoBlocco")).contains("ha ancora 2 pagamenti da incassare");

        // un pagamento annullato non è più da incassare
        assertThat(coord.post("/api/pagamenti/" + uno[1] + "/annulla", "{\"motivo\":\"Prova\"}").statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/pagamenti/metodi/" + id).body()).contains("ha ancora 1 pagamento da incassare");
        assertThat(admin.delete("/api/pagamenti/metodi/" + id).statusCode()).isEqualTo(409);

        // incassato l'ultimo, il blocco dei pagamenti cade (resterebbe quello della cassa non chiusa: la prova è in CassaTest); lo si annulla
        String voce = (String) elenco(elenco(json(segr.get("/api/pagamenti/persone/" + due[0])).get("pagamenti")).getFirst().get("metodi")).getFirst().get("id");
        assertThat(segr.post("/api/pagamenti/metodi-usati/" + voce + "/incassa", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/pagamenti/da-incassare").body()).doesNotContain(due[1]);
        assertThat(coord.post("/api/pagamenti/" + due[1] + "/annulla", "{\"motivo\":\"Prova\"}").statusCode()).isEqualTo(204);

        // tutto annullato: si elimina, e i pagamenti vecchi (annullati) mostrano ancora il nome
        assertThat(admin.delete("/api/pagamenti/metodi/" + id).statusCode()).isEqualTo(204);
        var conto = json(segr.get("/api/pagamenti/persone/" + uno[0]));
        var m = elenco(elenco(conto.get("pagamenti")).getFirst().get("metodi")).getFirst();
        assertThat(m).containsEntry("metodo", nome).containsEntry("metodoId", null).containsEntry("daIncassare", true);
    }

    @Test
    void rinominareUnMetodoNonCambiaIlNomeNelloStorico() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String regole = admin.get("/api/borsellino/regole").body();
        String vecchio = nomeProva("Carta"), nuovo = nomeProva("Carta di credito");
        String id = (String) crea(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + vecchio + "\"}").get("id");
        try {
            assertThat(admin.put("/api/borsellino/regole", regole.replaceAll("\"restituibile\":(true|false)", "\"restituibile\":true")).statusCode()).isEqualTo(200);
            orologio.alle("2028-02-15T10:00");
            var pag = paga(segr, id, 15);
            String restituzioni = "/api/borsellino/persone/" + pag[0] + "/restituzioni";
            assertThat(coord.post("/api/borsellino/persone/" + pag[0] + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":10,\"motivo\":\"Prova\"}").statusCode()).isEqualTo(204);
            assertThat(coord.post(restituzioni, "{\"importo\":4,\"metodoId\":\"" + id + "\"}").statusCode()).isEqualTo(204);

            assertThat(json(admin.put("/api/pagamenti/metodi/" + id, "{\"nome\":\"" + nuovo + "\"}"))).containsEntry("nome", nuovo);

            // l'elenco ha il nome nuovo; i pagamenti e le uscite già registrati, quello di allora
            assertThat(lista(admin.get("/api/pagamenti/metodi")).stream().map(m -> mappa(m).get("nome"))).contains(nuovo).doesNotContain(vecchio);
            var usato = elenco(elenco(json(segr.get("/api/pagamenti/persone/" + pag[0])).get("pagamenti")).getFirst().get("metodi")).getFirst();
            assertThat(usato).containsEntry("metodo", vecchio).containsEntry("metodoId", id);
            var incassi = json(segr.get("/api/pagamenti/incassi?da=2028-02-15&a=2028-02-15"));
            assertThat(elenco(incassi.get("pagamenti")).stream().flatMap(p -> elenco(p.get("metodi")).stream()).map(m -> m.get("metodo"))).contains(vecchio).doesNotContain(nuovo);
            assertThat(elenco(incassi.get("uscite"))).extracting(u -> u.get("metodo")).contains(vecchio).doesNotContain(nuovo);
        } finally {
            assertThat(admin.put("/api/borsellino/regole", regole).statusCode()).isEqualTo(200);
        }
    }
}
