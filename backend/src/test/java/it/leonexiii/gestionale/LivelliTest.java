package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Livelli dei corsi (ADR 0026): livello successivo, esiti di fine stagione, passaggio durante l'anno con la lista d'attesa. */
class LivelliTest extends ProvaApi {

    static final String IMPIANTO = "00000000-0000-0000-0000-0000000000b1";
    static final String CORSIA_5 = "00000000-0000-0000-0000-000000000105";
    static final String LUCA = "00000000-0000-0000-0000-000000000103";

    @SuppressWarnings("unchecked")
    private String turno(ClientApi coord, String corso, String inizio, String fine, String istruttore, int posti) throws Exception {
        var r = crea(coord, "/api/corsi/" + corso + "/lezioni", """
            {"giornoSettimana":7,"inizio":"%s","fine":"%s","validaDal":"2026-01-04","spazi":["%s"],"istruttori":%s,"posti":%d}"""
            .formatted(inizio, fine, CORSIA_5, istruttore == null ? "[]" : "[\"" + istruttore + "\"]", posti));
        return (String) ((Map<String, Object>) r.get("valore")).get("id");
    }

    private static String corso(String nome, String successivo) {
        return "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"" + nome + "\",\"tipo\":\"SCUOLA_NUOTO\""
            + (successivo == null ? "" : ",\"livelloSuccessivoId\":\"" + successivo + "\"") + "}";
    }

    @Test
    void esitiEPassaggiDiLivello() throws Exception {
        var coord = come("coordinatore@demo.leone");
        var segr = come("segreteria@demo.leone");
        var luca = come("staff@demo.leone");
        var giulia = come("giulia@demo.leone");
        LocalDate oggi = LocalDate.now(ZoneId.of("Europe/Rome"));
        String unMeseFa = oggi.minusDays(30).toString();

        // due livelli: Verde viene dopo Giallo; la catena non può girare in tondo né uscire dall'impianto
        String verde = creaId(coord, "/api/corsi", corso("Prova Verde", null));
        String giallo = creaId(coord, "/api/corsi", corso("Prova Giallo", verde));
        assertThat(lista(coord.get("/api/corsi")).stream().map(ProvaApi::mappa).filter(c -> c.get("id").equals(giallo)).findFirst().orElseThrow())
            .containsEntry("livelloSuccessivoId", verde);
        var giro = coord.put("/api/corsi/" + verde, corso("Prova Verde", giallo));
        assertThat(giro.statusCode()).isEqualTo(400);
        assertThat(giro.body()).contains("in tondo");
        assertThat(coord.put("/api/corsi/" + verde, corso("Prova Verde", verde)).statusCode()).isEqualTo(400);

        // Luca insegna nel turno del Giallo; nel Verde un solo posto, già preso, e qualcuno che aspetta
        String tGiallo = turno(coord, giallo, "18:00", "18:45", LUCA, 8);
        String tVerde = turno(coord, verde, "19:00", "19:45", null, 1);
        var gina = crea(segr, "/api/lezioni/" + tGiallo + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Gina\",\"cognome\":\"Livelli\",\"dataNascita\":\"2018-05-01\"},\"dal\":\"" + unMeseFa + "\"}");
        var marco = crea(segr, "/api/lezioni/" + tGiallo + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Marco\",\"cognome\":\"Livelli\"},\"dal\":\"" + unMeseFa + "\"}");
        crea(segr, "/api/lezioni/" + tVerde + "/iscritti", "{\"nuovaPersona\":{\"nome\":\"Pieno\",\"cognome\":\"Livelli\"},\"dal\":\"" + unMeseFa + "\"}");
        var altro = crea(segr, "/api/lezioni/" + tVerde + "/attesa", "{\"nuovaPersona\":{\"nome\":\"Altro\",\"cognome\":\"Livelli\"}}");
        assertThat(altro).containsEntry("posizione", 1);

        // Luca vede il suo turno; Giulia (né istruttrice del turno né col permesso «Livelli») no
        var pagina = json(luca.get("/api/livelli"));
        assertThat(pagina).containsEntry("tutti", false).containsKey("stagione");
        var mio = elenco(pagina.get("turni")).stream().filter(t -> t.get("lezioneId").equals(tGiallo)).findFirst().orElseThrow();
        assertThat(mio).containsEntry("mio", true).containsEntry("successivo", "Prova Verde").containsEntry("iscritti", 2).containsEntry("passano", 0);
        assertThat(giulia.get("/api/livelli").body()).doesNotContain(tGiallo);
        assertThat(giulia.get("/api/livelli/turni/" + tGiallo).statusCode()).isEqualTo(403);
        // il coordinatore ha «Livelli»: vede tutti i turni
        assertThat(json(coord.get("/api/livelli"))).containsEntry("tutti", true);
        assertThat(coord.get("/api/livelli").body()).contains(tGiallo);
        // il Verde non ha un livello successivo: niente esiti
        assertThat(coord.get("/api/livelli/turni/" + tVerde).statusCode()).isEqualTo(409);

        // esiti di fine stagione: Gina passa, Marco continua; poi l'esito di Marco si toglie
        String esiti = "{\"esiti\":[{\"personaId\":\"%s\",\"esito\":\"PASSA\"},{\"personaId\":\"%s\",\"esito\":\"CONTINUA\"}]}"
            .formatted(gina.get("personaId"), marco.get("personaId"));
        var dopo = json(luca.put("/api/livelli/turni/" + tGiallo + "/esiti", esiti));
        assertThat(mappa(dopo.get("turno"))).containsEntry("passano", 1).containsEntry("continuano", 1);
        assertThat(elenco(dopo.get("bambini"))).extracting(b -> b.get("nome") + ":" + b.get("esito")).containsExactly("Gina:PASSA", "Marco:CONTINUA");
        assertThat(luca.put("/api/livelli/turni/" + tGiallo + "/esiti", "{\"esiti\":[{\"personaId\":\"" + altro.get("personaId") + "\",\"esito\":\"PASSA\"}]}").statusCode())
            .isEqualTo(400);
        var tolto = json(luca.put("/api/livelli/turni/" + tGiallo + "/esiti", "{\"esiti\":[{\"personaId\":\"" + marco.get("personaId") + "\",\"esito\":null}]}"));
        assertThat(mappa(tolto.get("turno"))).containsEntry("passano", 1).containsEntry("continuano", 0);

        // durante l'anno: Gina passa adesso, tiene il suo turno ed è in attesa nel Verde davanti ad Altro
        var passata = json(luca.post("/api/livelli/passaggi", "{\"iscrizioneId\":\"" + gina.get("iscrizioneId") + "\"}"));
        var bGina = elenco(passata.get("bambini")).stream().filter(b -> b.get("nome").equals("Gina")).findFirst().orElseThrow();
        assertThat(mappa(bGina.get("passaggio"))).containsEntry("posizione", 1);
        assertThat(luca.post("/api/livelli/passaggi", "{\"iscrizioneId\":\"" + gina.get("iscrizioneId") + "\"}").statusCode()).isEqualTo(409);
        List<Map<String, Object>> attesa = elenco(lista(segr.get("/api/lezioni/" + tVerde + "/attesa")));
        assertThat(attesa).extracting(v -> v.get("nome") + ":" + v.get("posizione") + ":" + v.get("passaggio")).containsExactly("Gina:1:true", "Altro:2:false");

        // Marco passa e poi ci si ripensa: esce dalla lista del Verde
        var conMarco = json(luca.post("/api/livelli/passaggi", "{\"iscrizioneId\":\"" + marco.get("iscrizioneId") + "\"}"));
        String pMarco = (String) mappa(elenco(conMarco.get("bambini")).stream().filter(b -> b.get("nome").equals("Marco")).findFirst().orElseThrow().get("passaggio")).get("id");
        assertThat(segr.get("/api/lezioni/" + tVerde + "/attesa").body()).contains("Marco");
        var annullato = json(luca.post("/api/livelli/passaggi/" + pMarco + "/annulla", "{}"));
        assertThat(elenco(annullato.get("bambini")).stream().filter(b -> b.get("nome").equals("Marco")).findFirst().orElseThrow().get("passaggio")).isNull();
        assertThat(segr.get("/api/lezioni/" + tVerde + "/attesa").body()).doesNotContain("Marco");

        // la segreteria iscrive Gina nel Verde dalla lista: il passaggio è fatto e lascia il Giallo il giorno prima
        String voceGina = (String) attesa.getFirst().get("id");
        var entrata = segr.post("/api/attesa/" + voceGina + "/iscrivi", "{\"forza\":true}");
        assertThat(entrata.statusCode()).as(entrata.body()).isEqualTo(201);
        assertThat(segr.get("/api/lezioni/" + tGiallo + "/iscritti?data=" + oggi.minusDays(1)).body()).contains("\"al\":\"" + oggi.minusDays(1) + "\"");
        assertThat(elenco(lista(segr.get("/api/lezioni/" + tVerde + "/attesa")))).extracting(v -> v.get("nome") + ":" + v.get("posizione")).containsExactly("Altro:1");
        assertThat(segr.get("/api/attesa").body()).contains("\"tipo\":\"ISCRITTA\"");
    }
}
