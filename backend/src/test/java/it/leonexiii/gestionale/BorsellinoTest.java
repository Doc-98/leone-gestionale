package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import it.leonexiii.gestionale.pagamenti.Borsellino;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Borsellino, cauzione del braccialetto e macchine di phon e docce (ADR 0018, parti 7–9). */
class BorsellinoTest extends ProvaApi {

    @Autowired
    Borsellino borsellino;

    static final String PHON_M = "00000000-0000-0000-0000-000000000a11";
    static final String DOCCE_M = "00000000-0000-0000-0000-000000000a14";
    static final String SALDO = "00000000-0000-0000-0000-000000000a16";

    private Map<String, Object> saldo(ClientApi c, String persona) throws Exception {
        return mappa(json(c.get("/api/borsellino/persone/" + persona)).get("saldo"));
    }

    /** Come una macchina vera: niente sessione, solo la chiave del dispositivo. */
    private HttpResponse<String> macchina(String dispositivo, String chiave, String codice) throws Exception {
        return anonimo().macchina("POST", "/api/macchine/" + dispositivo + "/utilizzi", chiave, "{\"codice\":\"" + codice + "\",\"postazione\":\"3\"}");
    }

    @Test
    void ricaricaMacchineCauzioneERiconsegna() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Phon\",\"cognome\":\"Prova\",\"dataNascita\":\"1990-01-01\"}");
        // il braccialetto: la cauzione (10 € nelle regole demo) diventa un addebito
        var b = json(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"7A7A7A01\"}"));
        String braccialetto = (String) b.get("id");
        var conto = json(segr.get("/api/pagamenti/persone/" + persona));
        @SuppressWarnings("unchecked")
        var addebiti = (List<Map<String, Object>>) conto.get("addebiti");
        var voceCauzione = addebiti.stream().filter(a -> "CAUZIONE".equals(a.get("origine"))).findFirst().orElseThrow();
        assertThat(voceCauzione).containsEntry("importo", 10.00);
        String contanti = metodoPagamento(segr, "Contanti");
        creaId(segr, "/api/pagamenti", "{\"addebiti\":[\"" + voceCauzione.get("id") + "\"],\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":10}]}");

        // ricarica: minimo 5 €, multipli di 0,20 €
        String ricarica = "/api/borsellino/persone/" + persona + "/ricariche";
        assertThat(segr.post(ricarica, "{\"importo\":3,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":3}]}").statusCode()).isEqualTo(400);
        assertThat(segr.post(ricarica, "{\"importo\":5.10,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5.10}]}").statusCode()).isEqualTo(400);
        assertThat(segr.post(ricarica, "{\"importo\":1.00,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":1}]}").statusCode()).isEqualTo(400);
        assertThat(segr.post(ricarica, "{\"importo\":5,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5}]}").statusCode()).isEqualTo(201);
        assertThat(saldo(segr, persona)).containsEntry("crediti", 25);

        // le macchine: senza chiave no; doccia: la prima del giorno gratis, poi un credito; phon: un credito
        assertThat(macchina(PHON_M, null, "7A7A7A01").statusCode()).isEqualTo(401);
        assertThat(macchina(PHON_M, "sbagliata", "7A7A7A01").statusCode()).isEqualTo(401);
        var d1 = json(macchina(DOCCE_M, "demo-docce-m", "7A:7A:7A:01"));
        assertThat(d1).containsEntry("via", true).containsEntry("esito", "GRATIS").containsEntry("crediti", 25).containsEntry("nome", "Phon");
        assertThat(json(macchina(DOCCE_M, "demo-docce-m", "7A7A7A01"))).containsEntry("esito", "OK").containsEntry("crediti", 24);
        assertThat(json(macchina(PHON_M, "demo-phon-m", "7A7A7A01"))).containsEntry("esito", "OK").containsEntry("crediti", 23).containsEntry("minuti", 5);
        assertThat(json(macchina(PHON_M, "demo-phon-m", "FFFFFF99"))).containsEntry("via", false).containsEntry("esito", "SCONOSCIUTO");
        // il lettore del saldo
        var s = anonimo().macchina("GET", "/api/macchine/" + SALDO + "/saldo?codice=7A7A7A01", "demo-saldo", null);
        assertThat(json(s)).containsEntry("crediti", 23);
        // il simulatore (utente collegato) fa come la macchina e segna l'utilizzo come simulato
        var prova = json(segr.post("/api/dispositivi/" + PHON_M + "/prova", "{\"codice\":\"7A7A7A01\",\"azione\":\"USA\"}"));
        assertThat(prova).containsEntry("crediti", 22);
        assertThat(segr.get("/api/utilizzi?personaId=" + persona).body()).contains("\"simulato\":true").contains("\"esito\":\"GRATIS\"");

        // pagare un addebito con il credito in euro: serve il credito
        String borsellino = metodoPagamento(segr, "Borsellino");
        String cuffia = creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Cuffia\",\"importo\":8}");
        var senza = segr.post("/api/pagamenti", "{\"addebiti\":[\"" + cuffia + "\"],\"metodi\":[{\"metodoId\":\"" + borsellino + "\",\"importo\":8}]}");
        assertThat(senza.statusCode()).isEqualTo(400);
        assertThat(senza.body()).contains("non bastano");
        assertThat(coord.post("/api/borsellino/persone/" + persona + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":20,\"motivo\":\"Rimborso a mano\"}").statusCode()).isEqualTo(204);
        assertThat(segr.post("/api/borsellino/persone/" + persona + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":20,\"motivo\":\"x\"}").statusCode()).isEqualTo(403);
        String pag = creaId(segr, "/api/pagamenti", "{\"addebiti\":[\"" + cuffia + "\"],\"metodi\":[{\"metodoId\":\"" + borsellino + "\",\"importo\":8}]}");
        assertThat(saldo(segr, persona)).containsEntry("euro", 12.00);
        assertThat(coord.post("/api/pagamenti/" + pag + "/annulla", "{\"motivo\":\"Prova\"}").statusCode()).isEqualTo(204);
        assertThat(saldo(segr, persona)).containsEntry("euro", 20.00);

        // restituire in denaro: le regole demo non lo permettono
        assertThat(coord.post("/api/borsellino/persone/" + persona + "/restituzioni", "{\"importo\":5}").statusCode()).isEqualTo(409);

        // riconsegna dell'ultimo braccialetto: cauzione in denaro, crediti nel borsellino (22 × 0,20 = 4,40 €)
        var esito = json(segr.post("/api/accessi/braccialetti/" + braccialetto + "/riconsegna", "{\"cauzione\":\"DENARO\",\"crediti\":\"BORSELLINO\"}"));
        assertThat(esito).containsEntry("cauzione", 10.00).containsEntry("crediti", 22).containsEntry("valoreCrediti", 4.40);
        assertThat(saldo(segr, persona)).containsEntry("crediti", 0).containsEntry("euro", 24.40);
        assertThat(json(segr.get("/api/pagamenti/incassi")).get("uscite").toString()).contains("Restituzione cauzione");
        assertThat(json(macchina(PHON_M, "demo-phon-m", "7A7A7A01"))).containsEntry("esito", "NON_ATTIVO");
        // il codice è di nuovo libero
        assertThat(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"7A7A7A01\",\"cauzione\":false}").statusCode()).isEqualTo(201);
    }

    @Test
    void trasferimentiTraFamiliariESmarrimento() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String mamma = creaId(segr, "/api/clienti", "{\"nome\":\"Mamma\",\"cognome\":\"Borsellino\"}");
        String figlio = creaId(segr, "/api/clienti", "{\"nome\":\"Figlio\",\"cognome\":\"Borsellino\",\"dataNascita\":\"2016-01-01\"}");
        String estraneo = creaId(segr, "/api/clienti", "{\"nome\":\"Altro\",\"cognome\":\"Borsellino\"}");
        creaId(segr, "/api/clienti/" + figlio + "/tutori", "{\"tutoreId\":\"" + mamma + "\",\"relazione\":\"MADRE\"}");
        coord.post("/api/borsellino/persone/" + mamma + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":30,\"motivo\":\"Rimborso\"}");
        assertThat(segr.post("/api/borsellino/persone/" + mamma + "/trasferimenti", "{\"aPersonaId\":\"" + estraneo + "\",\"importo\":10}").statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/borsellino/persone/" + mamma + "/trasferimenti", "{\"aPersonaId\":\"" + figlio + "\",\"importo\":50}").statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/borsellino/persone/" + mamma + "/trasferimenti", "{\"aPersonaId\":\"" + figlio + "\",\"importo\":10}").statusCode()).isEqualTo(204);
        assertThat(saldo(segr, mamma)).containsEntry("euro", 20.00);
        var delFiglio = saldo(segr, figlio);
        assertThat(delFiglio).containsEntry("euro", 10.00);
        assertThat(delFiglio.get("prossimaScadenza")).isNotNull();

        // smarrito con la regola «crediti persi»: i crediti dei servizi si perdono (se non ha altri braccialetti attivi)
        var b = json(segr.post("/api/accessi/persone/" + figlio + "/braccialetti", "{\"codice\":\"7B7B7B01\",\"cauzione\":false}"));
        coord.post("/api/borsellino/persone/" + figlio + "/correzioni", "{\"saldo\":\"CREDITI\",\"quantita\":15,\"motivo\":\"Prova\"}");
        var smarrito = json(segr.put("/api/accessi/braccialetti/" + b.get("id") + "/stato", "{\"stato\":\"SMARRITO\"}"));
        assertThat(smarrito).containsEntry("creditiPersi", 15);
        assertThat(saldo(segr, figlio)).containsEntry("crediti", 0);
        assertThat(segr.get("/api/borsellino/persone/" + figlio).body()).contains("SMARRIMENTO").contains("Ricevuto da Mamma Borsellino");
    }

    @Test
    void restituzioneCorrezioniECreditoScaduto() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        String regole = admin.get("/api/borsellino/regole").body();
        try {
            // regole diverse dalla demo: il credito si restituisce, non si trasferisce, gli euro valgono un mese
            String altre = regole.replaceAll("\"restituibile\":(true|false)", "\"restituibile\":true")
                .replaceAll("\"trasferibile\":(true|false)", "\"trasferibile\":false")
                .replaceAll("\"validitaEuroMesi\":(\\d+|null)", "\"validitaEuroMesi\":1");
            assertThat(admin.put("/api/borsellino/regole", altre).statusCode()).isEqualTo(200);
            orologio.alle("2028-01-10T10:00");
            String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Resto\",\"cognome\":\"Borsellino\"}");
            String sorella = creaId(segr, "/api/clienti", "{\"nome\":\"Sorella\",\"cognome\":\"Borsellino\"}");
            String correzioni = "/api/borsellino/persone/" + persona + "/correzioni";

            // una correzione dice il motivo, quale saldo e di quanto (crediti interi, euro al centesimo)
            assertThat(coord.post(correzioni, "{\"saldo\":\"EURO\",\"quantita\":10,\"motivo\":\" \"}").statusCode()).isEqualTo(400);
            assertThat(coord.post(correzioni, "{\"saldo\":\"DOLLARI\",\"quantita\":10,\"motivo\":\"Prova\"}").statusCode()).isEqualTo(400);
            assertThat(coord.post(correzioni, "{\"saldo\":\"EURO\",\"quantita\":0,\"motivo\":\"Prova\"}").statusCode()).isEqualTo(400);
            assertThat(coord.post(correzioni, "{\"saldo\":\"CREDITI\",\"quantita\":1.5,\"motivo\":\"Prova\"}").statusCode()).isEqualTo(400);
            assertThat(coord.post(correzioni, "{\"saldo\":\"EURO\",\"quantita\":1.234,\"motivo\":\"Prova\"}").statusCode()).isEqualTo(400);
            assertThat(coord.post(correzioni, "{\"saldo\":\"EURO\",\"quantita\":10,\"motivo\":\"Rimborso lezione\"}").statusCode()).isEqualTo(204);
            assertThat(saldo(segr, persona)).containsEntry("euro", 10.00).containsEntry("prossimaScadenza", "2028-02-10");

            // restituire in denaro: l'importo va scritto; esce dalla cassa con il primo metodo in denaro
            String restituzioni = "/api/borsellino/persone/" + persona + "/restituzioni";
            assertThat(coord.post(restituzioni, "{\"importo\":0}").statusCode()).isEqualTo(400);
            assertThat(coord.post(restituzioni, "{\"importo\":1.005}").statusCode()).isEqualTo(400);
            assertThat(coord.post(restituzioni, "{\"importo\":3}").statusCode()).isEqualTo(204);
            assertThat(saldo(segr, persona)).containsEntry("euro", 7.00);
            assertThat(json(segr.get("/api/pagamenti/incassi")).get("uscite").toString()).contains("Credito del borsellino restituito");
            // trasferire non si può con queste regole, nemmeno a un familiare
            assertThat(segr.post("/api/borsellino/persone/" + persona + "/trasferimenti", "{\"aPersonaId\":\"" + sorella + "\",\"importo\":2}").statusCode())
                .isEqualTo(409);

            // passato il mese, il lavoro della notte toglie gli euro scaduti
            orologio.alle("2028-02-11T01:15");
            assertThat(borsellino.scadenze()).isGreaterThanOrEqualTo(1);
            assertThat(saldo(segr, persona)).containsEntry("euro", 0.00);
            assertThat(segr.get("/api/borsellino/persone/" + persona).body()).contains("SCADENZA").contains("Credito scaduto");
            // una seconda volta non c'è più niente da togliere a questa persona
            borsellino.scadenze();
            assertThat(saldo(segr, persona)).containsEntry("euro", 0.00);
        } finally {
            assertThat(admin.put("/api/borsellino/regole", regole).statusCode()).isEqualTo(200);
        }
    }
}
