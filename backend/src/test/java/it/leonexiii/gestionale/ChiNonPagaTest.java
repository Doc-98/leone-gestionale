package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Chi non paga (ADR 0018, parte 6): un pagamento scaduto fa avvisare il tornello dopo N giorni e dare rosso dopo M,
 * solo sopra la soglia e solo con la regola accesa; compare nelle scadenze e, se il promemoria è acceso, arriva
 * un'email ogni N giorni, al massimo M volte. Pagato il dovuto, tutto torna a posto.
 */
class ChiNonPagaTest extends ProvaApi {

    @Autowired
    AmbienteDiProva.CasellaFinta casella;

    private static String nuovoCodice() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> passa(ClientApi c, String quando, String codice) throws Exception {
        orologio.alle(quando);
        var r = c.post("/api/accessi/passaggi", "{\"codice\":\"" + codice + "\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return json(r);
    }

    private static List<Map<String, Object>> motivi(Map<String, Object> esito) {
        return elenco(esito.get("motivi"));
    }

    private static String regola(String giorniRosso, int soglia) {
        return "{\"attiva\":true,\"parametri\":{\"giorniAvviso\":7,\"giorniRosso\":" + giorniRosso + ",\"sogliaEuro\":" + soglia + "}}";
    }

    /** Salda tutto quello che la persona deve adesso (tesseramento, carnet…), così resta solo l'addebito della prova. */
    private void saldaTutto(ClientApi segr, String persona) throws Exception {
        var aperti = elenco(json(segr.get("/api/pagamenti/persone/" + persona)).get("addebiti")).stream()
            .filter(a -> new BigDecimal(a.get("residuo").toString()).signum() > 0).toList();
        if (aperti.isEmpty()) return;
        BigDecimal totale = aperti.stream().map(a -> new BigDecimal(a.get("residuo").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        String ids = String.join("\",\"", aperti.stream().map(a -> (String) a.get("id")).toList());
        assertThat(segr.post("/api/pagamenti", "{\"addebiti\":[\"" + ids + "\"],\"metodi\":[{\"metodoId\":\"" + metodoPagamento(segr, "Contanti")
            + "\",\"importo\":" + totale + "}]}").statusCode()).isEqualTo(201);
    }

    @Test
    void ilTornelloAvvisaPoiDaRossoEIPromemoriaSiRipetono() throws Exception {
        var segr = come("segreteria@demo.leone");
        var staff = come("staff@demo.leone");
        var admin = come("admin@demo.leone");
        orologio.alle("2028-03-01T10:00");
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Pino\",\"cognome\":\"Ritardo\",\"dataNascita\":\"1990-05-05\"}");
        String email = "pino.ritardo@esempio.it";
        assertThat(segr.put("/api/clienti/" + persona + "/contatti", "{\"email\":\"" + email + "\"}").statusCode()).isEqualTo(200);
        creaId(segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-12-31\"}");
        creaId(segr, "/api/clienti/" + persona + "/tesseramenti", "{}");
        creaId(segr, "/api/clienti/" + persona + "/abbonamenti", "{\"tipo\":\"CARNET\",\"ingressi\":30,\"dal\":\"2028-01-01\"}");
        String codice = nuovoCodice();
        creaId(segr, "/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}");
        saldaTutto(segr, persona);
        creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Quota piscina\",\"importo\":60,\"scadenza\":\"2028-03-05\"}");

        String promemoria = (String) elenco(json(segr.get("/api/promemoria")).get("regole")).stream()
            .filter(r -> "PAGAMENTO".equals(r.get("tipo"))).findFirst().orElseThrow().get("id");
        try {
            // regola spenta (com'è di base): nessun segno, anche a pagamento scaduto da tanto
            assertThat(motivi(passa(staff, "2028-04-20T10:00", codice))).isEmpty();

            assertThat(admin.put("/api/accessi/regole/PAGAMENTI", regola("30", 0)).statusCode()).isEqualTo(204);
            // prima dei 7 giorni niente; dopo, verde con l'avviso per il tornellista (e quando arriverà il rosso)
            assertThat(motivi(passa(staff, "2028-03-10T10:00", codice))).isEmpty();
            var avviso = passa(staff, "2028-03-13T10:00", codice);
            assertThat(avviso.get("verde")).isEqualTo(true);
            assertThat(motivi(avviso)).singleElement().satisfies(m -> {
                assertThat(m).containsEntry("codice", "PAGAMENTO_IN_RITARDO").containsEntry("blocca", false);
                assertThat((String) m.get("messaggio")).isEqualTo("Da pagare 60,00 €, scaduto dal 05/03/2028.");
                assertThat((String) m.get("rimedio")).contains("dal 04/04/2028 il tornello darà rosso");
            });
            // dal trentesimo giorno: rosso
            var rosso = passa(staff, "2028-04-05T10:00", codice);
            assertThat(rosso.get("verde")).isEqualTo(false);
            assertThat(motivi(rosso)).extracting(m -> m.get("codice")).containsExactly("PAGAMENTO_SCADUTO");
            // sotto la soglia non conta; senza i giorni del rosso, solo l'avviso
            assertThat(admin.put("/api/accessi/regole/PAGAMENTI", regola("30", 60)).statusCode()).isEqualTo(204);
            assertThat(motivi(passa(staff, "2028-04-05T12:00", codice))).isEmpty();
            assertThat(admin.put("/api/accessi/regole/PAGAMENTI", regola("null", 0)).statusCode()).isEqualTo(204);
            var soloAvviso = passa(staff, "2028-04-05T14:00", codice);
            assertThat(soloAvviso.get("verde")).isEqualTo(true);
            assertThat(motivi(soloAvviso)).singleElement().satisfies(m -> assertThat((String) m.get("rimedio")).isEqualTo("Ricordare di passare in segreteria."));
            assertThat(admin.put("/api/accessi/regole/PAGAMENTI", "{\"attiva\":true,\"parametri\":{\"giorniAvviso\":7,\"giorniRosso\":-1,\"sogliaEuro\":0}}").statusCode())
                .isEqualTo(400);

            // nelle scadenze, una voce per persona con quanto resta da pagare
            orologio.alle("2028-03-12T09:00");
            var voce = elenco(json(segr.get("/api/scadenze")).get("pagamenti")).stream().filter(v -> persona.equals(v.get("personaId"))).findFirst().orElseThrow();
            assertThat(voce).containsEntry("descrizione", "Da pagare 60,00 €").containsEntry("scadenza", "2028-03-05").containsEntry("giorni", -7)
                .containsEntry("importo", 60.00).containsEntry("email", email);

            // il promemoria, acceso: il primo a 7 giorni dalla scadenza, poi ogni 7, al massimo 2 volte
            assertThat(segr.put("/api/promemoria/" + promemoria, "{\"giorni\":7,\"maxInvii\":2,\"attivo\":true,\"oggetto\":\"Un pagamento da sistemare per {nome}\","
                + "\"testo\":\"Da pagare {importo} per {nome}, scaduto il {scadenza}.\"}").statusCode()).isEqualTo(200);
            orologio.alle("2028-03-11T09:00");
            segr.post("/api/promemoria/esegui", "{}");
            assertThat(casella.per(email)).isEmpty();
            orologio.alle("2028-03-12T09:00");
            segr.post("/api/promemoria/esegui", "{}");
            segr.post("/api/promemoria/esegui", "{}"); // lo stesso giorno non riparte
            assertThat(casella.per(email)).singleElement().satisfies(m -> {
                assertThat(m.getSubject()).isEqualTo("Un pagamento da sistemare per Pino");
                assertThat(m.getText()).isEqualTo("Da pagare 60,00 € per Pino, scaduto il 05/03/2028.");
            });
            orologio.alle("2028-03-15T09:00");
            segr.post("/api/promemoria/esegui", "{}");
            assertThat(casella.per(email)).hasSize(1);
            orologio.alle("2028-03-19T09:00");
            segr.post("/api/promemoria/esegui", "{}");
            assertThat(casella.per(email)).hasSize(2);
            orologio.alle("2028-03-26T09:00");
            segr.post("/api/promemoria/esegui", "{}");
            assertThat(casella.per(email)).hasSize(2);

            // pagato: niente più avvisi, né voce nelle scadenze
            assertThat(admin.put("/api/accessi/regole/PAGAMENTI", regola("30", 0)).statusCode()).isEqualTo(204);
            saldaTutto(segr, persona);
            assertThat(motivi(passa(staff, "2028-04-10T10:00", codice))).isEmpty();
            assertThat(segr.get("/api/scadenze").body()).doesNotContain("\"personaId\":\"" + persona + "\",\"nome\":\"Pino\"");
        } finally {
            assertThat(admin.put("/api/accessi/regole/PAGAMENTI", "{\"attiva\":false}").statusCode()).isEqualTo(204);
            segr.put("/api/promemoria/" + promemoria, "{\"giorni\":7,\"maxInvii\":3,\"attivo\":false,\"oggetto\":\"Un pagamento da sistemare per {nome}\","
                + "\"testo\":\"Risulta ancora da pagare {importo} per {nome} {cognome}, scaduto il {scadenza}.\"}");
        }
    }
}
