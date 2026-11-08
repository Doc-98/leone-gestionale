package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Cassa (ADR 0018, parte 11): apertura per turno con il fondo riportato, contanti attesi, spese, chiusura con la
 * differenza, un pagamento annullato dopo non cambia la chiusura fatta, riapertura; casse per postazione con il fondo
 * fisso e la chiusura a fine giornata, versamenti, fondo da contare a ogni apertura.
 */
class CassaTest extends ProvaApi {

    private String persona(ClientApi segr, String nome) throws Exception {
        return creaId(segr, "/api/clienti", "{\"nome\":\"" + nome + "\",\"cognome\":\"Cassieri\",\"dataNascita\":\"1985-02-03\"}");
    }

    /** Un addebito pagato subito con un metodo solo; restituisce l'id del pagamento. */
    private String paga(ClientApi segr, String persona, String metodo, double importo) throws Exception {
        String addebito = creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Ingresso\",\"importo\":" + importo
            + ",\"scadenza\":\"2030-12-31\"}");
        var r = crea(segr, "/api/pagamenti", "{\"paganteId\":\"" + persona + "\",\"addebiti\":[\"" + addebito + "\"],\"metodi\":[{\"metodoId\":\""
            + metodoPagamento(segr, metodo) + "\",\"importo\":" + importo + "}]}");
        return (String) r.get("id");
    }

    private static Map<String, Object> stato(ClientApi c, String cassaId) throws Exception {
        return elenco(json(c.get("/api/cassa")).get("stati")).stream().filter(s -> cassaId.equals(mappa(s.get("cassa")).get("id"))).findFirst().orElseThrow();
    }

    private static Map<String, Object> conti(Map<String, Object> stato) {
        return mappa(mappa(stato.get("corrente")).get("conti"));
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    /**
     * Chiude quello che c'è da chiudere (con i contati uguali agli attesi), così la prova parte da adesso: la sessione
     * conta dalla chiusura prima, e i dati demo e le altre prove hanno pagamenti in altre date.
     */
    private static void azzera(ClientApi c, String cassa) throws Exception {
        double attesi = num(conti(stato(c, cassa)).get("attesi"));
        var r = c.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":" + attesi + ",\"lasciato\":" + Math.min(100, attesi) + "}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    }

    @Test
    void unTurnoConIlFondoRiportato() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var staff = come("staff@demo.leone");
        orologio.alle("2030-05-06T08:00");
        var pagina = json(segr.get("/api/cassa"));
        assertThat(mappa(pagina.get("impostazioni"))).containsEntry("casse", "UNICA").containsEntry("fondo", "RIPORTATO").containsEntry("chiusura", "TURNO");
        String cassa = (String) elenco(pagina.get("casse")).stream().filter(c -> Boolean.TRUE.equals(c.get("principale"))).findFirst().orElseThrow().get("id");
        orologio.alle("2030-05-06T07:00");
        azzera(segr, cassa);
        orologio.alle("2030-05-06T08:00");
        double fondo = num(stato(segr, cassa).get("fondoProposto"));
        assertThat(staff.get("/api/cassa").statusCode()).isEqualTo(403);
        assertThat(staff.post("/api/cassa/casse/" + cassa + "/apri", "{}").statusCode()).isEqualTo(403);

        // apertura: il fondo è quello lasciato alla chiusura prima
        var aperta = segr.post("/api/cassa/casse/" + cassa + "/apri", "{}");
        assertThat(aperta.statusCode()).as(aperta.body()).isEqualTo(200);
        assertThat(json(aperta)).containsEntry("esplicita", true).containsEntry("apertaDa", "Anna Villa");
        assertThat(num(json(aperta).get("fondo"))).isEqualTo(fondo);
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/apri", "{}").statusCode()).isEqualTo(409);

        // due pagamenti (uno in contanti) e una spesa pagata con la cassa
        String anna = persona(segr, "Anna");
        orologio.alle("2030-05-06T09:00");
        String contanti = paga(segr, anna, "Contanti", 50);
        orologio.alle("2030-05-06T09:10");
        paga(segr, anna, "Bancomat", 30);
        orologio.alle("2030-05-06T10:00");
        var spesa = segr.post("/api/cassa/casse/" + cassa + "/movimenti", "{\"tipo\":\"SPESA\",\"importo\":5,\"descrizione\":\"Cancelleria\"}");
        assertThat(spesa.statusCode()).as(spesa.body()).isEqualTo(200);
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/movimenti", "{\"tipo\":\"REGALO\",\"importo\":5}").statusCode()).isEqualTo(400);

        var s = stato(segr, cassa);
        assertThat(s).containsEntry("aperta", true);
        var c = conti(s);
        assertThat(num(c.get("entrati"))).isEqualTo(50);
        assertThat(num(c.get("spese"))).isEqualTo(5);
        assertThat(num(c.get("attesi"))).isEqualTo(fondo + 45);
        assertThat(c).containsEntry("pagamenti", 2);
        assertThat(elenco(c.get("perMetodo"))).extracting(m -> m.get("metodo")).containsExactlyInAnyOrder("Contanti", "Bancomat");
        assertThat(elenco(mappa(s.get("corrente")).get("contanti"))).hasSize(1).first().satisfies(v -> assertThat(v).containsEntry("chi", "Anna Cassieri"));
        assertThat(elenco(mappa(s.get("corrente")).get("movimenti"))).extracting(m -> m.get("descrizione")).containsExactly("Cancelleria");

        // chiusura: se nel frattempo è cambiato qualcosa non si chiude; poi mancano 2 €
        orologio.alle("2030-05-06T12:00");
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":10,\"attesiVisti\":1}").statusCode()).isEqualTo(409);
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":10,\"lasciato\":11}").statusCode()).isEqualTo(400);
        var chiusa = segr.post("/api/cassa/casse/" + cassa + "/chiudi",
            "{\"contati\":" + (fondo + 43) + ",\"lasciato\":40,\"note\":\"Forse un resto sbagliato\",\"attesiVisti\":" + (fondo + 45) + "}");
        assertThat(chiusa.statusCode()).as(chiusa.body()).isEqualTo(200);
        var ch = json(chiusa);
        assertThat(num(ch.get("differenza"))).isEqualTo(-2);
        assertThat(num(ch.get("lasciato"))).isEqualTo(40);
        assertThat(num(ch.get("versato"))).isEqualTo(fondo + 3);
        assertThat(ch).containsEntry("chiusaDa", "Anna Villa").containsEntry("note", "Forse un resto sbagliato");
        String sessione = (String) ch.get("id");

        // a cassa chiusa: il pagamento va nella sessione dopo, che parte dal fondo lasciato
        orologio.alle("2030-05-06T13:00");
        paga(segr, anna, "Contanti", 20);
        s = stato(segr, cassa);
        assertThat(s).containsEntry("aperta", false);
        assertThat(num(s.get("fondoProposto"))).isEqualTo(40);
        assertThat(num(conti(s).get("entrati"))).isEqualTo(20);
        assertThat(mappa(s.get("ultimaChiusa"))).containsEntry("id", sessione);

        // annullare dopo un pagamento non cambia la chiusura fatta
        assertThat(coord.post("/api/pagamenti/" + contanti + "/annulla", "{\"motivo\":\"Prova\"}").statusCode()).isEqualTo(204);
        List<Map<String, Object>> storico = elenco(lista(segr.get("/api/cassa/sessioni?da=2030-05-06")));
        assertThat(storico).hasSize(2); // con quella delle 7 (azzera), la più recente per prima
        assertThat(storico.getFirst()).containsEntry("id", sessione);
        assertThat(num(mappa(storico.getFirst().get("conti")).get("entrati"))).isEqualTo(50);
        var pdf = segr.scarica("/api/cassa/sessioni/" + sessione + "/pdf");
        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(new String(pdf.body(), 0, 4)).isEqualTo("%PDF");

        // riaprire l'ultima chiusura: con il permesso «Annullare pagamenti» e il motivo
        assertThat(segr.post("/api/cassa/sessioni/" + sessione + "/riapri", "{\"motivo\":\"Contati male\"}").statusCode()).isEqualTo(403);
        assertThat(coord.post("/api/cassa/sessioni/" + sessione + "/riapri", "{}").statusCode()).isEqualTo(400);
        var riaperta = coord.post("/api/cassa/sessioni/" + sessione + "/riapri", "{\"motivo\":\"Contati male\"}");
        assertThat(riaperta.statusCode()).as(riaperta.body()).isEqualTo(200);
        assertThat((String) json(riaperta).get("note")).contains("Riaperta 06/05/2030 alle 13:00 da Paolo Galli: Contati male");
        assertThat(coord.post("/api/cassa/sessioni/" + sessione + "/riapri", "{\"motivo\":\"Ancora\"}").statusCode()).isEqualTo(409);
        // ora conta anche il pagamento delle 13 (e non più quello annullato)
        assertThat(num(conti(stato(segr, cassa)).get("entrati"))).isEqualTo(20);
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":" + (fondo + 15) + ",\"lasciato\":100}").statusCode()).isEqualTo(200);
        assertThat(num(json(segr.get("/api/cassa/sessioni/" + sessione)).get("differenza"))).isEqualTo(0);
    }

    @Test
    void unaCassaPerPostazioneConIlFondoFisso() throws Exception {
        var segr = come("segreteria@demo.leone");
        var staff = come("staff@demo.leone");
        var admin = come("admin@demo.leone");
        orologio.alle("2030-06-03T08:00");
        String imp = "{\"casse\":\"PER_POSTAZIONE\",\"fondo\":\"FISSO\",\"importoFondo\":150,\"chiusura\":\"GIORNATA\"}";
        assertThat(staff.put("/api/cassa/impostazioni", imp).statusCode()).isEqualTo(403);
        assertThat(admin.put("/api/cassa/impostazioni", imp.replace("150", "-1")).statusCode()).isEqualTo(400);
        assertThat(admin.put("/api/cassa/impostazioni", imp).statusCode()).isEqualTo(200);
        String principale = (String) elenco(json(admin.get("/api/cassa")).get("casse")).stream().filter(c -> Boolean.TRUE.equals(c.get("principale")))
            .findFirst().orElseThrow().get("id");
        azzera(segr, principale);
        var seconda = admin.post("/api/cassa/casse", "{\"nome\":\"Segreteria 2\"}");
        assertThat(seconda.statusCode()).as(seconda.body()).isEqualTo(201);
        String due = (String) json(seconda).get("id");
        assertThat(admin.post("/api/cassa/casse", "{\"nome\":\"segreteria 2\"}").statusCode()).isEqualTo(409);

        // ogni computer dice la sua cassa; senza, il pagamento va nella principale
        var segr2 = come("segreteria@demo.leone").dallaCassa(due);
        String bruno = persona(segr, "Bruno");
        orologio.alle("2030-06-03T09:00");
        paga(segr2, bruno, "Contanti", 70);
        orologio.alle("2030-06-03T09:05");
        paga(segr, bruno, "Contanti", 25);
        assertThat(elenco(json(segr.get("/api/cassa")).get("stati"))).hasSize(2);
        assertThat(num(conti(stato(segr, principale)).get("entrati"))).isEqualTo(25);
        assertThat(num(conti(stato(segr, principale)).get("fondo"))).isEqualTo(150);
        assertThat(num(conti(stato(segr, due)).get("entrati"))).isEqualTo(70);

        // a fine giornata, senza averla aperta: un versamento durante il giorno, poi la chiusura che torna
        orologio.alle("2030-06-03T15:00");
        var versamento = segr2.post("/api/cassa/casse/" + due + "/movimenti", "{\"tipo\":\"VERSAMENTO\",\"importo\":50}");
        assertThat(json(versamento)).containsEntry("esplicita", false);
        orologio.alle("2030-06-03T19:00");
        var ch = json(segr2.post("/api/cassa/casse/" + due + "/chiudi", "{\"contati\":170}"));
        assertThat(num(ch.get("attesi"))).isEqualTo(170);
        assertThat(num(ch.get("differenza"))).isEqualTo(0);
        assertThat(num(ch.get("lasciato"))).isEqualTo(150);
        assertThat(num(ch.get("versato"))).isEqualTo(20);

        // con una cassa aperta non si torna a una cassa sola, e non la si spegne
        assertThat(segr.post("/api/cassa/casse/" + principale + "/movimenti", "{\"tipo\":\"AGGIUNTA\",\"importo\":10,\"descrizione\":\"Monete\"}").statusCode())
            .isEqualTo(200);
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"FISSO\",\"importoFondo\":150,\"chiusura\":\"GIORNATA\"}").statusCode())
            .isEqualTo(409);
        assertThat(admin.put("/api/cassa/casse/" + principale, "{\"attiva\":false}").statusCode()).isEqualTo(409);
        assertThat(num(json(segr.post("/api/cassa/casse/" + principale + "/chiudi", "{\"contati\":185}")).get("differenza"))).isEqualTo(0);
        // la principale non si spegne nemmeno chiusa (prima se ne sceglie un'altra)
        assertThat(admin.put("/api/cassa/casse/" + principale, "{\"attiva\":false}").statusCode()).isEqualTo(400);

        // fondo da contare: prima si apre contando, e si vede se torna con quello lasciato
        assertThat(admin.put("/api/cassa/impostazioni", imp.replace("FISSO", "CONTATO")).statusCode()).isEqualTo(200);
        orologio.alle("2030-06-04T08:00");
        assertThat(segr2.post("/api/cassa/casse/" + due + "/chiudi", "{\"contati\":150}").statusCode()).isEqualTo(409);
        assertThat(segr2.post("/api/cassa/casse/" + due + "/apri", "{}").statusCode()).isEqualTo(400);
        var aperta = json(segr2.post("/api/cassa/casse/" + due + "/apri", "{\"fondo\":148}"));
        assertThat(num(aperta.get("fondo"))).isEqualTo(148);
        assertThat(num(aperta.get("fondoAtteso"))).isEqualTo(150);
        assertThat(segr2.post("/api/cassa/casse/" + due + "/chiudi", "{\"contati\":148,\"lasciato\":148}").statusCode()).isEqualTo(200);

        // si torna come prima (una cassa sola), e la seconda si spegne
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\"}").statusCode())
            .isEqualTo(200);
        assertThat(admin.put("/api/cassa/casse/" + due, "{\"attiva\":false}").statusCode()).isEqualTo(200);
        assertThat(elenco(json(segr.get("/api/cassa")).get("stati"))).hasSize(1);
    }
}
