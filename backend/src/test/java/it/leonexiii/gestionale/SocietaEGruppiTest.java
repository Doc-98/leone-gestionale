package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Impostazioni della società, stagioni con la quota e gruppi di clienti (ADR 0018, parti 1 e 2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("demo")
class SocietaEGruppiTest {

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

    @Test
    void datiDellaSocietaEStagioni() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        assertThat(json(segr.get("/api/societa"))).containsEntry("nome", "Leone XIII Sport");
        String dati = """
            {"nome":"Leone XIII Sport","ragioneSociale":"Leone XIII Sport S.S.D. a r.l.","partitaIva":"%s","citta":"Milano","provincia":"mi","giorniAnticipo":30}""";
        assertThat(segr.put("/api/societa", dati.formatted("12345678901")).statusCode()).isEqualTo(403);
        assertThat(admin.put("/api/societa", dati.formatted("123")).statusCode()).isEqualTo(400);
        var ok = admin.put("/api/societa", dati.formatted("123 456 789 01"));
        assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);
        assertThat(json(ok)).containsEntry("partitaIva", "12345678901").containsEntry("provincia", "MI");

        // le stagioni demo: 1/9–31/8 con la quota; una che si accavalla non si può aggiungere
        String stagioni = segr.get("/api/stagioni").body();
        assertThat(stagioni).contains("\"quota\":35.00").contains("-09-01").contains("-08-31");
        assertThat(admin.post("/api/stagioni", "{\"nome\":\"Doppia\",\"dal\":\"2026-12-01\",\"al\":\"2027-03-31\"}").statusCode()).isEqualTo(409);
        assertThat(admin.post("/api/stagioni", "{\"nome\":\"Rovescia\",\"dal\":\"2040-09-01\",\"al\":\"2040-08-31\"}").statusCode()).isEqualTo(400);
        var lontana = admin.post("/api/stagioni", "{\"nome\":\"2040\",\"dal\":\"2040-01-01\",\"al\":\"2040-12-31\",\"quota\":40}");
        assertThat(lontana.statusCode()).as(lontana.body()).isEqualTo(201);
        String id = (String) json(lontana).get("id");
        assertThat(segr.post("/api/stagioni", "{\"nome\":\"2041\",\"dal\":\"2041-01-01\",\"al\":\"2041-12-31\"}").statusCode()).isEqualTo(403);
        assertThat(admin.put("/api/stagioni/" + id, "{\"nome\":\"2040\",\"dal\":\"2040-01-01\",\"al\":\"2040-12-31\",\"quota\":45,\"quotaRimborsabile\":true}").statusCode()).isEqualTo(200);
        assertThat(segr.get("/api/stagioni").body()).contains("\"quota\":45").contains("\"quotaRimborsabile\":true");
        assertThat(admin.delete("/api/stagioni/" + id).statusCode()).isEqualTo(204);

        // il tesseramento senza dati usa la stagione in corso, con la sua quota
        String cliente = (String) json(segr.post("/api/clienti", "{\"nome\":\"Stagionale\",\"cognome\":\"Prova\"}")).get("id");
        assertThat(segr.post("/api/clienti/" + cliente + "/tesseramenti", "{}").statusCode()).isEqualTo(201);
        assertThat(segr.post("/api/clienti/" + cliente + "/tesseramenti", "{\"stagione\":\"Inesistente\"}").statusCode()).isEqualTo(400);
        String scheda = segr.get("/api/clienti/" + cliente).body();
        assertThat(scheda).contains("\"importo\":35.00").contains("\"stagioniTesseramento\":[{");
    }

    @Test
    void gruppiDiClienti() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        assertThat(segr.get("/api/gruppi").body()).contains("Alunni dell'Istituto").contains("\"persone\":");

        // li crea chi ha «Impostazioni», ci mette le persone chi ha «Clienti»
        assertThat(segr.post("/api/gruppi", "{\"nome\":\"Convenzione aziendale\"}").statusCode()).isEqualTo(403);
        var r = admin.post("/api/gruppi", "{\"nome\":\"Convenzione aziendale\",\"descrizione\":\"Dipendenti di un'azienda convenzionata\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String gruppo = (String) json(r).get("id");
        assertThat(admin.post("/api/gruppi", "{\"nome\":\"convenzione AZIENDALE\"}").statusCode()).isEqualTo(409);

        String persona = (String) json(segr.post("/api/clienti", "{\"nome\":\"Convenzionato\",\"cognome\":\"Prova\"}")).get("id");
        var dentro = segr.post("/api/clienti/" + persona + "/gruppi", "{\"gruppoId\":\"" + gruppo + "\"}");
        assertThat(dentro.statusCode()).as(dentro.body()).isEqualTo(201);
        assertThat(segr.post("/api/clienti/" + persona + "/gruppi", "{\"gruppoId\":\"" + gruppo + "\"}").statusCode()).isEqualTo(409);
        assertThat(segr.get("/api/clienti/" + persona).body()).contains("\"gruppi\":[{").contains("Convenzione aziendale");
        assertThat(segr.get("/api/gruppi/" + gruppo + "/persone").body()).contains("Convenzionato");
        assertThat(come("staff@demo.leone").get("/api/gruppi").statusCode()).isEqualTo(403);

        // si toglie (entrato oggi: come se non fosse mai entrato); un gruppo disattivato non accoglie nessuno
        assertThat(segr.post("/api/clienti/appartenenze/" + json(dentro).get("id") + "/chiudi", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/clienti/" + persona).body()).contains("\"gruppi\":[]");
        assertThat(admin.put("/api/gruppi/" + gruppo, "{\"nome\":\"Convenzione aziendale\",\"attivo\":false}").statusCode()).isEqualTo(204);
        assertThat(segr.post("/api/clienti/" + persona + "/gruppi", "{\"gruppoId\":\"" + gruppo + "\"}").statusCode()).isEqualTo(409);
    }
}
