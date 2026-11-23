package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * «Macchine e crediti» nelle impostazioni (ADR 0030): servizi nell'ordine dell'elenco, servizi e macchine che si eliminano
 * sempre (gli utilizzi tengono il nome di allora), macchine senza servizio, lettore del saldo spento.
 */
class MacchineECreditiTest extends ProvaApi {

    private static String nuovoCodice() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private HttpResponse<String> usa(String macchina, String chiave, String codice) throws Exception {
        return anonimo().macchina("POST", "/api/macchine/" + macchina + "/utilizzi", chiave, "{\"codice\":\"" + codice + "\",\"postazione\":\"2\"}");
    }

    /** Una persona con un braccialetto e 25 crediti (5 € di ricarica). */
    private String conCrediti(ClientApi segr, String codice) throws Exception {
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Macchina\",\"cognome\":\"Eliminata\",\"dataNascita\":\"1991-05-05\"}");
        assertThat(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}").statusCode()).isEqualTo(201);
        String contanti = metodoPagamento(segr, "Contanti");
        creaId(segr, "/api/borsellino/persone/" + persona + "/ricariche", "{\"importo\":5,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5}]}");
        return persona;
    }

    private static Map<String, Object> perId(List<Map<String, Object>> elenco, String id) {
        return elenco.stream().filter(m -> id.equals(m.get("id"))).findFirst().orElseThrow();
    }

    private static List<String> ids(HttpResponse<String> r) {
        return elenco(lista(r)).stream().map(m -> (String) m.get("id")).toList();
    }

    @Test
    void iServiziHannoUnOrdineChePuoCambiare() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        List<String> originale = ids(admin.get("/api/servizi"));
        try {
            // un servizio nuovo va in fondo (l'ordine del corpo si ignora) e, senza «attivo», è acceso
            var nuovo = crea(admin, "/api/servizi", "{\"nome\":\"Asciugamani\",\"crediti\":2,\"ordine\":0}");
            String id = (String) nuovo.get("id");
            assertThat(nuovo).containsEntry("attivo", true).containsEntry("eliminabile", true).containsEntry("utilizzi", 0).containsEntry("macchine", 0);
            assertThat(nuovo.get("motivoBlocco")).isNull();
            List<String> con = ids(admin.get("/api/servizi"));
            assertThat(con).hasSize(originale.size() + 1).endsWith(id);

            // l'ordine vuole tutti gli id, una volta sola; lo cambia solo chi ha IMPOSTAZIONI
            assertThat(segr.put("/api/servizi/ordine", "{\"ids\":[]}").statusCode()).isEqualTo(403);
            for (String corpo : List.of("{\"ids\":[]}", "{}", "{\"ids\":[\"" + id + "\"]}",
                "{\"ids\":[" + String.join(",", con.stream().map(x -> "\"" + x + "\"").toList()) + ",\"" + id + "\"]}",
                "{\"ids\":[" + String.join(",", con.stream().map(x -> "\"" + UUID.randomUUID() + "\"").toList()) + "]}")) {
                var r = admin.put("/api/servizi/ordine", corpo);
                assertThat(r.statusCode()).as(corpo).isEqualTo(400);
                assertThat(r.body()).contains("L'ordine deve contenere, una volta sola, tutti i servizi.");
            }
            var rovesciato = new java.util.ArrayList<>(con);
            java.util.Collections.reverse(rovesciato);
            var r = admin.put("/api/servizi/ordine", "{\"ids\":[" + String.join(",", rovesciato.stream().map(x -> "\"" + x + "\"").toList()) + "]}");
            assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
            assertThat(ids(r)).isEqualTo(rovesciato);
            assertThat(ids(admin.get("/api/servizi"))).isEqualTo(rovesciato);

            // modificare un servizio senza dire «ordine» e «attivo» li lascia com'erano
            assertThat(admin.put("/api/servizi/" + id, "{\"nome\":\"Asciugamani\",\"crediti\":3,\"attivo\":false}").statusCode()).isEqualTo(200);
            assertThat(admin.put("/api/servizi/" + id, "{\"nome\":\"Asciugamani\",\"crediti\":4}").statusCode()).isEqualTo(200);
            assertThat(ids(admin.get("/api/servizi"))).isEqualTo(rovesciato);
            var dopo = perId(elenco(lista(admin.get("/api/servizi"))), id);
            assertThat(dopo).containsEntry("crediti", 4).containsEntry("attivo", false).containsEntry("ordine", 0);

            // chi legge soltanto (CLIENTI o BORSELLINO) non riceve i campi dell'eliminazione
            var visto = perId(elenco(lista(segr.get("/api/servizi"))), id);
            assertThat(visto.get("eliminabile")).isNull();
            assertThat(visto.get("motivoBlocco")).isNull();
            assertThat(visto.get("utilizzi")).isNull();

            assertThat(admin.delete("/api/servizi/" + id).statusCode()).isEqualTo(200);
        } finally {
            admin.put("/api/servizi/ordine", "{\"ids\":[" + String.join(",", ids(admin.get("/api/servizi")).stream()
                .sorted(java.util.Comparator.comparingInt(x -> originale.contains(x) ? originale.indexOf(x) : 999)).map(x -> "\"" + x + "\"").toList()) + "]}");
        }
    }

    @Test
    void unServizioUsatoSiEliminaELeMacchineRestanoSenzaServizio() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String servizio = creaId(admin, "/api/servizi", "{\"nome\":\"Asciugacapelli\",\"crediti\":10,\"minuti\":5}");
        var creata = crea(admin, "/api/dispositivi", "{\"nome\":\"Asciugacapelli atrio\",\"tipo\":\"SERVIZIO\",\"servizioId\":\"" + servizio + "\"}");
        String macchina = (String) mappa(creata.get("dispositivo")).get("id");
        String chiave = (String) creata.get("chiave");
        String codice = nuovoCodice();
        String persona = conCrediti(segr, codice);
        assertThat(json(usa(macchina, chiave, codice))).containsEntry("esito", "OK").containsEntry("crediti", 15);
        assertThat(json(segr.post("/api/dispositivi/" + macchina + "/prova", "{\"codice\":\"" + codice + "\"}"))).containsEntry("crediti", 5); // simulato

        var visto = perId(elenco(lista(admin.get("/api/servizi"))), servizio);
        assertThat(visto).containsEntry("eliminabile", true).containsEntry("utilizzi", 2).containsEntry("macchine", 1);
        assertThat(visto.get("motivoBlocco")).isNull();

        // non c'è niente «in corso»: si elimina, anche se usato e con macchine
        assertThat(segr.delete("/api/servizi/" + servizio).statusCode()).isEqualTo(403);
        var eliminato = admin.delete("/api/servizi/" + servizio);
        assertThat(eliminato.statusCode()).isEqualTo(200);
        assertThat(json(eliminato)).containsEntry("utilizzi", 2).containsEntry("macchine", 1);
        assertThat(admin.delete("/api/servizi/" + servizio).statusCode()).isEqualTo(404);
        assertThat(admin.delete("/api/servizi/" + servizio).body()).contains("Servizio non trovato.");

        // la macchina resta, senza servizio
        var macchinaSenza = perId(elenco(lista(admin.get("/api/dispositivi"))), macchina);
        assertThat(macchinaSenza).containsEntry("tipo", "SERVIZIO").containsEntry("servizioId", null).containsEntry("servizio", null).containsEntry("utilizzi", 2);
        // anche chi chiede solo il saldo (display, GET /saldo, simulatore) non riceve né nome né crediti
        String saldoSenza = "/api/macchine/" + macchina + "/saldo?codice=" + codice;
        for (var r : List.of(json(anonimo().macchina("GET", saldoSenza, chiave, null)), json(segr.post("/api/dispositivi/" + macchina + "/prova", "{\"codice\":\"" + codice + "\",\"azione\":\"SALDO\"}")))) {
            assertThat(r).containsEntry("via", false).containsEntry("esito", "SERVIZIO_SPENTO").containsEntry("messaggio", "Macchina non configurata");
            assertThat(r.get("nome")).isNull();
            assertThat(r.get("crediti")).isNull();
            assertThat(r.get("euro")).isNull();
        }
        // si può spegnere e riaccendere (l'interruttore della riga) anche senza servizio
        assertThat(json(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Asciugacapelli atrio\",\"attivo\":false}"))).containsEntry("attivo", false).containsEntry("servizioId", null);
        assertThat(json(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Asciugacapelli atrio\",\"attivo\":true}"))).containsEntry("attivo", true);

        // passato il braccialetto: «Macchina non configurata», niente crediti scalati, utilizzo registrato
        var risposta = json(usa(macchina, chiave, codice));
        assertThat(risposta).containsEntry("via", false).containsEntry("esito", "SERVIZIO_SPENTO").containsEntry("messaggio", "Macchina non configurata")
            .containsEntry("creditiUsati", 0);
        assertThat(risposta.get("nome")).isNull();
        assertThat(risposta.get("crediti")).isNull();
        assertThat(mappa(json(segr.get("/api/borsellino/persone/" + persona)).get("saldo"))).containsEntry("crediti", 5);

        // lo storico tiene il nome del servizio eliminato (e quello della macchina)
        var utilizzi = elenco(lista(admin.get("/api/utilizzi?dispositivoId=" + macchina)));
        assertThat(utilizzi).hasSize(3);
        assertThat(utilizzi.get(0)).containsEntry("esito", "SERVIZIO_SPENTO").containsEntry("dispositivo", "Asciugacapelli atrio").containsEntry("servizio", null);
        assertThat(utilizzi.get(1)).containsEntry("esito", "OK").containsEntry("servizio", "Asciugacapelli").containsEntry("simulato", true);
        assertThat(utilizzi.get(2)).containsEntry("esito", "OK").containsEntry("servizio", "Asciugacapelli").containsEntry("simulato", false);

        // si può dare un servizio alla macchina (si sceglie nel foglio) e torna a funzionare
        String altro = creaId(admin, "/api/servizi", "{\"nome\":\"Asciugacapelli 2\",\"crediti\":5}");
        assertThat(json(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Asciugacapelli atrio\",\"servizioId\":\"" + altro + "\"}")))
            .containsEntry("servizioId", altro).containsEntry("servizio", "Asciugacapelli 2");
        assertThat(json(usa(macchina, chiave, codice))).containsEntry("esito", "OK").containsEntry("crediti", 0);
        assertThat(admin.delete("/api/dispositivi/" + macchina).statusCode()).isEqualTo(200);
        assertThat(admin.delete("/api/servizi/" + altro).statusCode()).isEqualTo(200);
    }

    @Test
    void unaMacchinaSiEliminaSempreEGliUtilizziTengonoIlNome() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String servizio = creaId(admin, "/api/servizi", "{\"nome\":\"Idromassaggio plus\",\"crediti\":5}");
        var creata = crea(admin, "/api/dispositivi", "{\"nome\":\"Vasca plus 1\",\"tipo\":\"SERVIZIO\",\"servizioId\":\"" + servizio + "\"}");
        var dispositivo = mappa(creata.get("dispositivo"));
        String macchina = (String) dispositivo.get("id");
        String chiave = (String) creata.get("chiave");
        assertThat(dispositivo).containsEntry("eliminabile", true).containsEntry("utilizzi", 0).containsEntry("utilizziOggi", 0);
        assertThat(dispositivo.get("motivoBlocco")).isNull();

        // senza utilizzi, un solo simulato, poi uno vero: si elimina lo stesso
        String codice = nuovoCodice();
        String persona = conCrediti(segr, codice);
        assertThat(json(segr.post("/api/dispositivi/" + macchina + "/prova", "{\"codice\":\"" + codice + "\"}"))).containsEntry("esito", "OK");
        assertThat(json(usa(macchina, chiave, codice))).containsEntry("esito", "OK");
        var elencoAdmin = elenco(lista(admin.get("/api/dispositivi")));
        assertThat(perId(elencoAdmin, macchina)).containsEntry("eliminabile", true).containsEntry("utilizzi", 2).containsEntry("utilizziOggi", 2);
        // il vero «utilizziOggi» c'è anche nella risposta di una modifica
        assertThat(json(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Vasca plus 1\"}"))).containsEntry("utilizziOggi", 2).containsEntry("utilizzi", 2);
        // chi ha solo BORSELLINO vede l'elenco ma non i campi dell'eliminazione
        var visto = perId(elenco(lista(segr.get("/api/dispositivi"))), macchina);
        assertThat(visto.get("eliminabile")).isNull();
        assertThat(visto.get("utilizzi")).isNull();

        // una macchina da servizio spenta non mostra nome né crediti nemmeno nel saldo
        assertThat(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Vasca plus 1\",\"attivo\":false}").statusCode()).isEqualTo(200);
        var spentaSaldo = json(anonimo().macchina("GET", "/api/macchine/" + macchina + "/saldo?codice=" + codice, chiave, null));
        assertThat(spentaSaldo).containsEntry("esito", "SERVIZIO_SPENTO").containsEntry("messaggio", "Servizio non disponibile");
        assertThat(spentaSaldo.get("nome")).isNull();
        assertThat(spentaSaldo.get("crediti")).isNull();
        assertThat(spentaSaldo.get("euro")).isNull();
        assertThat(json(segr.post("/api/dispositivi/" + macchina + "/prova", "{\"codice\":\"" + codice + "\",\"azione\":\"SALDO\"}")).get("nome")).isNull();
        assertThat(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Vasca plus 1\",\"attivo\":true}").statusCode()).isEqualTo(200);

        assertThat(segr.delete("/api/dispositivi/" + macchina).statusCode()).isEqualTo(403);
        var tolta = admin.delete("/api/dispositivi/" + macchina);
        assertThat(tolta.statusCode()).isEqualTo(200);
        assertThat(json(tolta)).containsEntry("utilizzi", 2).containsEntry("macchine", null);
        assertThat(admin.delete("/api/dispositivi/" + macchina).statusCode()).isEqualTo(404);
        assertThat(admin.delete("/api/dispositivi/" + macchina).body()).contains("Dispositivo non trovato.");
        assertThat(elenco(lista(admin.get("/api/dispositivi"))).stream().map(m -> m.get("id"))).doesNotContain(macchina);
        // la macchina eliminata non risponde più
        assertThat(usa(macchina, chiave, codice).statusCode()).isEqualTo(401);

        // lo storico resta, con il nome di allora, ovunque lo si legga (elenco generale e scheda della persona)
        for (String percorso : List.of("/api/utilizzi?personaId=" + persona, "/api/utilizzi?quanti=200")) {
            var mie = elenco(lista(admin.get(percorso))).stream().filter(u -> "Vasca plus 1".equals(u.get("dispositivo")) && persona.equals(u.get("personaId"))).toList();
            assertThat(mie).as(percorso).hasSize(2);
            assertThat(mie).allSatisfy(u -> assertThat(u).containsEntry("dispositivoId", null).containsEntry("servizio", "Idromassaggio plus").containsEntry("esito", "OK"));
        }
        // i movimenti del borsellino non cambiano
        assertThat(segr.get("/api/borsellino/persone/" + persona).body()).contains("Vasca plus 1");
        assertThat(admin.delete("/api/servizi/" + servizio).statusCode()).isEqualTo(200);
    }

    @Test
    void ilLettoreDelSaldoSiEliminaESpentoNonMostraNiente() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        // si crea spento (l'«attivo» del corpo vale); il tipo non si cambia dopo la creazione
        var creato = crea(admin, "/api/dispositivi", "{\"nome\":\"Saldo piscina\",\"tipo\":\"SALDO\",\"attivo\":false}");
        var lettore = mappa(creato.get("dispositivo"));
        assertThat(lettore).containsEntry("attivo", false).containsEntry("eliminabile", true);
        String id = (String) lettore.get("id");
        String chiave = (String) creato.get("chiave");
        var servizio = creaId(admin, "/api/servizi", "{\"nome\":\"Cuffie\",\"crediti\":1}");
        var cambio = admin.put("/api/dispositivi/" + id, "{\"nome\":\"Saldo piscina\",\"tipo\":\"SERVIZIO\",\"servizioId\":\"" + servizio + "\"}");
        assertThat(cambio.statusCode()).isEqualTo(400);
        assertThat(cambio.body()).contains("Il tipo di una macchina non si cambia: creane una nuova.");
        assertThat(admin.put("/api/dispositivi/" + id, "{\"nome\":\"Saldo piscina\",\"tipo\":\"SALDO\"}").statusCode()).isEqualTo(200);

        String codice = nuovoCodice();
        conCrediti(segr, codice);
        String saldo = "/api/macchine/" + id + "/saldo?codice=";
        // spento: «Lettore non disponibile», senza nome, crediti né euro
        for (var r : List.of(json(anonimo().macchina("GET", saldo + codice, chiave, null)), json(usa(id, chiave, codice)))) {
            assertThat(r).containsEntry("via", false).containsEntry("esito", "SERVIZIO_SPENTO").containsEntry("messaggio", "Lettore non disponibile");
            assertThat(r.get("nome")).isNull();
            assertThat(r.get("crediti")).isNull();
            assertThat(r.get("euro")).isNull();
        }
        // acceso, risponde come prima
        assertThat(admin.put("/api/dispositivi/" + id, "{\"nome\":\"Saldo piscina\",\"attivo\":true}").statusCode()).isEqualTo(200);
        assertThat(json(anonimo().macchina("GET", saldo + codice, chiave, null))).containsEntry("esito", "SALDO").containsEntry("messaggio", "Crediti: 25");

        // un lettore non scrive utilizzi: si elimina
        assertThat(perId(elenco(lista(admin.get("/api/dispositivi"))), id)).containsEntry("utilizzi", 0);
        assertThat(admin.delete("/api/dispositivi/" + id).statusCode()).isEqualTo(200);
        assertThat(admin.delete("/api/servizi/" + servizio).statusCode()).isEqualTo(200);
    }
}
