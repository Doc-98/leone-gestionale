package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Cassa (ADR 0018, parte 11): apertura per turno con il fondo riportato, contanti attesi, spese, chiusura con la
 * differenza, un pagamento annullato dopo non cambia la chiusura fatta, riapertura; casse per postazione con il fondo
 * fisso e la chiusura a fine giornata, versamenti, fondo da contare a ogni apertura; i controlli (conta alla cieca,
 * tolleranza, riscontro con il POS, controllo del responsabile, avviso, cassaforte e banca).
 * In ordine e con le date che crescono (maggio, giugno, luglio 2030): una sessione conta dalla chiusura prima.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
        // il confronto mostra gli attesi anche con la conta alla cieca
        var esito = c.post("/api/cassa/casse/" + cassa + "/confronta", "{\"contati\":0}");
        assertThat(esito.statusCode()).as(esito.body()).isEqualTo(200);
        double attesi = num(mappa(mappa(json(esito).get("sessione")).get("conti")).get("attesi"));
        var r = c.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":" + attesi + ",\"lasciato\":" + Math.min(100, attesi) + "}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    }

    @Test
    @Order(1)
    void unTurnoConIlFondoRiportato() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var staff = come("staff@demo.leone");
        orologio.alle("2030-05-06T08:00");
        // qui gli attesi si vedono sempre (la conta alla cieca ha la sua prova)
        assertThat(come("admin@demo.leone").put("/api/cassa/impostazioni",
            "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\",\"cieca\":false}").statusCode()).isEqualTo(200);
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
    @Order(2)
    void unaCassaPerPostazioneConIlFondoFisso() throws Exception {
        var segr = come("segreteria@demo.leone");
        var staff = come("staff@demo.leone");
        var admin = come("admin@demo.leone");
        orologio.alle("2030-06-03T08:00");
        String imp = "{\"casse\":\"PER_POSTAZIONE\",\"fondo\":\"FISSO\",\"importoFondo\":150,\"chiusura\":\"GIORNATA\",\"cieca\":false}";
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
        var principaleAperta = admin.put("/api/cassa/casse/" + principale, "{\"attiva\":false}");
        assertThat(principaleAperta.statusCode()).isEqualTo(400);
        assertThat(principaleAperta.body()).contains("La cassa principale non si spegne");
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

    @Test
    @Order(3)
    void iControlliDellaChiusura() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var admin = come("admin@demo.leone");
        orologio.alle("2030-07-01T07:00");
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"cieca\":true,\"tolleranza\":5,\"controllo\":\"OLTRE\",\"spese\":false,\"banca\":false,\"oreAvviso\":2}").statusCode()).isEqualTo(200);
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"controllo\":\"QUALCUNA\"}").statusCode()).isEqualTo(400);
        var pagina = json(segr.get("/api/cassa"));
        String cassa = (String) elenco(pagina.get("casse")).stream().filter(c -> Boolean.TRUE.equals(c.get("principale"))).findFirst().orElseThrow().get("id");
        assertThat(elenco(pagina.get("daRiscontrare"))).extracting(m -> m.get("nome")).containsExactly("Bancomat");
        azzera(segr, cassa);
        double fondo = num(stato(segr, cassa).get("fondoProposto"));
        // il conto della cassaforte parte da quando si accende (la chiusura delle 7 non c'entra)
        orologio.alle("2030-07-01T07:30");
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"banca\":true}").statusCode()).isEqualTo(200);

        // alla cieca: della cassa aperta si vedono il fondo e quanti pagamenti, non gli importi
        orologio.alle("2030-07-01T08:00");
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/apri", "{}").statusCode()).isEqualTo(200);
        String eva = persona(segr, "Eva");
        orologio.alle("2030-07-01T09:00");
        paga(segr, eva, "Contanti", 60);
        orologio.alle("2030-07-01T09:10");
        paga(segr, eva, "Bancomat", 40);
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/movimenti", "{\"tipo\":\"SPESA\",\"importo\":3}").statusCode()).isEqualTo(400);
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/movimenti", "{\"tipo\":\"VERSAMENTO\",\"importo\":20}").statusCode()).isEqualTo(200);
        var s = stato(segr, cassa);
        var corrente = mappa(s.get("corrente"));
        assertThat(corrente).containsEntry("nascosti", true).containsEntry("contanti", null);
        assertThat(conti(s)).containsEntry("attesi", null).containsEntry("entrati", null).containsEntry("quanteEntrate", 1).containsEntry("pagamenti", 2);
        assertThat(s).containsEntry("apertaDaTroppo", false);

        // dopo 2 ore aperta: l'avviso
        orologio.alle("2030-07-01T11:00");
        assertThat(stato(segr, cassa)).containsEntry("apertaDaTroppo", true);
        assertThat(elenco(json(segr.get("/api/cassa/avvisi")).get("aperteDaTroppo"))).hasSize(1);

        // il confronto: mancano 12 € (oltre i 5 tollerati), il POS torna; resta il primo conteggio
        var esito = json(segr.post("/api/cassa/casse/" + cassa + "/confronta", "{\"contati\":" + (fondo + 28)
            + ",\"riscontri\":[{\"metodoId\":\"" + metodoPagamento(segr, "Bancomat") + "\",\"importo\":40}]}"));
        assertThat(num(esito.get("differenza"))).isEqualTo(-12);
        assertThat(esito).containsEntry("oltre", true).containsEntry("notaObbligatoria", true).containsEntry("daControllare", true);
        assertThat(num(mappa(mappa(esito.get("sessione")).get("conti")).get("attesi"))).isEqualTo(fondo + 40);
        assertThat(mappa(esito.get("sessione"))).containsEntry("nascosti", false);
        assertThat(elenco(esito.get("riscontri"))).singleElement().satisfies(r -> assertThat(num(r.get("differenza"))).isZero());

        // riconta: 2 € in meno, dentro la tolleranza; ma con un POS che non torna serve la nota
        String bancomat = metodoPagamento(segr, "Bancomat");
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":" + (fondo + 38) + ",\"lasciato\":100,"
            + "\"riscontri\":[{\"metodoId\":\"" + bancomat + "\",\"importo\":35}]}").statusCode()).isEqualTo(400);
        var ch = json(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":" + (fondo + 38) + ",\"lasciato\":100,"
            + "\"riscontri\":[{\"metodoId\":\"" + bancomat + "\",\"importo\":40}]}"));
        assertThat(num(ch.get("primoConteggio"))).isEqualTo(fondo + 28);
        assertThat(num(ch.get("differenza"))).isEqualTo(-2);
        assertThat(ch).containsEntry("daControllare", false);
        assertThat(num(ch.get("perCassaforte"))).isEqualTo(fondo + 38 - 100 + 20);
        double primaInCassaforte = num(ch.get("perCassaforte"));

        // un'altra sessione che non torna: nota obbligatoria, poi da controllare
        orologio.alle("2030-07-01T15:00");
        paga(segr, eva, "Contanti", 50);
        orologio.alle("2030-07-01T19:00");
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":140,\"lasciato\":100}").statusCode()).isEqualTo(400);
        var seconda = json(segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":140,\"lasciato\":100,\"note\":\"Non trovo 10 euro\"}"));
        assertThat(seconda).containsEntry("daControllare", true);
        String id = (String) seconda.get("id");
        assertThat(json(segr.get("/api/cassa/avvisi")).get("daControllare")).isEqualTo(1);
        assertThat(segr.post("/api/cassa/sessioni/" + id + "/controlla", "{}").statusCode()).isEqualTo(403);
        var controllata = json(coord.post("/api/cassa/sessioni/" + id + "/controlla", "{\"nota\":\"Riconteggiato con Anna\"}"));
        assertThat(controllata).containsEntry("controllataDa", "Paolo Galli").containsEntry("notaControllo", "Riconteggiato con Anna");
        assertThat(coord.post("/api/cassa/sessioni/" + id + "/controlla", "{}").statusCode()).isEqualTo(409);
        assertThat(json(segr.get("/api/cassa/avvisi")).get("daControllare")).isEqualTo(0);

        // cassaforte e banca: le due chiusure, poi in banca; una chiusura in banca non si riapre
        var forte = json(segr.get("/api/cassa/cassaforte"));
        assertThat(num(forte.get("totale"))).isEqualTo(primaInCassaforte + 40);
        assertThat(elenco(forte.get("chiusure"))).hasSize(2);
        assertThat(segr.post("/api/cassa/versamenti", "{\"data\":\"2030-07-02\"}").statusCode()).isEqualTo(400);
        var versamento = segr.post("/api/cassa/versamenti", "{\"data\":\"2030-07-01\",\"distinta\":\"0042\"}");
        assertThat(versamento.statusCode()).as(versamento.body()).isEqualTo(201);
        assertThat(num(json(versamento).get("importo"))).isEqualTo(primaInCassaforte + 40);
        assertThat(num(json(segr.get("/api/cassa/cassaforte")).get("totale"))).isZero();
        assertThat(coord.post("/api/cassa/sessioni/" + id + "/riapri", "{\"motivo\":\"Prova\"}").statusCode()).isEqualTo(409);
        String vid = (String) json(versamento).get("id");
        assertThat(segr.post("/api/cassa/versamenti/" + vid + "/annulla", "{}").statusCode()).isEqualTo(403);
        assertThat(coord.post("/api/cassa/versamenti/" + vid + "/annulla", "{}").statusCode()).isEqualTo(200);
        assertThat(num(json(segr.get("/api/cassa/cassaforte")).get("totale"))).isEqualTo(primaInCassaforte + 40);
        var pdf = segr.scarica("/api/cassa/sessioni/" + id + "/pdf");
        assertThat(new String(pdf.body(), 0, 4)).isEqualTo("%PDF");

        // si torna alle scelte di base
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"cieca\":true,\"tolleranza\":5,\"controllo\":\"OLTRE\",\"spese\":true,\"banca\":false,\"oreAvviso\":14}").statusCode()).isEqualTo(200);
        assertThat(segr.get("/api/cassa/cassaforte").statusCode()).isEqualTo(409);
    }

    @Test
    @Order(4)
    void unMetodoEliminatoLasciaLoStorico() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var admin = come("admin@demo.leone");
        orologio.alle("2030-09-02T07:00");
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"cieca\":false,\"tolleranza\":5,\"controllo\":\"OLTRE\",\"spese\":true,\"banca\":false,\"oreAvviso\":14}").statusCode()).isEqualTo(200);
        String cassa = (String) elenco(json(segr.get("/api/cassa")).get("casse")).stream().filter(c -> Boolean.TRUE.equals(c.get("principale"))).findFirst().orElseThrow().get("id");
        azzera(segr, cassa);

        // un metodo da riscontrare con il POS e uno da incassare a mano
        String suffisso = java.util.UUID.randomUUID().toString().substring(0, 6);
        String pos = "App pagamenti " + suffisso, banca = "Bonifico veloce " + suffisso;
        String posId = creaId(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + pos + "\",\"riscontro\":true}");
        String bancaId = creaId(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + banca + "\",\"daIncassare\":true}");
        orologio.alle("2030-09-02T08:00");
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/apri", "{}").statusCode()).isEqualTo(200);
        String anna = persona(segr, "Elisa");
        orologio.alle("2030-09-02T09:00");
        String pagPos = paga(segr, anna, pos, 30);
        orologio.alle("2030-09-02T09:10");
        paga(segr, anna, banca, 20);

        // un metodo rinominato a cassa aperta resta una riga sola nei totali della cassa, con il nome attuale
        String terzo = "Buono famiglia " + suffisso, terzoNuovo = "Buono famiglia due " + suffisso;
        String terzoId = creaId(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + terzo + "\"}");
        orologio.alle("2030-09-02T09:20");
        paga(segr, anna, terzo, 5);
        assertThat(admin.put("/api/pagamenti/metodi/" + terzoId, "{\"nome\":\"" + terzoNuovo + "\"}").statusCode()).isEqualTo(200);
        orologio.alle("2030-09-02T09:25");
        paga(segr, anna, terzoNuovo, 5);
        var righeBuono = elenco(conti(stato(segr, cassa)).get("perMetodo")).stream().filter(m -> ((String) m.get("metodo")).startsWith("Buono famiglia")).toList();
        assertThat(righeBuono).hasSize(1);
        assertThat(righeBuono.getFirst()).containsEntry("metodo", terzoNuovo).containsEntry("metodoId", terzoId);
        assertThat(num(righeBuono.getFirst().get("entrate"))).isEqualTo(10);

        // con la cassa aperta, e con un bonifico da incassare, non si elimina (e il messaggio dice cosa fare)
        var inCassa = admin.delete("/api/pagamenti/metodi/" + posId);
        assertThat(inCassa.statusCode()).isEqualTo(409);
        assertThat((String) json(inCassa).get("detail")).startsWith("Non si può eliminare: «" + pos + "» è in una cassa non ancora chiusa («Segreteria»).\n").endsWith("Chiudi la cassa: poi potrai eliminarlo.");
        var daIncassare = admin.delete("/api/pagamenti/metodi/" + bancaId);
        assertThat(daIncassare.statusCode()).isEqualTo(409);
        assertThat(daIncassare.body()).contains("ha ancora 1 pagamento da incassare");
        var elencoMetodi = elenco(lista(admin.get("/api/pagamenti/metodi")));
        assertThat(elencoMetodi.stream().filter(m -> posId.equals(m.get("id"))).findFirst().orElseThrow()).containsEntry("eliminabile", false);

        // si chiude la cassa (il POS torna): il metodo del POS è libero, il bonifico aspetta ancora di essere incassato
        orologio.alle("2030-09-02T12:00");
        double attesi = num(mappa(mappa(json(segr.post("/api/cassa/casse/" + cassa + "/confronta", "{\"contati\":0}")).get("sessione")).get("conti")).get("attesi"));
        var chiusa = segr.post("/api/cassa/casse/" + cassa + "/chiudi", "{\"contati\":" + attesi + ",\"lasciato\":" + Math.min(100, attesi)
            + ",\"riscontri\":[{\"metodoId\":\"" + posId + "\",\"importo\":30}]}");
        assertThat(chiusa.statusCode()).as(chiusa.body()).isEqualTo(200);
        String sessione = (String) json(chiusa).get("id");
        elencoMetodi = elenco(lista(admin.get("/api/pagamenti/metodi")));
        assertThat(elencoMetodi.stream().filter(m -> posId.equals(m.get("id"))).findFirst().orElseThrow()).containsEntry("eliminabile", true).containsEntry("motivoBlocco", null);
        assertThat(admin.delete("/api/pagamenti/metodi/" + bancaId).statusCode()).isEqualTo(409);
        String voceBonifico = (String) elenco(json(segr.get("/api/pagamenti/persone/" + anna)).get("pagamenti")).stream()
            .flatMap(p -> elenco(p.get("metodi")).stream()).filter(m -> bancaId.equals(m.get("metodoId"))).toList().getFirst().get("id");
        assertThat(segr.post("/api/pagamenti/metodi-usati/" + voceBonifico + "/incassa", "{}").statusCode()).isEqualTo(204);

        // tutto chiuso: si eliminano tutti e due, anche se sono stati usati
        assertThat(segr.delete("/api/pagamenti/metodi/" + posId).statusCode()).isEqualTo(403);
        assertThat(admin.delete("/api/pagamenti/metodi/" + posId).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/pagamenti/metodi/" + bancaId).statusCode()).isEqualTo(204);
        assertThat(elenco(lista(admin.get("/api/pagamenti/metodi"))).stream().map(m -> m.get("nome"))).doesNotContain(pos, banca);

        // lo storico legge la copia: il conto della persona, gli incassi del giorno, la cassa chiusa e la ricevuta mostrano ancora nomi e totali
        var conto = json(segr.get("/api/pagamenti/persone/" + anna));
        var usati = elenco(conto.get("pagamenti")).stream().flatMap(p -> elenco(p.get("metodi")).stream()).toList();
        // il metodo rinominato mostra il nome di allora nei due pagamenti (prima e dopo la rinomina)
        assertThat(usati).extracting(m -> m.get("metodo")).containsExactlyInAnyOrder(pos, banca, terzo, terzoNuovo);
        assertThat(usati.stream().filter(m -> pos.equals(m.get("metodo")) || banca.equals(m.get("metodo")))).allSatisfy(m -> assertThat(m.get("metodoId")).isNull());
        assertThat(usati.stream().filter(m -> banca.equals(m.get("metodo"))).findFirst().orElseThrow()).containsEntry("daIncassare", true).containsEntry("importo", 20.0);
        assertThat(mappa(conto.get("totali"))).containsEntry("daIncassare", 0.0);
        var incassi = json(segr.get("/api/pagamenti/incassi?da=2030-09-02&a=2030-09-02"));
        var perMetodo = elenco(incassi.get("perMetodo"));
        assertThat(perMetodo.stream().filter(m -> pos.equals(m.get("metodo"))).findFirst().orElseThrow()).containsEntry("importo", 30.0);
        assertThat(perMetodo.stream().filter(m -> banca.equals(m.get("metodo"))).findFirst().orElseThrow()).containsEntry("importo", 20.0);
        assertThat(num(incassi.get("totale"))).isEqualTo(60.0);
        // due metodi eliminati nello stesso periodo sono due righe distinte (id vuoto, nome della copia)
        assertThat(perMetodo.stream().filter(m -> m.get("metodoId") == null).map(m -> m.get("metodo"))).containsExactlyInAnyOrder(pos, banca);
        var chiusaDopo = json(segr.get("/api/cassa/sessioni/" + sessione));
        assertThat(elenco(mappa(chiusaDopo.get("conti")).get("perMetodo"))).extracting(m -> m.get("metodo")).contains(pos, banca);
        assertThat(elenco(chiusaDopo.get("riscontri"))).extracting(r -> r.get("metodo")).contains(pos);
        assertThat(segr.scarica("/api/cassa/sessioni/" + sessione + "/pdf").statusCode()).isEqualTo(200);
        var ricevute = segr.post("/api/ricevute", "{\"pagamentoId\":\"" + pagPos + "\"}");
        assertThat(ricevute.statusCode()).as(ricevute.body()).isEqualTo(201);
        assertThat(mappa(lista(ricevute).getFirst())).containsEntry("metodi", pos);

        // si può annullare un pagamento vecchio fatto con un metodo che non c'è più; la chiusura fatta non cambia
        var annullato = coord.post("/api/pagamenti/" + pagPos + "/annulla", "{\"motivo\":\"Prova\"}");
        assertThat(annullato.statusCode()).as(annullato.body()).isEqualTo(204);
        assertThat(json(segr.get("/api/cassa/sessioni/" + sessione)).get("conti").toString()).contains(pos);
        assertThat(num(json(segr.get("/api/pagamenti/incassi?da=2030-09-02&a=2030-09-02")).get("totale"))).isEqualTo(30.0);

        // un pagamento a cassa chiusa, dopo l'ultima chiusura, aspetta la chiusura successiva: il metodo non si elimina finché non c'è
        orologio.alle("2030-09-02T13:00");
        paga(segr, anna, terzoNuovo, 3);
        var dopoChiusura = admin.delete("/api/pagamenti/metodi/" + terzoId);
        assertThat(dopoChiusura.statusCode()).isEqualTo(409);
        assertThat(dopoChiusura.body()).contains("cassa non ancora chiusa");
        orologio.alle("2030-09-02T13:30");
        azzera(segr, cassa);
        assertThat(admin.delete("/api/pagamenti/metodi/" + terzoId).statusCode()).isEqualTo(204);

        // si torna alle scelte di base
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"cieca\":true,\"tolleranza\":5,\"controllo\":\"OLTRE\",\"spese\":true,\"banca\":false,\"oreAvviso\":14}").statusCode()).isEqualTo(200);
    }

    @Test
    @Order(5)
    void conLaCassaApertaUnMetodoNonEntraNeEsceDalCassetto() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        orologio.alle("2030-10-07T07:00");
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"cieca\":false,\"tolleranza\":5,\"controllo\":\"OLTRE\",\"spese\":true,\"banca\":false,\"oreAvviso\":14}").statusCode()).isEqualTo(200);
        String cassa = (String) elenco(json(segr.get("/api/cassa")).get("casse")).stream().filter(c -> Boolean.TRUE.equals(c.get("principale"))).findFirst().orElseThrow().get("id");
        azzera(segr, cassa);

        String nome = "Gettone " + java.util.UUID.randomUUID().toString().substring(0, 6);
        String id = creaId(admin, "/api/pagamenti/metodi", "{\"nome\":\"" + nome + "\"}");
        String url = "/api/pagamenti/metodi/" + id;
        String corpo = "{\"nome\":\"" + nome + "\",\"contanti\":%s,\"daIncassare\":%s}";

        // cassa chiusa: si passa nel cassetto e si torna
        var dentro = admin.put(url, corpo.formatted(true, false));
        assertThat(dentro.statusCode()).as(dentro.body()).isEqualTo(200);
        assertThat(json(dentro)).containsEntry("contanti", true);
        assertThat(json(admin.put(url, corpo.formatted(false, false)))).containsEntry("contanti", false);

        // cassa aperta: il metodo non entra nel cassetto (409 col nome e cosa fare)…
        orologio.alle("2030-10-07T08:00");
        assertThat(segr.post("/api/cassa/casse/" + cassa + "/apri", "{}").statusCode()).isEqualTo(200);
        var no = admin.put(url, corpo.formatted(true, false));
        assertThat(no.statusCode()).isEqualTo(409);
        assertThat(json(no).get("detail")).isEqualTo("Non si può cambiare adesso: c'è una cassa aperta.\nChiudi la cassa: poi potrai spostare «" + nome + "» da o verso «Nel cassetto».");
        assertThat(elenco(lista(admin.get("/api/pagamenti/metodi"))).stream().filter(m -> id.equals(m.get("id"))).findFirst().orElseThrow()).containsEntry("contanti", false);

        // …ma nome, acceso/spento e «da incassare» ↔ «incassato subito» si cambiano lo stesso
        assertThat(json(admin.put(url, corpo.formatted(false, true)))).containsEntry("daIncassare", true).containsEntry("contanti", false);
        assertThat(json(admin.put(url, corpo.formatted(false, false)))).containsEntry("daIncassare", false);
        assertThat(json(admin.put(url, "{\"nome\":\"" + nome + " bis\",\"attivo\":false}"))).containsEntry("nome", nome + " bis").containsEntry("attivo", false);
        assertThat(json(admin.put(url, "{\"nome\":\"" + nome + "\",\"attivo\":true}"))).containsEntry("attivo", true);
        // da incassare con il cassetto in un colpo solo è sempre impossibile
        assertThat(admin.put(url, corpo.formatted(true, true)).statusCode()).isIn(400, 409);

        // un metodo che è già nel cassetto non ne esce a cassa aperta
        orologio.alle("2030-10-07T08:30");
        String contanti = metodoPagamento(admin, "Contanti");
        var esce = admin.put("/api/pagamenti/metodi/" + contanti, "{\"nome\":\"Contanti\",\"contanti\":false}");
        assertThat(esce.statusCode()).isEqualTo(409);
        assertThat(esce.body()).contains("«Contanti»").contains("Chiudi la cassa");
        // lo stesso valore (il foglio rimanda tutto com'è) non è un cambio
        assertThat(admin.put("/api/pagamenti/metodi/" + contanti, "{\"nome\":\"Contanti\",\"contanti\":true}").statusCode()).isEqualTo(200);

        // cassa chiusa: ora sì
        orologio.alle("2030-10-07T12:00");
        azzera(segr, cassa);
        var finalmente = admin.put(url, corpo.formatted(true, false));
        assertThat(finalmente.statusCode()).as(finalmente.body()).isEqualTo(200);
        assertThat(json(finalmente)).containsEntry("contanti", true);

        assertThat(admin.delete(url).statusCode()).isEqualTo(204);
        // si torna alle scelte di base
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\","
            + "\"cieca\":true,\"tolleranza\":5,\"controllo\":\"OLTRE\",\"spese\":true,\"banca\":false,\"oreAvviso\":14}").statusCode()).isEqualTo(200);
    }

    private static String corpoOrdine(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    private static List<String> idCasse(ClientApi c) throws Exception {
        return elenco(json(c.get("/api/cassa")).get("casse")).stream().map(x -> (String) x.get("id")).toList();
    }

    @Test
    @Order(6)
    void leCasseSiRiordinanoESiEliminanoSoloSeMaiAperte() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var anonimo = anonimo();
        orologio.alle("2030-07-01T08:00");
        String imp = "{\"casse\":\"PER_POSTAZIONE\",\"fondo\":\"FISSO\",\"importoFondo\":150,\"chiusura\":\"GIORNATA\",\"cieca\":false}";
        assertThat(admin.put("/api/cassa/impostazioni", imp).statusCode()).isEqualTo(200);
        String nomeUno = "Cassa uno " + java.util.UUID.randomUUID().toString().substring(0, 6);
        var uno = crea(admin, "/api/cassa/casse", "{\"nome\":\"" + nomeUno + "\"}");
        String nomeDue = "Cassa due " + java.util.UUID.randomUUID().toString().substring(0, 6);
        var due = crea(admin, "/api/cassa/casse", "{\"nome\":\"" + nomeDue + "\"}");
        assertThat(uno).containsEntry("eliminabile", true).containsEntry("motivoBlocco", null);
        List<String> prima = idCasse(admin);
        assertThat(prima).hasSizeGreaterThanOrEqualTo(3);

        // riordino: serve il permesso, e gli id di tutte le casse, ognuna una volta
        List<String> rovesciato = new java.util.ArrayList<>(prima);
        java.util.Collections.reverse(rovesciato);
        assertThat(segr.put("/api/cassa/casse/ordine", corpoOrdine(rovesciato)).statusCode()).isEqualTo(403);
        assertThat(anonimo.put("/api/cassa/casse/ordine", corpoOrdine(rovesciato)).statusCode()).isIn(401, 403);
        assertThat(admin.put("/api/cassa/casse/ordine", "{\"ids\":[]}").statusCode()).isEqualTo(400);
        var manca = admin.put("/api/cassa/casse/ordine", corpoOrdine(prima.subList(1, prima.size())));
        assertThat(manca.statusCode()).isEqualTo(400);
        assertThat(manca.body()).contains("L'elenco delle casse è cambiato");
        List<String> doppione = new java.util.ArrayList<>(prima);
        doppione.set(0, prima.get(1));
        var doppio = admin.put("/api/cassa/casse/ordine", corpoOrdine(doppione));
        assertThat(doppio.statusCode()).isEqualTo(400);
        assertThat(doppio.body()).contains("compare due volte");
        assertThat(idCasse(admin)).isEqualTo(prima);
        var r = admin.put("/api/cassa/casse/ordine", corpoOrdine(rovesciato));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(lista(r).stream().map(x -> (String) mappa(x).get("id")).toList()).isEqualTo(rovesciato);
        assertThat(idCasse(segr)).isEqualTo(rovesciato);
        assertThat(admin.put("/api/cassa/casse/ordine", corpoOrdine(prima)).statusCode()).isEqualTo(200);
        assertThat(idCasse(admin)).isEqualTo(prima);

        // chi non gestisce le impostazioni non sa se una cassa si elimina, e non la elimina
        assertThat(elenco(json(segr.get("/api/cassa")).get("casse"))).allSatisfy(x -> {
            assertThat(x.get("eliminabile")).isNull();
            assertThat(x.get("motivoBlocco")).isNull();
        });
        String urlUno = "/api/cassa/casse/" + uno.get("id");
        assertThat(segr.delete(urlUno).statusCode()).isEqualTo(403);
        assertThat(anonimo.delete(urlUno).statusCode()).isIn(401, 403);

        // la principale non si elimina
        var principale = elenco(json(admin.get("/api/cassa")).get("casse")).stream().filter(x -> Boolean.TRUE.equals(x.get("principale"))).findFirst().orElseThrow();
        assertThat(principale).containsEntry("eliminabile", false);
        var no = admin.delete("/api/cassa/casse/" + principale.get("id"));
        assertThat(no.statusCode()).isEqualTo(409);
        assertThat((String) json(no).get("detail")).isEqualTo(principale.get("motivoBlocco"))
            .startsWith("Non si può eliminare: «" + principale.get("nome") + "» è la cassa principale.\n");

        // una cassa mai aperta si elimina; la seconda volta non c'è più
        assertThat(admin.delete(urlUno).statusCode()).isEqualTo(204);
        assertThat(idCasse(admin)).doesNotContain((String) uno.get("id"));
        assertThat(admin.delete(urlUno).statusCode()).isEqualTo(404);
        // il nome torna libero
        assertThat(admin.post("/api/cassa/casse", "{\"nome\":\"" + nomeUno + "\"}").statusCode()).isEqualTo(201);

        // una cassa che è stata aperta ha uno storico: non si elimina, si spegne
        String idDue = (String) due.get("id");
        String urlDue = "/api/cassa/casse/" + idDue;
        assertThat(segr.post("/api/cassa/casse/" + idDue + "/apri", "{}").statusCode()).isEqualTo(200);
        var aperta = admin.delete(urlDue);
        assertThat(aperta.statusCode()).isEqualTo(409);
        assertThat((String) json(aperta).get("detail")).startsWith("Non si può eliminare: la cassa «" + nomeDue + "» ha 1 sessione nello storico.\nChiudila e poi puoi spegnerla:");
        var chiusuraDue = segr.post("/api/cassa/casse/" + idDue + "/chiudi", "{\"contati\":150,\"lasciato\":150}");
        assertThat(chiusuraDue.statusCode()).isEqualTo(200);
        String sessioneChiusa = (String) json(chiusuraDue).get("id");
        var chiusa = admin.delete(urlDue);
        assertThat(chiusa.statusCode()).isEqualTo(409);
        assertThat((String) json(chiusa).get("detail")).isEqualTo("Non si può eliminare: la cassa «" + nomeDue + "» ha 1 sessione nello storico.\n"
            + "Puoi spegnerla: le sessioni restano com'erano, ma la cassa non verrà più proposta per le nuove.");
        var conBlocco = elenco(json(admin.get("/api/cassa")).get("casse")).stream().filter(x -> idDue.equals(x.get("id"))).findFirst().orElseThrow();
        assertThat(conBlocco).containsEntry("eliminabile", false).containsEntry("motivoBlocco", json(chiusa).get("detail"));
        assertThat(admin.put(urlDue, "{\"attiva\":false}").statusCode()).isEqualTo(200);
        assertThat((String) json(admin.delete(urlDue)).get("detail")).contains("\nÈ già spenta: le sessioni restano com'erano");
        assertThat(idCasse(admin)).contains(idDue);

        // una cassa spenta non si propone più: un computer che l'aveva scelta paga lo stesso, e il movimento nuovo va nella principale;
        // le sessioni già chiuse della cassa spenta non cambiano
        orologio.alle("2030-11-03T09:00"); // dopo le chiusure delle altre prove: la principale conta da lì
        String idPrincipale = (String) principale.get("id");
        double entratiPrima = num(conti(stato(admin, idPrincipale)).get("entrati"));
        var chiusaPrima = json(admin.get("/api/cassa/sessioni/" + sessioneChiusa));
        paga(come("segreteria@demo.leone").dallaCassa(idDue), persona(segr, "Spenta"), "Contanti", 12);
        assertThat(num(conti(stato(admin, idPrincipale)).get("entrati"))).isEqualTo(entratiPrima + 12);
        assertThat(json(admin.get("/api/cassa/sessioni/" + sessioneChiusa))).isEqualTo(chiusaPrima);

        // una cassa mai aperta ma con pagamenti registrati sopra non si elimina: i pagamenti restano com'erano
        String nomeTre = "Cassa tre " + java.util.UUID.randomUUID().toString().substring(0, 6);
        String idTre = (String) crea(admin, "/api/cassa/casse", "{\"nome\":\"" + nomeTre + "\"}").get("id");
        var segr3 = come("segreteria@demo.leone").dallaCassa(idTre);
        paga(segr3, persona(segr, "Tre"), "Contanti", 10);
        var conPagamento = admin.delete("/api/cassa/casse/" + idTre);
        assertThat(conPagamento.statusCode()).isEqualTo(409);
        assertThat((String) json(conPagamento).get("detail")).isEqualTo("Non si può eliminare: la cassa «" + nomeTre + "» ha 1 pagamento registrato.\n"
            + "Puoi spegnerla: i pagamenti restano com'erano, ma la cassa non verrà più proposta per i nuovi pagamenti.");
        var tre = elenco(json(admin.get("/api/cassa")).get("casse")).stream().filter(x -> idTre.equals(x.get("id"))).findFirst().orElseThrow();
        assertThat(tre).containsEntry("eliminabile", false).containsEntry("motivoBlocco", json(conPagamento).get("detail"));
        assertThat(admin.put("/api/cassa/casse/" + idTre, "{\"attiva\":false}").statusCode()).isEqualTo(200);
        assertThat((String) json(admin.delete("/api/cassa/casse/" + idTre)).get("detail")).contains("\nÈ già spenta: i pagamenti restano com'erano");

        // pulizia: la cassa ricreata (mai aperta), e si torna alle scelte di base; «Cassa due prova» resta spenta con il suo storico
        String ricreata = elenco(json(admin.get("/api/cassa")).get("casse")).stream().filter(x -> nomeUno.equals(x.get("nome"))).findFirst().orElseThrow()
            .get("id").toString();
        assertThat(admin.delete("/api/cassa/casse/" + ricreata).statusCode()).isEqualTo(204);
        assertThat(admin.put("/api/cassa/impostazioni", "{\"casse\":\"UNICA\",\"fondo\":\"RIPORTATO\",\"importoFondo\":100,\"chiusura\":\"TURNO\"}").statusCode())
            .isEqualTo(200);
    }
}
