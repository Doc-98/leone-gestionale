package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Fasce orarie con assetto delle corsie, iscrizioni ai turni dei corsi, presenze. La domenica è libera nei dati demo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class FasceEIscrittiTest {

    static final String IMPIANTO = "00000000-0000-0000-0000-0000000000b1";
    static final String VASCA_GRANDE = "00000000-0000-0000-0000-0000000000c1";
    static final String CORSIA_2 = "00000000-0000-0000-0000-000000000102";
    static final String CORSIA_6 = "00000000-0000-0000-0000-000000000106";
    static final String CORSIA_2_DI_5 = "00000000-0000-0000-0000-000000000112";
    static final String CORSIA_4 = "00000000-0000-0000-0000-000000000104";

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

    private static List<Object> lista(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseList(r.body());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mappa(Object o) {
        return (Map<String, Object>) o;
    }

    private Map<String, Object> crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return json(r);
    }

    private String corso(ClientApi coord, String nome) throws Exception {
        return (String) crea(coord, "/api/corsi", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"" + nome + "\",\"tipo\":\"ALTRO\"}").get("id");
    }

    private String lezione(ClientApi coord, String corso, String inizio, String fine, String spazio, String posti) throws Exception {
        var r = crea(coord, "/api/corsi/" + corso + "/lezioni", """
            {"giornoSettimana":7,"inizio":"%s","fine":"%s","validaDal":"2028-01-01","spazi":["%s"],"posti":%s}""".formatted(inizio, fine, spazio, posti));
        return (String) mappa(r.get("valore")).get("id");
    }

    @Test
    void leFasceHannoOrariLiberiEUnAssettoCheSiCambia() throws Exception {
        var coord = come("coordinatore@demo.leone");
        // le fasce demo ci sono e le vede anche lo staff
        assertThat(come("staff@demo.leone").get("/api/fasce").body()).contains("\"inizio\":\"17:00:00\"").contains("\"assetto\":6").contains("\"assetto\":5");
        assertThat(come("staff@demo.leone").post("/api/fasce", "{}").statusCode()).isEqualTo(403);

        // domenica 10:00-10:45 in vasca grande a 6 corsie; una fascia che si accavalla no
        var nuove = coord.post("/api/fasce", """
            {"vascaId":"%s","giorni":[7],"inizio":"10:00","fine":"10:45","assetto":6,"validaDal":"2028-01-01"}""".formatted(VASCA_GRANDE));
        assertThat(nuove.statusCode()).as(nuove.body()).isEqualTo(201);
        String fascia = (String) mappa(lista(nuove).get(0)).get("id");
        var accavallata = coord.post("/api/fasce", """
            {"vascaId":"%s","giorni":[7],"inizio":"10:30","fine":"11:15","assetto":6,"validaDal":"2028-01-01"}""".formatted(VASCA_GRANDE));
        assertThat(accavallata.statusCode()).isEqualTo(409);
        assertThat(accavallata.body()).contains("già una fascia");
        // una vasca non si divide in 4
        assertThat(coord.post("/api/fasce", """
            {"vascaId":"%s","giorni":[7],"inizio":"12:00","fine":"12:45","assetto":4,"validaDal":"2028-01-01"}""".formatted(VASCA_GRANDE)).statusCode()).isEqualTo(400);

        String corso = corso(coord, "Prova fasce");
        String turno = lezione(coord, corso, "10:00", "10:45", CORSIA_2, "null");

        // a 5 corsie: il turno passa sulla corsia 2 della vasca a 5
        var a5 = coord.post("/api/fasce/" + fascia + "/assetto", "{\"assetto\":5}");
        assertThat(a5.statusCode()).as(a5.body()).isEqualTo(200);
        assertThat(json(a5)).containsEntry("turniAggiornati", 1);
        assertThat(coord.get("/api/corsi").body()).contains(CORSIA_2_DI_5);
        // ora la corsia 6 non c'è
        var sesta = coord.post("/api/corsi/" + corso + "/lezioni", """
            {"giornoSettimana":7,"inizio":"10:00","fine":"10:45","validaDal":"2028-01-01","spazi":["%s"]}""".formatted(CORSIA_6));
        assertThat(sesta.statusCode()).isEqualTo(400);
        // si torna a 6 e si occupa la corsia 6: tornare a 5 non si può finché c'è un corso in corsia 6
        assertThat(coord.post("/api/fasce/" + fascia + "/assetto", "{\"assetto\":6}").statusCode()).isEqualTo(200);
        String inSesta = lezione(coord, corso, "10:00", "10:45", CORSIA_6, "null");
        var bloccato = coord.post("/api/fasce/" + fascia + "/assetto", "{\"assetto\":5}");
        assertThat(bloccato.statusCode()).isEqualTo(409);
        assertThat(bloccato.body()).contains("corsia 6");

        // la fascia si sposta di 5 minuti e i suoi turni la seguono
        var spostata = coord.put("/api/fasce/" + fascia, "{\"inizio\":\"10:05\",\"fine\":\"10:50\",\"validaDal\":\"2028-01-01\"}");
        assertThat(json(spostata)).containsEntry("turniAggiornati", 2);
        assertThat(coord.get("/api/corsi").body()).contains("\"inizio\":\"10:05:00\"");

        // con dei turni dentro non si elimina; tolti i turni sì
        assertThat(coord.delete("/api/fasce/" + fascia).statusCode()).isEqualTo(409);
        assertThat(coord.delete("/api/lezioni/" + turno).statusCode()).isEqualTo(204);
        assertThat(coord.delete("/api/lezioni/" + inSesta).statusCode()).isEqualTo(204);
        assertThat(coord.delete("/api/fasce/" + fascia).statusCode()).isEqualTo(204);
    }

    @Test
    void iscrizioniConPostiEPresenzeDellIstruttore() throws Exception {
        var coord = come("coordinatore@demo.leone");
        var segreteria = come("segreteria@demo.leone");
        String corso = corso(coord, "Prova iscrizioni");
        // istruttrice: Giulia
        assertThat(coord.put("/api/corsi/" + corso, "{\"nome\":\"Prova iscrizioni\",\"tipo\":\"ALTRO\",\"istruttoreId\":\"00000000-0000-0000-0000-000000000104\"}").statusCode()).isEqualTo(200);
        String turno = lezione(coord, corso, "12:00", "12:45", CORSIA_4, "2");

        // la segreteria iscrive due persone nuove; la terza no (posti finiti), se non in soprannumero
        var primo = crea(segreteria, "/api/lezioni/" + turno + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Bimbo\",\"cognome\":\"Inventato\",\"dataNascita\":\"2020-05-01\"},\"dal\":\"2028-01-01\"}");
        crea(segreteria, "/api/lezioni/" + turno + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Bimba\",\"cognome\":\"Inventata\",\"dataNascita\":\"2019-02-01\"},\"dal\":\"2028-01-01\"}");
        var pieno = segreteria.post("/api/lezioni/" + turno + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Terzo\",\"cognome\":\"Inventato\"},\"dal\":\"2028-01-01\"}");
        assertThat(pieno.statusCode()).isEqualTo(409);
        assertThat(pieno.body()).contains("al completo (2/2)");
        crea(segreteria, "/api/lezioni/" + turno + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Terzo\",\"cognome\":\"Inventato\"},\"dal\":\"2028-01-01\",\"forza\":true}");
        // la stessa persona due volte no
        String bimbo = (String) primo.get("personaId");
        assertThat(segreteria.post("/api/lezioni/" + turno + "/iscritti", "{\"personaId\":\"" + bimbo + "\",\"dal\":\"2028-02-01\",\"forza\":true}").statusCode()).isEqualTo(409);
        // ricerca per nome (segreteria sì, staff no)
        assertThat(segreteria.get("/api/persone?q=bimbo%20invent").body()).contains(bimbo);
        assertThat(come("staff@demo.leone").get("/api/persone?q=bimbo").statusCode()).isEqualTo(403);

        // nel piano: 3 iscritti su 2 posti, domenica 9 gennaio 2028
        var piano = json(coord.get("/api/piano?da=2028-01-09&a=2028-01-09"));
        @SuppressWarnings("unchecked")
        var occ = ((List<Object>) piano.get("occorrenze")).stream().map(FasceEIscrittiTest::mappa)
            .filter(o -> "Prova iscrizioni".equals(o.get("titolo"))).findFirst().orElseThrow();
        assertThat(occ).containsEntry("iscritti", 3).containsEntry("posti", 2);
        assertThat(piano.get("impiantiIscrizioni").toString()).contains(IMPIANTO);

        // chi vede la lista: l'istruttrice e la segreteria sì, un altro dello staff no
        var giulia = come("giulia@demo.leone");
        var elenco = json(giulia.get("/api/lezioni/" + turno + "/iscritti?data=2028-01-09"));
        assertThat((List<?>) elenco.get("iscritti")).hasSize(3);
        assertThat(elenco).containsEntry("puoIscrivere", false).containsEntry("siTiene", true);
        assertThat(elenco.get("iscritti").toString()).contains("Inventato").contains("eta=7");
        assertThat(come("staff@demo.leone").get("/api/lezioni/" + turno + "/iscritti").statusCode()).isEqualTo(403);
        assertThat(json(segreteria.get("/api/lezioni/" + turno + "/iscritti?data=2028-01-09"))).containsEntry("puoIscrivere", true)
            .containsEntry("puoSegnarePresenze", false);
        // nel futuro le presenze non si segnano
        assertThat(elenco).containsEntry("puoSegnarePresenze", false);
        assertThat(giulia.put("/api/lezioni/" + turno + "/presenze", "{\"data\":\"2028-01-09\",\"presenze\":[{\"personaId\":\"" + bimbo + "\",\"presente\":true}]}")
            .statusCode()).isEqualTo(400);

        // un turno demo già iniziato: l'ultimo lunedì passato, Delfino Giallo (istruttore Luca)
        LocalDate oggi = LocalDate.now(ZoneId.of("Europe/Rome"));
        LocalDate lunedi = oggi.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        var luca = come("staff@demo.leone");
        @SuppressWarnings("unchecked")
        var giallo = ((List<Object>) json(luca.get("/api/piano?da=" + lunedi + "&a=" + lunedi)).get("occorrenze")).stream().map(FasceEIscrittiTest::mappa)
            .filter(o -> "Delfino Giallo".equals(o.get("titolo")) && "17:00:00".equals(o.get("inizio"))).findFirst().orElseThrow();
        assertThat(((Number) giallo.get("iscritti")).intValue()).isBetween(5, 9); // 5-8 inventati, più Tommaso (casi del tornello)
        String turnoGiallo = (String) giallo.get("lezioneId");
        var lista = json(luca.get("/api/lezioni/" + turnoGiallo + "/iscritti?data=" + lunedi));
        assertThat(lista).containsEntry("puoSegnarePresenze", true);
        @SuppressWarnings("unchecked")
        var bambini = ((List<Object>) lista.get("iscritti")).stream().map(FasceEIscrittiTest::mappa).toList();
        String uno = (String) bambini.get(0).get("personaId"), due = (String) bambini.get(1).get("personaId");
        var segnate = json(luca.put("/api/lezioni/" + turnoGiallo + "/presenze", """
            {"data":"%s","presenze":[{"personaId":"%s","presente":true},{"personaId":"%s","presente":false}]}""".formatted(lunedi, uno, due)));
        @SuppressWarnings("unchecked")
        var dopo = ((List<Object>) segnate.get("iscritti")).stream().map(FasceEIscrittiTest::mappa).toList();
        assertThat(dopo.stream().filter(b -> uno.equals(b.get("personaId"))).findFirst().orElseThrow()).containsEntry("presente", true);
        assertThat(dopo.stream().filter(b -> due.equals(b.get("personaId"))).findFirst().orElseThrow()).containsEntry("presente", false);
        // il martedì il turno del lunedì non si tiene
        assertThat(luca.put("/api/lezioni/" + turnoGiallo + "/presenze", """
            {"data":"%s","presenze":[{"personaId":"%s","presente":true}]}""".formatted(lunedi.plusDays(1), uno)).statusCode()).isEqualTo(400);
        // un altro istruttore non segna le presenze di Luca
        assertThat(giulia.put("/api/lezioni/" + turnoGiallo + "/presenze", """
            {"data":"%s","presenze":[{"personaId":"%s","presente":true}]}""".formatted(lunedi, uno)).statusCode()).isEqualTo(403);

        // ritiro: dal giorno dopo non conta più
        String iscrizione = (String) primo.get("iscrizioneId");
        assertThat(segreteria.post("/api/iscrizioni/" + iscrizione + "/termina", "{\"al\":\"2028-01-09\"}").statusCode()).isEqualTo(204);
        assertThat((List<?>) json(segreteria.get("/api/lezioni/" + turno + "/iscritti?data=2028-01-16")).get("iscritti")).hasSize(2);
    }
}
