package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fase 1 sui dati demo. Ogni test usa giorni propri (nel 2027, lontano dai turni demo generati intorno a oggi),
 * così i test non si disturbano tra loro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class PianificazioneERichiesteTest {

    // Persone e postazioni dei dati demo
    static final String PAOLO = "00000000-0000-0000-0000-000000000102";
    static final String LUCA = "00000000-0000-0000-0000-000000000103";
    static final String GIULIA = "00000000-0000-0000-0000-000000000104";
    static final String MARTA = "00000000-0000-0000-0000-000000000105";
    static final String CHIARA_ADMIN = "00000000-0000-0000-0000-000000000101";
    static final String SPOGL_M_1 = "00000000-0000-0000-0000-000000000401";
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

    private static String turno(String postazione, String data, String inizio, String fine, String persona) {
        return """
            {"postazioneId":"%s","data":"%s","inizio":"%s","fine":"%s","personaId":%s}"""
            .formatted(postazione, data, inizio, fine, persona == null ? "null" : "\"" + persona + "\"");
    }

    private String creaTurno(ClientApi coord, String postazione, String data, String inizio, String fine, String persona) throws Exception {
        var r = coord.post("/api/turni", turno(postazione, data, inizio, fine, persona));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return (String) json(r).get("id");
    }

    @Test
    void ilCoordinatoreCreaModificaEAnnullaITurni() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String id = creaTurno(coord, SPOGL_F_1, "2027-03-01", "10:00", "12:00", GIULIA);

        // la stessa persona non può avere turni che si accavallano
        var doppio = coord.post("/api/turni", turno(ATRIO, "2027-03-01", "11:00", "13:00", GIULIA));
        assertThat(doppio.statusCode()).isEqualTo(409);
        assertThat(doppio.body()).contains("già un turno");
        // un turno scoperto invece sì
        assertThat(coord.post("/api/turni", turno(ATRIO, "2027-03-01", "11:00", "13:00", null)).statusCode()).isEqualTo(201);

        var mod = coord.put("/api/turni/" + id, turno(SPOGL_F_1, "2027-03-01", "13:00", "14:30", GIULIA));
        assertThat(mod.statusCode()).as(mod.body()).isEqualTo(200);
        assertThat(json(mod)).containsEntry("ore", 1.5);

        var ann = coord.post("/api/turni/" + id + "/annulla", "");
        assertThat(json(ann)).containsEntry("stato", "ANNULLATO");

        // chi non è staff dell'impianto non riceve turni; lo staff non pianifica
        assertThat(coord.post("/api/turni", turno(ATRIO, "2027-03-02", "10:00", "11:00", CHIARA_ADMIN)).statusCode()).isEqualTo(400);
        assertThat(come("staff@demo.leone").post("/api/turni", turno(ATRIO, "2027-03-02", "10:00", "11:00", null)).statusCode()).isEqualTo(403);
    }

    @Test
    void generareIlMeseDalModelloSenzaDoppioni() throws Exception {
        var coord = come("coordinatore@demo.leone");
        var prima = json(coord.post("/api/modelli/" + MODELLO + "/genera", "{\"mese\":\"2027-02\"}"));
        // febbraio 2027: 4 settimane esatte; 8 righe con persona e 1 da assegnare (il venerdì dei piccoli)
        assertThat(prima).containsEntry("creati", 32).containsEntry("scoperti", 4).containsEntry("giaPresenti", 0);

        var seconda = json(coord.post("/api/modelli/" + MODELLO + "/genera", "{\"mese\":\"2027-02\"}"));
        assertThat(seconda).containsEntry("creati", 0).containsEntry("giaPresenti", 36);

        var tutti = lista(coord.get("/api/turni?da=2027-02-01&a=2027-02-28"));
        assertThat(tutti).hasSize(36);
        assertThat(tutti.stream().filter(t -> mappa(t).get("persona") == null)).hasSize(4);

        assertThat(coord.post("/api/modelli/" + MODELLO + "/genera", "{\"mese\":\"febbraio\"}").statusCode()).isEqualTo(400);
        assertThat(come("staff@demo.leone").post("/api/modelli/" + MODELLO + "/genera", "{\"mese\":\"2027-03\"}").statusCode()).isEqualTo(403);
    }

    @Test
    void sostituzioneApertaAccettataDaUnCollegaEApprovata() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String t = creaTurno(coord, SPOGL_M_1, "2027-04-05", "09:00", "10:00", LUCA);

        var luca = come("staff@demo.leone");
        var r = luca.post("/api/richieste", "{\"tipo\":\"SOSTITUZIONE\",\"turnoId\":\"" + t + "\",\"messaggio\":\"Visita medica\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String id = (String) json(r).get("id");
        assertThat(json(r)).containsEntry("stato", "IN_ATTESA_COLLEGA");

        // una seconda richiesta sullo stesso turno no
        assertThat(luca.post("/api/richieste", "{\"tipo\":\"SOSTITUZIONE\",\"turnoId\":\"" + t + "\"}").statusCode()).isEqualTo(409);

        var marta = come("marta@demo.leone");
        assertThat(marta.get("/api/richieste/aperte").body()).contains(id);
        assertThat(json(marta.post("/api/richieste/" + id + "/accetta", ""))).containsEntry("stato", "IN_ATTESA_APPROVAZIONE");

        assertThat(luca.post("/api/richieste/" + id + "/approva", "{}").statusCode()).isEqualTo(403);
        assertThat(coord.get("/api/richieste").body()).contains(id);
        var ok = coord.post("/api/richieste/" + id + "/approva", "{\"nota\":\"Ok\"}");
        assertThat(json(ok)).containsEntry("stato", "APPROVATA");

        var giorno = lista(coord.get("/api/turni?da=2027-04-05&a=2027-04-05"));
        var turno = giorno.stream().map(PianificazioneERichiesteTest::mappa).filter(x -> x.get("id").equals(t)).findFirst().orElseThrow();
        assertThat(mappa(turno.get("persona"))).containsEntry("id", MARTA);
    }

    @Test
    void scambioTraDueColleghi() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String tLuca = creaTurno(coord, SPOGL_M_1, "2027-04-12", "09:00", "10:00", LUCA);
        String tGiulia = creaTurno(coord, SPOGL_F_1, "2027-04-13", "09:00", "10:00", GIULIA);

        var luca = come("staff@demo.leone");
        var r = luca.post("/api/richieste", "{\"tipo\":\"SCAMBIO\",\"turnoId\":\"" + tLuca + "\",\"turnoCollegaId\":\"" + tGiulia + "\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String id = (String) json(r).get("id");

        assertThat(come("marta@demo.leone").post("/api/richieste/" + id + "/accetta", "").statusCode()).isEqualTo(403);
        var giulia = come("giulia@demo.leone");
        assertThat(giulia.get("/api/richieste/mie").body()).contains(id);
        assertThat(json(giulia.post("/api/richieste/" + id + "/accetta", ""))).containsEntry("stato", "IN_ATTESA_APPROVAZIONE");
        assertThat(json(coord.post("/api/richieste/" + id + "/approva", ""))).containsEntry("stato", "APPROVATA");

        var turni = lista(coord.get("/api/turni?da=2027-04-12&a=2027-04-13")).stream().map(PianificazioneERichiesteTest::mappa).toList();
        assertThat(mappa(turni.stream().filter(x -> x.get("id").equals(tLuca)).findFirst().orElseThrow().get("persona"))).containsEntry("id", GIULIA);
        assertThat(mappa(turni.stream().filter(x -> x.get("id").equals(tGiulia)).findFirst().orElseThrow().get("persona"))).containsEntry("id", LUCA);
    }

    @Test
    void cambioOrarioRespintoEUnoCheSiAccavalla() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String t1 = creaTurno(coord, SPOGL_M_1, "2027-04-19", "09:00", "10:00", LUCA);
        creaTurno(coord, ATRIO, "2027-04-19", "11:00", "12:00", LUCA);
        var luca = come("staff@demo.leone");

        // turno di un altro: vietato
        String tGiulia = creaTurno(coord, SPOGL_F_1, "2027-04-19", "09:00", "10:00", GIULIA);
        assertThat(luca.post("/api/richieste", "{\"tipo\":\"CAMBIO_ORARIO\",\"turnoId\":\"" + tGiulia + "\",\"nuovoInizio\":\"09:30\",\"nuovoFine\":\"10:00\"}").statusCode()).isEqualTo(403);

        // il nuovo orario si accavalla con l'altro turno di Luca: all'approvazione 409
        var r = luca.post("/api/richieste", "{\"tipo\":\"CAMBIO_ORARIO\",\"turnoId\":\"" + t1 + "\",\"nuovoInizio\":\"09:00\",\"nuovoFine\":\"11:30\"}");
        assertThat(json(r)).containsEntry("stato", "IN_ATTESA_APPROVAZIONE");
        String id = (String) json(r).get("id");
        assertThat(coord.post("/api/richieste/" + id + "/approva", "").statusCode()).isEqualTo(409);
        assertThat(json(coord.post("/api/richieste/" + id + "/respingi", "{\"nota\":\"Si sovrappone all'atrio\"}")))
            .containsEntry("stato", "RESPINTA").containsEntry("notaDecisione", "Si sovrappone all'atrio");

        // ora una nuova richiesta sullo stesso turno si può fare, e approvata cambia l'orario
        var r2 = luca.post("/api/richieste", "{\"tipo\":\"CAMBIO_ORARIO\",\"turnoId\":\"" + t1 + "\",\"nuovoInizio\":\"09:00\",\"nuovoFine\":\"10:45\"}");
        assertThat(json(coord.post("/api/richieste/" + json(r2).get("id") + "/approva", ""))).containsEntry("stato", "APPROVATA");
        var mieiTurni = lista(luca.get("/api/turni/miei?da=2027-04-19&a=2027-04-19")).stream().map(PianificazioneERichiesteTest::mappa).toList();
        assertThat(mieiTurni.stream().filter(x -> x.get("id").equals(t1)).findFirst().orElseThrow()).containsEntry("fine", "10:45:00");
    }

    @Test
    void annullareUnaRichiestaESoloIlRichiedente() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String t = creaTurno(coord, SPOGL_M_1, "2027-04-26", "09:00", "10:00", LUCA);
        var luca = come("staff@demo.leone");
        String id = (String) json(luca.post("/api/richieste", "{\"tipo\":\"SOSTITUZIONE\",\"turnoId\":\"" + t + "\",\"collegaId\":\"" + MARTA + "\"}")).get("id");
        assertThat(come("marta@demo.leone").post("/api/richieste/" + id + "/annulla", "").statusCode()).isEqualTo(403);
        assertThat(json(luca.post("/api/richieste/" + id + "/annulla", ""))).containsEntry("stato", "ANNULLATA");
        assertThat(luca.post("/api/richieste/" + id + "/annulla", "").statusCode()).isEqualTo(409);
    }

    @Test
    void riepilogoOreECompensi() throws Exception {
        var coord = come("coordinatore@demo.leone");
        String mese = YearMonth.now().toString();
        var r = json(coord.get("/api/riepilogo?mese=" + mese));
        var righe = ((List<?>) r.get("righe")).stream().map(PianificazioneERichiesteTest::mappa).toList();
        var paolo = righe.stream().filter(x -> mappa(x.get("persona")).get("id").equals(PAOLO)).findFirst().orElseThrow();
        // Paolo fa l'atrio con la sua tariffa personale (11 €/h invece di 9,50)
        var voce = mappa(((List<?>) paolo.get("voci")).getFirst());
        assertThat(voce).containsEntry("mansione", "Assistente atrio");
        assertThat(((Number) voce.get("euroOra")).doubleValue()).isEqualTo(11.0);
        assertThat(((Number) paolo.get("compenso")).doubleValue()).isEqualTo(((Number) paolo.get("ore")).doubleValue() * 11.0);

        var staff = come("staff@demo.leone");
        assertThat(staff.get("/api/riepilogo?mese=" + mese).statusCode()).isEqualTo(403);
        var mio = json(staff.get("/api/riepilogo/mio?mese=" + mese));
        assertThat((List<?>) mio.get("righe")).hasSize(1);

        var csv = coord.get("/api/riepilogo/csv?mese=" + mese);
        assertThat(csv.headers().firstValue("content-type").orElse("")).startsWith("text/csv");
        assertThat(csv.body()).contains("Persona;Turni;Ore;Compenso").contains("Paolo Galli;");
    }

    @Test
    void elenchiPerLaPianificazione() throws Exception {
        var coord = come("coordinatore@demo.leone");
        assertThat(lista(coord.get("/api/staff/postazioni"))).hasSize(5);
        var persone = coord.get("/api/staff/persone").body();
        assertThat(persone).contains("Luca Bianchi").contains("Giulia Conti").doesNotContain("Chiara Ferri");
        assertThat(lista(coord.get("/api/modelli"))).hasSize(1);
        var staff = come("staff@demo.leone");
        assertThat(staff.get("/api/staff/persone").statusCode()).isEqualTo(403);
        // lo staff vede i colleghi (non sé stesso) e i loro turni, per proporre scambi
        assertThat(staff.get("/api/staff/colleghi").body()).contains("Giulia Conti").doesNotContain("Luca Bianchi");
        String oggi = java.time.LocalDate.now().toString();
        assertThat(staff.get("/api/turni/staff?da=" + oggi + "&a=" + java.time.LocalDate.now().plusDays(7)).body()).contains("Marta Ricci");
    }
}
