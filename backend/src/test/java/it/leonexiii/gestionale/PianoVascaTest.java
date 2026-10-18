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
    static final String CORSIA_6 = "00000000-0000-0000-0000-000000000106";
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
        assertThat(r.body()).contains("\"nome\":\"Corsia 1\"").contains("\"padreId\":\"" + VASCA_GRANDE + "\"");
        var occ = occorrenze(luca, "2028-03-06", "2028-03-12");
        // lunedì 6 marzo 2028: delfini 17-18 sulle corsie 1-2
        var delfini = di(occ, "Scuola nuoto ragazzi · delfini", "2028-03-06");
        assertThat(delfini).containsEntry("inizio", "17:00:00").containsEntry("stato", "REGOLARE");
        assertThat(delfini.get("spazi").toString()).contains(CORSIA_1);
        assertThat(delfini.get("dettaglio")).isEqualTo("Delfini · 8–12 anni");
        assertThat(mappa(delfini.get("istruttore"))).containsEntry("nome", "Luca Bianchi");

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

        // lunedì 17:30-18:30 sulla corsia 1: si sovrappone ai delfini (avviso, non errore)
        var esito = crea(coord, "/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"17:30","fine":"18:30","validaDal":"2028-03-01","validaAl":"2028-06-30","spazi":["%s"]}""".formatted(CORSIA_1));
        assertThat((List<?>) esito.get("avvisi")).isNotEmpty();
        assertThat(esito.get("avvisi").toString()).contains("delfini");
        String lezione = (String) mappa(esito.get("valore")).get("id");
        // senza spazi: errore
        assertThat(coord.post("/api/corsi/" + corsoId + "/lezioni", """
            {"giornoSettimana":1,"inizio":"17:30","fine":"18:30","validaDal":"2028-03-01","spazi":[]}""").statusCode()).isEqualTo(400);

        var occ = occorrenze(coord, "2028-03-06", "2028-03-12");
        assertThat((List<?>) di(occ, "Master adulti", "2028-03-06").get("conflitti")).isNotEmpty();
        assertThat((List<?>) di(occ, "Scuola nuoto ragazzi · delfini", "2028-03-06").get("conflitti")).isNotEmpty();
        // i pinguini (corsie 3-4) non si toccano
        assertThat((List<?>) di(occ, "Scuola nuoto ragazzi · pinguini", "2028-03-06").get("conflitti")).isEmpty();

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
        assertThat((List<?>) di(occ, "Scuola nuoto ragazzi · delfini", "2028-03-06").get("conflitti")).isEmpty();
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
    void eventiChiusureEGare() throws Exception {
        var coord = come("coordinatore@demo.leone");
        // chiusura di tutto l'impianto lunedì 20 e martedì 21 marzo 2028
        var ch = crea(coord, "/api/eventi", """
            {"impiantoId":"%s","tipo":"CHIUSURA","titolo":"Manutenzione impianto","data":"2028-03-20","dataFine":"2028-03-21","inizio":"00:00","fine":"23:59"}"""
            .formatted(IMPIANTO));
        assertThat((List<?>) ch.get("avvisi")).isEmpty();
        var occ = occorrenze(coord, "2028-03-20", "2028-03-22");
        assertThat(di(occ, "Scuola nuoto ragazzi · delfini", "2028-03-20")).containsEntry("stato", "CHIUSO").containsEntry("nota", "Manutenzione impianto");
        assertThat(di(occ, "Agonismo esordienti", "2028-03-21")).containsEntry("stato", "CHIUSO");
        assertThat(di(occ, "Scuola nuoto ragazzi · delfini", "2028-03-22")).containsEntry("stato", "REGOLARE");
        assertThat(occ.stream().filter(o -> "Manutenzione impianto".equals(o.get("titolo"))).count()).isEqualTo(2);

        // gara sulla vasca grande martedì 28 alle 18: tocca l'agonismo sulle corsie 4-6
        var gara = crea(coord, "/api/eventi", """
            {"impiantoId":"%s","tipo":"GARA","titolo":"Gara sociale","data":"2028-03-28","inizio":"18:00","fine":"20:00","spazi":["%s"]}"""
            .formatted(IMPIANTO, VASCA_GRANDE));
        assertThat(gara.get("avvisi").toString()).contains("Agonismo esordienti");
        String garaId = (String) mappa(gara.get("valore")).get("id");
        assertThat((List<?>) di(occorrenze(coord, "2028-03-28", "2028-03-28"), "Agonismo esordienti", "2028-03-28").get("conflitti")).isNotEmpty();

        // modifica: la sposto alle 20, nessuna sovrapposizione
        var mod = coord.put("/api/eventi/" + garaId, """
            {"impiantoId":"%s","tipo":"GARA","titolo":"Gara sociale","data":"2028-03-28","inizio":"20:00","fine":"21:30","spazi":["%s"]}"""
            .formatted(IMPIANTO, CORSIA_6));
        assertThat(mod.statusCode()).as(mod.body()).isEqualTo(200);
        assertThat((List<?>) json(mod).get("avvisi")).isEmpty();

        assertThat(json(coord.post("/api/eventi/" + garaId + "/annulla", ""))).containsEntry("annullato", true);
        assertThat(di(occorrenze(coord, "2028-03-28", "2028-03-28"), "Gara sociale", "2028-03-28")).containsEntry("stato", "ANNULLATO");
        assertThat(coord.put("/api/eventi/" + garaId, "{\"impiantoId\":\"" + IMPIANTO + "\",\"tipo\":\"GARA\",\"titolo\":\"x\",\"data\":\"2028-03-28\",\"inizio\":\"20:00\",\"fine\":\"21:00\"}")
            .statusCode()).isEqualTo(409);
        // lo staff vede il dettaglio dell'evento
        assertThat(come("staff@demo.leone").get("/api/eventi/" + garaId).statusCode()).isEqualTo(200);

        // un corso disattivato sparisce dal piano
        String acquagym = "00000000-0000-0000-0000-000000000605";
        assertThat(coord.put("/api/corsi/" + acquagym, "{\"nome\":\"Acquagym\",\"tipo\":\"ACQUAGYM\",\"etaDa\":18,\"attivo\":false}").statusCode()).isEqualTo(200);
        assertThat(occorrenze(coord, "2028-03-20", "2028-03-26").stream().noneMatch(o -> "Acquagym".equals(o.get("titolo")))).isTrue();
        assertThat(coord.put("/api/corsi/" + acquagym, "{\"nome\":\"Acquagym\",\"tipo\":\"ACQUAGYM\",\"etaDa\":18,\"attivo\":true}").statusCode()).isEqualTo(200);
    }
}
