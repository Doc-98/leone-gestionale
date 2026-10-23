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

/** Piano vasca sui dati demo, in date del marzo 2028 (lontane dagli altri test). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class PianoVascaTest {

    static final String IMPIANTO = "00000000-0000-0000-0000-0000000000b1";
    static final String VASCA_GRANDE = "00000000-0000-0000-0000-0000000000c1";
    static final String CORSIA_1 = "00000000-0000-0000-0000-000000000101";
    static final String CORSIA_2 = "00000000-0000-0000-0000-000000000102";
    static final String CORSIA_3 = "00000000-0000-0000-0000-000000000103";
    static final String CORSIA_6 = "00000000-0000-0000-0000-000000000106";
    /** Corsie 2 e 3 della vasca divisa a 5 */
    static final String CORSIA_2_DI_5 = "00000000-0000-0000-0000-000000000112";
    static final String CORSIA_3_DI_5 = "00000000-0000-0000-0000-000000000113";
    static final String LUCA = "00000000-0000-0000-0000-000000000103";

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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> occorrenze(ClientApi c, String da, String a) throws Exception {
        var r = c.get("/api/piano?da=" + da + "&a=" + a);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return ((List<Object>) json(r).get("occorrenze")).stream().map(PianoVascaTest::mappa).toList();
    }

    private static Map<String, Object> di(List<Map<String, Object>> occ, String titolo, String data) {
        return occ.stream().filter(o -> titolo.equals(o.get("titolo")) && data.equals(o.get("data"))).findFirst()
            .orElseThrow(() -> new AssertionError("Manca " + titolo + " il " + data));
    }

    private Map<String, Object> crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return json(r);
    }

    @Test
    void loStaffVedeIlPianoMaNonLoModifica() throws Exception {
        var luca = come("staff@demo.leone");
        var r = luca.get("/api/piano?da=2028-03-06&a=2028-03-12");
        assertThat(r.statusCode()).isEqualTo(200);
        // le corsie hanno la vasca come padre
        assertThat(r.body()).contains("\"nome\":\"Corsia 1\"").contains("\"padreId\":\"" + VASCA_GRANDE + "\"")
            .contains("\"assetto\":6").contains("\"assetto\":5");
        var occ = occorrenze(luca, "2028-03-06", "2028-03-12");
        // lunedì 6 marzo 2028, fascia 17:00-17:45: Delfino Giallo in corsia 2
        var giallo = di(occ, "Delfino Giallo", "2028-03-06");
        assertThat(giallo).containsEntry("inizio", "17:00:00").containsEntry("fine", "17:45:00").containsEntry("stato", "REGOLARE")
            .containsEntry("colore", "#E6B400");
        assertThat(giallo.get("spazi").toString()).contains(CORSIA_2);
        assertThat(giallo.get("dettaglio")).isEqualTo("Delfini · 6–6 anni");
        assertThat(mappa(giallo.get("istruttore"))).containsEntry("nome", "Federico Mauri");
        // nella stessa fascia un corso per corsia, nessuna sovrapposizione
        assertThat(occ.stream().filter(o -> "2028-03-06".equals(o.get("data")) && "17:00:00".equals(o.get("inizio"))).count()).isGreaterThanOrEqualTo(6);
        assertThat((List<?>) giallo.get("conflitti")).isEmpty();

        assertThat(luca.post("/api/corsi", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"x\",\"tipo\":\"ALTRO\"}").statusCode()).isEqualTo(403);
        assertThat(luca.post("/api/eventi", "{}").statusCode()).isEqualTo(403);
        assertThat(luca.get("/api/piano?da=2028-01-01&a=2028-06-01").statusCode()).isEqualTo(400);
    }

    @Test
    void lezioniSovrapposteSaltateESpostate() throws Exception {
        var coord = come("coordinatore@demo.leone");
        var corso = crea(coord, "/api/corsi", """
            {"impiantoId":"%s","nome":"Master adulti","tipo":"MASTER","livello":"Avanzato","etaDa":18,"istruttoreId":"%s"}""".formatted(IMPIANTO, LUCA));
        String corsoId = (String) corso.get("id");
        assertThat(corso).containsEntry("attivo", true);

        // lunedì 17:30-18:30 sulla corsia 6: si sovrappone all'ambientamento e al Delfino Nero (avviso, non errore)
        var esito = crea(coord, "/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"17:30","fine":"18:30","validaDal":"2028-03-01","validaAl":"2028-06-30","spazi":["%s"]}""".formatted(CORSIA_6));
        assertThat((List<?>) esito.get("avvisi")).isNotEmpty();
        assertThat(esito.get("avvisi").toString()).contains("Ambientamento vasca alta").contains("Delfino Nero");
        String lezione = (String) mappa(esito.get("valore")).get("id");
        // senza spazi: errore
        assertThat(coord.post("/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"17:30","fine":"18:30","validaDal":"2028-03-01","spazi":[]}""").statusCode()).isEqualTo(400);

        var occ = occorrenze(coord, "2028-03-06", "2028-03-12");
        assertThat((List<?>) di(occ, "Master adulti", "2028-03-06").get("conflitti")).isNotEmpty();
        assertThat((List<?>) di(occ, "Delfino Nero", "2028-03-06").get("conflitti")).isNotEmpty();
        // le altre corsie non si toccano
        assertThat((List<?>) di(occ, "Delfino Azzurro", "2028-03-06").get("conflitti")).isEmpty();

        // salto il 6, sposto il 13 al 14 mattina
        var salto = crea(coord, "/api/lezioni/" + lezione + "/eccezioni", "{\"data\":\"2028-03-06\",\"tipo\":\"SALTATA\",\"motivo\":\"Riunione\"}");
        crea(coord, "/api/lezioni/" + lezione + "/eccezioni",
            "{\"data\":\"2028-03-13\",\"tipo\":\"SPOSTATA\",\"nuovaData\":\"2028-03-14\",\"nuovoInizio\":\"10:00\",\"nuovoFine\":\"11:00\"}");
        // non è un lunedì: errore; due volte lo stesso giorno: conflitto
        assertThat(coord.post("/api/lezioni/" + lezione + "/eccezioni", "{\"data\":\"2028-03-07\",\"tipo\":\"SALTATA\"}").statusCode()).isEqualTo(400);
        assertThat(coord.post("/api/lezioni/" + lezione + "/eccezioni", "{\"data\":\"2028-03-06\",\"tipo\":\"SALTATA\"}").statusCode()).isEqualTo(409);

        occ = occorrenze(coord, "2028-03-06", "2028-03-19");
        var saltata = di(occ, "Master adulti", "2028-03-06");
        assertThat(saltata).containsEntry("stato", "SALTATA").containsEntry("nota", "Riunione");
        assertThat((List<?>) saltata.get("conflitti")).isEmpty();
        assertThat((List<?>) di(occ, "Delfino Nero", "2028-03-06").get("conflitti")).isEmpty();
        assertThat(di(occ, "Master adulti", "2028-03-13")).containsEntry("stato", "SPOSTATA_VIA");
        assertThat(di(occ, "Master adulti", "2028-03-14")).containsEntry("stato", "SPOSTATA_QUI").containsEntry("inizio", "10:00:00")
            .containsEntry("dataOriginale", "2028-03-13");

        // annullo il salto: torna regolare
        assertThat(coord.delete("/api/eccezioni/" + salto.get("valore")).statusCode()).isEqualTo(204);
        assertThat(di(occorrenze(coord, "2028-03-06", "2028-03-06"), "Master adulti", "2028-03-06")).containsEntry("stato", "REGOLARE");

        // una lezione non ancora iniziata si elimina davvero
        assertThat(coord.delete("/api/lezioni/" + lezione).statusCode()).isEqualTo(204);
        assertThat(coord.get("/api/corsi").body()).doesNotContain(lezione);
        assertThat(occorrenze(coord, "2028-03-06", "2028-03-19").stream().noneMatch(o -> "Master adulti".equals(o.get("titolo")))).isTrue();
    }

    @Test
    void laVascaNonSiUsaACinqueEASeiCorsieInsieme() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String corsoId = (String) crea(coord, "/api/corsi", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"Prova assetti\",\"tipo\":\"ALTRO\"}").get("id");
        // corsie di assetti diversi nella stessa lezione: errore
        var misto = coord.post("/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"17:00","fine":"17:45","validaDal":"2028-04-01","spazi":["%s","%s"]}""".formatted(CORSIA_3, CORSIA_3_DI_5));
        assertThat(misto.statusCode()).isEqualTo(400);
        assertThat(misto.body()).contains("assetti diversi");
        // lunedì alle 17 la fascia è a 6 corsie: la corsia 2 della vasca a 5 non si può scegliere
        var fuoriAssetto = coord.post("/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"17:00","fine":"17:45","validaDal":"2028-04-01","validaAl":"2028-04-30","spazi":["%s"]}""".formatted(CORSIA_2_DI_5));
        assertThat(fuoriAssetto.statusCode()).isEqualTo(400);
        assertThat(fuoriAssetto.body()).contains("divisa in 6 corsie");
        // dove non c'è una fascia (lunedì a pranzo) si può, ma si sovrappone al nuoto libero sulle corsie a 6
        var esito = crea(coord, "/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"12:30","fine":"13:15","validaDal":"2028-04-01","validaAl":"2028-04-30","spazi":["%s"]}""".formatted(CORSIA_2_DI_5));
        assertThat(esito.get("avvisi").toString()).contains("Nuoto libero");
        // il mercoledì la vasca è già a 5: nessun problema (corsia 3, libera alle 16:10)
        var mercoledi = crea(coord, "/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":3,"inizio":"16:10","fine":"16:55","validaDal":"2028-04-01","validaAl":"2028-04-30","spazi":["%s"]}""".formatted(CORSIA_3_DI_5));
        assertThat((List<?>) mercoledi.get("avvisi")).isEmpty();
        // colore del corso
        var colore = coord.put("/api/corsi/" + corsoId, "{\"nome\":\"Prova assetti\",\"tipo\":\"ALTRO\",\"colore\":\"#12ab34\"}");
        assertThat(json(colore)).containsEntry("colore", "#12AB34");
        assertThat(coord.put("/api/corsi/" + corsoId, "{\"nome\":\"Prova assetti\",\"tipo\":\"ALTRO\",\"colore\":\"rosso\"}").statusCode()).isEqualTo(400);
        assertThat(coord.put("/api/corsi/" + corsoId, "{\"nome\":\"Prova assetti\",\"tipo\":\"ALTRO\",\"attivo\":false}").statusCode()).isEqualTo(200);
    }

    @Test
    void eventiChiusureEGare() throws Exception {
        var coord = come("coordinatore@demo.leone");
        // chiusura di tutto l'impianto lunedì 20 e martedì 21 marzo 2028
        var ch = crea(coord, "/api/eventi", """
            {"impiantoId":"%s","tipo":"CHIUSURA","titolo":"Manutenzione impianto","data":"2028-03-20","dataFine":"2028-03-21","inizio":"00:00","fine":"23:59"}"""
            .formatted(IMPIANTO));
        assertThat((List<?>) ch.get("avvisi")).isEmpty();
        var occ = occorrenze(coord, "2028-03-20", "2028-03-22");
        assertThat(di(occ, "Delfino Giallo", "2028-03-20")).containsEntry("stato", "CHIUSO").containsEntry("nota", "Manutenzione impianto");
        assertThat(di(occ, "Squalo Base", "2028-03-21")).containsEntry("stato", "CHIUSO");
        assertThat(di(occ, "Propaganda · 8-12 anni", "2028-03-22")).containsEntry("stato", "REGOLARE");
        assertThat(occ.stream().filter(o -> "Manutenzione impianto".equals(o.get("titolo"))).count()).isEqualTo(2);

        // gara sulla vasca grande martedì 28 alle 18: tocca i corsi delle fasce 17:50 e 18:40
        var gara = crea(coord, "/api/eventi", """
            {"impiantoId":"%s","tipo":"GARA","titolo":"Gara sociale","data":"2028-03-28","inizio":"18:00","fine":"20:00","spazi":["%s"]}"""
            .formatted(IMPIANTO, VASCA_GRANDE));
        assertThat(gara.get("avvisi").toString()).contains("Squalo Base").contains("Delfino Nero");
        String garaId = (String) mappa(gara.get("valore")).get("id");
        assertThat((List<?>) di(occorrenze(coord, "2028-03-28", "2028-03-28"), "Squalo Base", "2028-03-28").get("conflitti")).isNotEmpty();

        // modifica: la sposto alle 14:30 in corsia 1, nessuna sovrapposizione
        var mod = coord.put("/api/eventi/" + garaId, """
            {"impiantoId":"%s","tipo":"GARA","titolo":"Gara sociale","data":"2028-03-28","inizio":"14:30","fine":"16:00","spazi":["%s"]}"""
            .formatted(IMPIANTO, CORSIA_1));
        assertThat(mod.statusCode()).as(mod.body()).isEqualTo(200);
        assertThat((List<?>) json(mod).get("avvisi")).isEmpty();

        assertThat(json(coord.post("/api/eventi/" + garaId + "/annulla", ""))).containsEntry("annullato", true);
        assertThat(di(occorrenze(coord, "2028-03-28", "2028-03-28"), "Gara sociale", "2028-03-28")).containsEntry("stato", "ANNULLATO");
        assertThat(coord.put("/api/eventi/" + garaId, "{\"impiantoId\":\"" + IMPIANTO + "\",\"tipo\":\"GARA\",\"titolo\":\"x\",\"data\":\"2028-03-28\",\"inizio\":\"20:00\",\"fine\":\"21:00\"}")
            .statusCode()).isEqualTo(409);
        // lo staff vede il dettaglio dell'evento
        assertThat(come("staff@demo.leone").get("/api/eventi/" + garaId).statusCode()).isEqualTo(200);

        // un corso disattivato sparisce dal piano
        String fitness = "00000000-0000-0000-0000-000000000624";
        assertThat(coord.put("/api/corsi/" + fitness, "{\"nome\":\"AquaFitness\",\"tipo\":\"ACQUAGYM\",\"etaDa\":18,\"attivo\":false}").statusCode()).isEqualTo(200);
        assertThat(occorrenze(coord, "2028-03-20", "2028-03-26").stream().noneMatch(o -> "AquaFitness".equals(o.get("titolo")))).isTrue();
        assertThat(coord.put("/api/corsi/" + fitness, "{\"nome\":\"AquaFitness\",\"tipo\":\"ACQUAGYM\",\"etaDa\":18,\"attivo\":true}").statusCode()).isEqualTo(200);
    }
}
