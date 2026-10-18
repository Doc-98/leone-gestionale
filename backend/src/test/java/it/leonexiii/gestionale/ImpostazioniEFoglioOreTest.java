package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Mansioni, postazioni, tariffe, settimana tipo e foglio ore, sui dati demo (date nel 2028, lontane dagli altri test). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class ImpostazioniEFoglioOreTest {

    static final String IMPIANTO = "00000000-0000-0000-0000-0000000000b1";
    static final String VASCA_GRANDE = "00000000-0000-0000-0000-0000000000c1";
    static final String GIULIA = "00000000-0000-0000-0000-000000000104";
    static final String MARTA = "00000000-0000-0000-0000-000000000105";
    static final String SPOGL_F_1 = "00000000-0000-0000-0000-000000000403";
    static final String ATRIO = "00000000-0000-0000-0000-000000000405";
    static final String MODELLO = "00000000-0000-0000-0000-000000000501";

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

    private String crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return (String) json(r).get("id");
    }

    @Test
    void mansioniEPostazioniSiGestisconoDallApp() throws Exception {
        var coord = come("coordinatore@demo.leone");
        assertThat(come("staff@demo.leone").get("/api/staff/mansioni").statusCode()).isEqualTo(403);
        assertThat(lista(coord.get("/api/staff/impianti"))).hasSize(1);
        assertThat(coord.get("/api/staff/spazi").body()).contains("Vasca grande");

        // la voce deve essere una colonna del modello
        assertThat(coord.post("/api/staff/mansioni", """
            {"impiantoId":"%s","nome":"Istruttore","voceFoglioOre":"INVENTATA"}""".formatted(IMPIANTO)).statusCode()).isEqualTo(400);
        var r = coord.post("/api/staff/mansioni", """
            {"impiantoId":"%s","nome":"Istruttore scuola nuoto","voceFoglioOre":"ISTRUTTORE Scuola Nuoto"}""".formatted(IMPIANTO));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        assertThat(json(r)).containsEntry("codice", "ISTRUTTORE_SCUOLA_NUOTO");
        String mansione = (String) json(r).get("id");
        // stesso nome: codice diverso
        assertThat(json(coord.post("/api/staff/mansioni", """
            {"impiantoId":"%s","nome":"Istruttore scuola nuoto"}""".formatted(IMPIANTO)))).containsEntry("codice", "ISTRUTTORE_SCUOLA_NUOTO_2");

        String postazione = crea(coord, "/api/staff/mansioni/" + mansione + "/postazioni", """
            {"nome":"Vasca grande · corsia 1","spazioId":"%s"}""".formatted(VASCA_GRANDE));
        assertThat(coord.get("/api/staff/postazioni").body()).contains("Vasca grande · corsia 1");

        // un turno futuro sulla postazione, poi la si disattiva: sparisce dalle postazioni assegnabili
        crea(coord, "/api/turni", """
            {"postazioneId":"%s","data":"2028-01-10","inizio":"17:00","fine":"18:00","personaId":null}""".formatted(postazione));
        var dis = coord.put("/api/staff/postazioni/" + postazione, "{\"nome\":\"Vasca grande · corsia 1\",\"spazioId\":null,\"attiva\":false}");
        assertThat(dis.statusCode()).as(dis.body()).isEqualTo(200);
        assertThat(((Number) json(dis).get("turniFuturi")).intValue()).isEqualTo(1);
        assertThat(coord.get("/api/staff/postazioni").body()).doesNotContain("Vasca grande · corsia 1");
        assertThat(coord.get("/api/staff/mansioni").body()).contains("Vasca grande · corsia 1");

        // rinomina con la voce
        var ren = coord.put("/api/staff/mansioni/" + mansione, "{\"nome\":\"Istruttore\",\"voceFoglioOre\":\"ISTRUTTORE Scuola Nuoto\"}");
        assertThat(json(ren)).containsEntry("nome", "Istruttore").containsEntry("voceFoglioOre", "ISTRUTTORE Scuola Nuoto");
    }

    @Test
    void unaNuovaTariffaChiudeLaPrecedente() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String mansione = crea(coord, "/api/staff/mansioni", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"Assistente bagnanti\"}");

        String prima = crea(coord, "/api/staff/tariffe", "{\"mansioneId\":\"" + mansione + "\",\"euroOra\":10.00,\"dal\":\"2027-01-01\"}");
        String seconda = crea(coord, "/api/staff/tariffe", "{\"mansioneId\":\"" + mansione + "\",\"euroOra\":10.50,\"dal\":\"2028-01-01\"}");
        var elenco = lista(coord.get("/api/staff/tariffe")).stream().map(ImpostazioniEFoglioOreTest::mappa)
            .filter(t -> mansione.equals(t.get("mansioneId"))).toList();
        assertThat(elenco).hasSize(2);
        assertThat(elenco.stream().filter(t -> prima.equals(t.get("id"))).findFirst().orElseThrow()).containsEntry("al", "2027-12-31");

        // stessa data: si corregge l'importo, non si aggiunge
        var corr = coord.post("/api/staff/tariffe", "{\"mansioneId\":\"" + mansione + "\",\"euroOra\":10.75,\"dal\":\"2028-01-01\"}");
        assertThat(json(corr)).containsEntry("id", seconda);
        // prima di una tariffa già esistente: conflitto
        assertThat(coord.post("/api/staff/tariffe", "{\"mansioneId\":\"" + mansione + "\",\"euroOra\":9,\"dal\":\"2026-06-01\"}").statusCode()).isEqualTo(409);
        // eccezione per una persona: ha una sua storia
        crea(coord, "/api/staff/tariffe", "{\"mansioneId\":\"" + mansione + "\",\"personaId\":\"" + GIULIA + "\",\"euroOra\":12,\"dal\":\"2028-01-01\"}");
        // correzione con chiusura
        var chiusa = coord.put("/api/staff/tariffe/" + seconda, "{\"euroOra\":10.75,\"al\":\"2028-12-31\"}");
        assertThat(json(chiusa)).containsEntry("al", "2028-12-31");
        assertThat(coord.put("/api/staff/tariffe/" + seconda, "{\"euroOra\":-1,\"al\":null}").statusCode()).isEqualTo(400);
    }

    @Test
    void laSettimanaTipoSiCreaCopiaModificaEdElimina() throws Exception {
        var coord = come("coordinatore@demo.leone");
        int righeDemo = ((List<?>) lista(coord.get("/api/modelli")).stream().map(ImpostazioniEFoglioOreTest::mappa)
            .filter(m -> MODELLO.equals(m.get("id"))).findFirst().orElseThrow().get("righe")).size();

        var r = coord.post("/api/modelli", """
            {"impiantoId":"%s","nome":"Stagione 2028","validoDal":"2028-02-01","validoAl":"2028-02-29","copiaDa":"%s"}""".formatted(IMPIANTO, MODELLO));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String copia = (String) json(r).get("id");
        List<?> righe = (List<?>) json(r).get("righe");
        assertThat(righe).hasSize(righeDemo);

        // sposto una riga e la lascio scoperta
        String riga = (String) mappa(righe.get(0)).get("id");
        var mod = coord.put("/api/modelli/" + copia + "/righe/" + riga, """
            {"postazioneId":"%s","giornoSettimana":7,"inizio":"09:00","fine":"12:00","personaId":null}""".formatted(SPOGL_F_1));
        assertThat(mod.statusCode()).as(mod.body()).isEqualTo(200);
        assertThat(mod.body()).contains("\"giornoSettimana\":7");

        var rin = coord.put("/api/modelli/" + copia, "{\"nome\":\"Febbraio 2028\",\"validoDal\":\"2028-02-01\",\"validoAl\":\"2028-02-29\"}");
        assertThat(json(rin)).containsEntry("nome", "Febbraio 2028");
        assertThat(coord.put("/api/modelli/" + copia, "{\"nome\":\"x\",\"validoDal\":\"2028-02-10\",\"validoAl\":\"2028-02-01\"}").statusCode()).isEqualTo(400);

        // usato per generare: non si elimina
        assertThat(coord.post("/api/modelli/" + copia + "/genera", "{\"mese\":\"2028-02\"}").statusCode()).isEqualTo(200);
        assertThat(coord.delete("/api/modelli/" + copia).statusCode()).isEqualTo(409);

        // vuoto e mai usato: sì
        String vuoto = crea(coord, "/api/modelli", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"Prova\",\"validoDal\":\"2029-01-01\"}");
        assertThat(coord.delete("/api/modelli/" + vuoto).statusCode()).isEqualTo(204);
        assertThat(coord.get("/api/modelli").body()).doesNotContain(vuoto);
    }

    @Test
    void ilFoglioOreSegueIlModelloDellaSocieta() throws Exception {
        var coord = come("coordinatore@demo.leone");
        assertThat(coord.get("/api/foglio-ore/voci").body()).contains("ASSISTENTE SPOGLIATOIO");

        // Giulia: spogliatoio il 3 (3,5 h), atrio il 3 e il 4 (2 h ciascuno)
        crea(coord, "/api/turni", turno(SPOGL_F_1, "2028-05-03", "09:00", "12:30", GIULIA));
        crea(coord, "/api/turni", turno(ATRIO, "2028-05-03", "15:00", "17:00", GIULIA));
        crea(coord, "/api/turni", turno(ATRIO, "2028-05-04", "15:00", "17:00", GIULIA));
        // il 10 Giulia sostituisce Marta
        String tMarta = crea(coord, "/api/turni", turno(SPOGL_F_1, "2028-05-10", "15:00", "19:00", MARTA));
        var marta = come("marta@demo.leone");
        String rich = crea(marta, "/api/richieste", "{\"tipo\":\"SOSTITUZIONE\",\"turnoId\":\"" + tMarta + "\",\"collegaId\":\"" + GIULIA + "\"}");
        assertThat(come("giulia@demo.leone").post("/api/richieste/" + rich + "/accetta", "").statusCode()).isEqualTo(200);
        assertThat(coord.post("/api/richieste/" + rich + "/approva", "{}").statusCode()).isEqualTo(200);

        var r = coord.scarica("/api/foglio-ore/persone/" + GIULIA + "?mese=2028-05");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Disposition").orElse("")).contains("FOGLIO ORE").contains("Maggio 2028");
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(r.body()))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(wb.getSheetName(0)).isEqualTo("Maggio 28");
            assertThat(s.getRow(4).getCell(4).getStringCellValue()).isEqualTo("Giulia Conti");
            assertThat(s.getRow(5).getCell(5).getStringCellValue()).isEqualTo("Maggio 2028");
            Row voci = s.getRow(10);
            int spogliatoio = colonna(voci, "ASSISTENTE SPOGLIATOIO");
            int atrio = colonna(voci, "Assistente atrio");
            int sost = colonna(voci, "Sostituzioni");
            assertThat(atrio).isGreaterThan(spogliatoio);
            assertThat(sost).isGreaterThan(atrio);
            assertThat(s.getRow(11 + 2).getCell(spogliatoio).getNumericCellValue()).isEqualTo(3.5);
            assertThat(s.getRow(11 + 2).getCell(atrio).getNumericCellValue()).isEqualTo(2.0);
            assertThat(s.getRow(11 + 3).getCell(atrio).getNumericCellValue()).isEqualTo(2.0);
            assertThat(s.getRow(11 + 9).getCell(spogliatoio).getNumericCellValue()).isEqualTo(4.0);
            assertThat(s.getRow(11 + 9).getCell(sost).getStringCellValue()).contains("sost. Marta Ricci");
            assertThat(s.getRow(42).getCell(atrio).getCellFormula()).isEqualTo("SUM(" + lettera(atrio) + "12:" + lettera(atrio) + "42)");
        }

        // tutti in un .zip; lo staff non può scaricare quello degli altri, ma il suo sì
        var zip = coord.scarica("/api/foglio-ore/tutti?mese=2028-05");
        assertThat(zip.statusCode()).isEqualTo(200);
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip.body()))) {
            assertThat(in.getNextEntry().getName()).startsWith("FOGLIO ORE - ").endsWith("Maggio 2028.xlsx");
        }
        var giulia = come("giulia@demo.leone");
        assertThat(giulia.scarica("/api/foglio-ore/persone/" + MARTA + "?mese=2028-05").statusCode()).isEqualTo(403);
        assertThat(giulia.scarica("/api/foglio-ore/mio?mese=2028-05").statusCode()).isEqualTo(200);
    }

    private static String turno(String postazione, String data, String inizio, String fine, String persona) {
        return """
            {"postazioneId":"%s","data":"%s","inizio":"%s","fine":"%s","personaId":"%s"}""".formatted(postazione, data, inizio, fine, persona);
    }

    private static int colonna(Row r, String intestazione) {
        for (var c : r) if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING && c.getStringCellValue().strip().equals(intestazione)) return c.getColumnIndex();
        throw new AssertionError("Colonna mancante: " + intestazione);
    }

    private static String lettera(int col) {
        return org.apache.poi.ss.util.CellReference.convertNumToColString(col);
    }
}
