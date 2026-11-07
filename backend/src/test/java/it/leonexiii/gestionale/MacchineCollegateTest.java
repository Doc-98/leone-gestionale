package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Phon e docce con le macchine collegate al server (ADR 0018, parte 9): chi configura servizi e macchine, la chiave
 * della macchina (vista una volta, rifatta quando serve), i crediti che finiscono, la macchina spenta, il servizio
 * gratis e il lettore del saldo. Il caso di tutti i giorni (ricarica, doccia gratis, phon a pagamento) è in
 * {@link BorsellinoTest}.
 */
class MacchineCollegateTest extends ProvaApi {

    private static String nuovoCodice() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private HttpResponse<String> usa(String macchina, String chiave, String codice) throws Exception {
        return anonimo().macchina("POST", "/api/macchine/" + macchina + "/utilizzi", chiave, "{\"codice\":\"" + codice + "\",\"postazione\":\"2\"}");
    }

    /** Una persona con un braccialetto e 25 crediti (5 € di ricarica). */
    private String conCrediti(ClientApi segr, String codice) throws Exception {
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Macchina\",\"cognome\":\"Collegata\",\"dataNascita\":\"1992-04-04\"}");
        assertThat(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}").statusCode()).isEqualTo(201);
        String contanti = metodoPagamento(segr, "Contanti");
        creaId(segr, "/api/borsellino/persone/" + persona + "/ricariche", "{\"importo\":5,\"metodi\":[{\"metodoId\":\"" + contanti + "\",\"importo\":5}]}");
        return persona;
    }

    @Test
    void serviziEMacchineSiConfiguranoSoloDalleImpostazioni() throws Exception {
        var admin = come("admin@demo.leone");
        var staff = come("staff@demo.leone");

        assertThat(staff.get("/api/dispositivi").statusCode()).isEqualTo(403);
        assertThat(staff.post("/api/servizi", "{\"nome\":\"Sauna\",\"crediti\":3}").statusCode()).isEqualTo(403);
        // i dati del servizio devono avere senso
        assertThat(admin.post("/api/servizi", "{\"nome\":\" \",\"crediti\":3}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/servizi", "{\"nome\":\"Sauna\",\"crediti\":-1}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/servizi", "{\"nome\":\"Sauna\",\"crediti\":3,\"gratuitiAlGiorno\":-1}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/servizi", "{\"nome\":\"Sauna\",\"crediti\":3,\"minuti\":0}").statusCode()).isEqualTo(400);
        var sauna = crea(admin, "/api/servizi", "{\"nome\":\" Sauna \",\"crediti\":3,\"minuti\":15}");
        assertThat(sauna).containsEntry("nome", "Sauna").containsEntry("gratuitiAlGiorno", 0).containsEntry("attivo", true);
        String servizio = (String) sauna.get("id");

        // una macchina di un servizio vuole il servizio; il tipo è SERVIZIO o SALDO
        assertThat(admin.post("/api/dispositivi", "{\"nome\":\"Sauna 1\",\"tipo\":\"SERVIZIO\"}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/dispositivi", "{\"nome\":\"Sauna 1\",\"tipo\":\"FRIGO\",\"servizioId\":\"" + servizio + "\"}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/dispositivi", "{\"nome\":\"Sauna 1\",\"tipo\":\"SERVIZIO\",\"servizioId\":\"" + UUID.randomUUID() + "\"}").statusCode()).isEqualTo(404);
        var creata = crea(admin, "/api/dispositivi", "{\"nome\":\"Sauna 1\",\"tipo\":\"SERVIZIO\",\"servizioId\":\"" + servizio + "\"}");
        String chiave = (String) creata.get("chiave");
        assertThat(chiave).hasSizeGreaterThan(20);
        assertThat(mappa(creata.get("dispositivo"))).containsEntry("servizio", "Sauna").containsEntry("utilizziOggi", 0);
        // la chiave si vede solo alla creazione: l'elenco non la mostra
        assertThat(admin.get("/api/dispositivi").body()).contains("Sauna 1").doesNotContain(chiave);
        // un lettore del saldo non ha servizio, anche se gliene si passa uno
        var lettore = crea(admin, "/api/dispositivi", "{\"nome\":\"Saldo ingresso\",\"tipo\":\"SALDO\",\"servizioId\":\"" + servizio + "\"}");
        assertThat(mappa(lettore.get("dispositivo"))).containsEntry("tipo", "SALDO").containsEntry("servizioId", null);
    }

    @Test
    void chiaveCreditiFinitiMacchinaSpentaEServizioGratis() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String servizio = creaId(admin, "/api/servizi", "{\"nome\":\"Idromassaggio\",\"crediti\":10,\"minuti\":10}");
        var creata = crea(admin, "/api/dispositivi", "{\"nome\":\"Vasca relax\",\"tipo\":\"SERVIZIO\",\"servizioId\":\"" + servizio + "\"}");
        String macchina = (String) mappa(creata.get("dispositivo")).get("id");
        String chiave = (String) creata.get("chiave");
        String codice = nuovoCodice();
        String persona = conCrediti(segr, codice);

        // senza codice la macchina riceve 400; con la chiave scala 10 crediti alla volta finché bastano
        assertThat(usa(macchina, chiave, " ").statusCode()).isEqualTo(400);
        assertThat(json(usa(macchina, chiave, codice))).containsEntry("via", true).containsEntry("esito", "OK").containsEntry("crediti", 15)
            .containsEntry("creditiUsati", 10).containsEntry("minuti", 10);
        assertThat(json(usa(macchina, chiave, codice))).containsEntry("crediti", 5);
        var finiti = json(usa(macchina, chiave, codice));
        assertThat(finiti).containsEntry("via", false).containsEntry("esito", "CREDITI_FINITI").containsEntry("crediti", 5).containsEntry("minuti", null);
        assertThat((String) finiti.get("messaggio")).contains("ricarica");
        // l'elenco conta i passaggi di oggi (anche quelli rifiutati) e sa quando la macchina si è fatta sentire
        var elenco = lista(admin.get("/api/dispositivi")).stream().map(ProvaApi::mappa).filter(d -> macchina.equals(d.get("id"))).findFirst().orElseThrow();
        assertThat(elenco).containsEntry("utilizziOggi", 3);
        assertThat(elenco.get("ultimoContatto")).isNotNull();
        assertThat(lista(segr.get("/api/utilizzi?dispositivoId=" + macchina))).hasSize(3);
        assertThat(segr.get("/api/utilizzi?personaId=" + persona).body()).contains("\"esito\":\"CREDITI_FINITI\"").contains("Macchina Collegata");

        // una chiave nuova manda in pensione la vecchia
        String nuova = (String) json(admin.post("/api/dispositivi/" + macchina + "/chiave", "{}")).get("chiave");
        assertThat(nuova).isNotEqualTo(chiave);
        assertThat(usa(macchina, chiave, codice).statusCode()).isEqualTo(401);
        assertThat(usa(macchina, nuova, codice).statusCode()).isEqualTo(200);

        // macchina spenta: non parte e non scala niente
        assertThat(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Vasca relax\",\"attivo\":false}").statusCode()).isEqualTo(200);
        assertThat(json(usa(macchina, nuova, codice))).containsEntry("via", false).containsEntry("esito", "SERVIZIO_SPENTO");
        assertThat(admin.put("/api/dispositivi/" + macchina, "{\"nome\":\"Vasca relax\",\"attivo\":true}").statusCode()).isEqualTo(200);

        // a zero crediti il servizio è gratis per tutti, anche senza crediti in tasca
        assertThat(admin.put("/api/servizi/" + servizio, "{\"nome\":\"Idromassaggio\",\"crediti\":0}").statusCode()).isEqualTo(200);
        assertThat(json(usa(macchina, nuova, codice))).containsEntry("via", true).containsEntry("esito", "GRATIS").containsEntry("messaggio", "Idromassaggio gratis");
        // un servizio spento vale come una macchina spenta
        assertThat(admin.put("/api/servizi/" + servizio, "{\"nome\":\"Idromassaggio\",\"crediti\":0,\"attivo\":false}").statusCode()).isEqualTo(200);
        assertThat(json(usa(macchina, nuova, codice))).containsEntry("esito", "SERVIZIO_SPENTO");
    }

    @Test
    void ilLettoreDelSaldoMostraCreditiEdEuro() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var creato = crea(admin, "/api/dispositivi", "{\"nome\":\"Saldo spogliatoio\",\"tipo\":\"SALDO\"}");
        String lettore = (String) mappa(creato.get("dispositivo")).get("id");
        String chiave = (String) creato.get("chiave");
        String codice = nuovoCodice();
        String persona = conCrediti(segr, codice);
        String saldo = "/api/macchine/" + lettore + "/saldo?codice=";

        assertThat(anonimo().macchina("GET", saldo + codice, null, null).statusCode()).isEqualTo(401);
        assertThat(json(anonimo().macchina("GET", saldo + codice, chiave, null))).containsEntry("esito", "SALDO").containsEntry("messaggio", "Crediti: 25");
        assertThat(coord.post("/api/borsellino/persone/" + persona + "/correzioni", "{\"saldo\":\"EURO\",\"quantita\":7.5,\"motivo\":\"Prova del lettore\"}").statusCode()).isEqualTo(204);
        assertThat(json(anonimo().macchina("GET", saldo + codice, chiave, null))).containsEntry("messaggio", "Crediti: 25 · credito 7.50 €");
        assertThat(json(anonimo().macchina("GET", saldo + nuovoCodice(), chiave, null))).containsEntry("esito", "SCONOSCIUTO");
        // passato su una macchina «da servizio», un lettore del saldo mostra soltanto il saldo
        assertThat(json(usa(lettore, chiave, codice))).containsEntry("via", false).containsEntry("esito", "SALDO").containsEntry("crediti", 25);

        // il simulatore vuole il codice e un dispositivo che esiste
        assertThat(segr.post("/api/dispositivi/" + lettore + "/prova", "{\"codice\":\" \"}").statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/dispositivi/" + UUID.randomUUID() + "/prova", "{\"codice\":\"" + codice + "\"}").statusCode()).isEqualTo(404);
        assertThat(json(segr.post("/api/dispositivi/" + lettore + "/prova", "{\"codice\":\"" + codice + "\",\"azione\":\"SALDO\"}"))).containsEntry("crediti", 25);
    }
}
