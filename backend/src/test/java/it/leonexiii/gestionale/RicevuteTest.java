package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.MimeMultipart;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ricevute (ADR 0018, parte 10): un genitore paga per due figli; la ricevuta per famiglia (intestata a lui, con i figli,
 * il bollo e la dicitura), il PDF, l'email con l'allegato, l'annullo con «rifai» (il numero non torna libero), una per
 * figlio, l'annullo automatico con il pagamento e le impostazioni.
 */
class RicevuteTest extends ProvaApi {

    @Autowired
    AmbienteDiProva.CasellaFinta casella;

    private static final String ANAG = """
        {"nome":"%s","cognome":"Bianchi","dataNascita":"%s","sesso":"%s","luogoNascita":"Milano","codiceFiscale":"%s",
         "indirizzo":"Via delle Prove 10","cap":"20100","citta":"Milano","provincia":"MI","nazione":"Italia"}""";

    private String persona(ClientApi segr, String nome, String nascita, String sesso, String cf) throws Exception {
        String id = creaId(segr, "/api/clienti", "{\"nome\":\"" + nome + "\",\"cognome\":\"Bianchi\",\"dataNascita\":\"" + nascita + "\"}");
        var r = segr.put("/api/clienti/" + id + "/anagrafica", ANAG.formatted(nome, nascita, sesso, cf));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return id;
    }

    private String addebito(ClientApi segr, String persona, String descrizione, int importo) throws Exception {
        return creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"" + descrizione + "\",\"importo\":" + importo
            + ",\"scadenza\":\"2028-03-31\"}");
    }

    private String paga(ClientApi segr, String pagante, int importo, String... addebiti) throws Exception {
        var r = crea(segr, "/api/pagamenti", "{\"paganteId\":\"" + pagante + "\",\"addebiti\":[\"" + String.join("\",\"", addebiti) + "\"],\"metodi\":[{\"metodoId\":\""
            + metodoPagamento(segr, "Contanti") + "\",\"importo\":" + importo + "}],\"separati\":false}");
        return (String) r.get("id");
    }

    private List<Map<String, Object>> emetti(ClientApi c, String pagamento, String modo) throws Exception {
        var r = c.post("/api/ricevute", "{\"pagamentoId\":\"" + pagamento + "\"" + (modo == null ? "" : ",\"modo\":\"" + modo + "\"") + "}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return lista(r).stream().map(ProvaApi::mappa).toList();
    }

    @Test
    void unGenitorePagaPerDueFigli() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var staff = come("staff@demo.leone");
        orologio.alle("2028-03-10T10:00");
        String marco = persona(segr, "Marco", "1980-01-01", "M", "BNCMRC80A01F205F");
        String sofia = persona(segr, "Sofia", "2016-04-14", "F", "BNCSFO16D54F205J");
        String luca = persona(segr, "Luca", "2018-03-02", "M", "BNCLCU18C02F205R");
        assertThat(segr.put("/api/clienti/" + marco + "/contatti", "{\"email\":\"marco.bianchi@esempio.it\"}").statusCode()).isEqualTo(200);
        String a1 = addebito(segr, sofia, "Corso Delfini, 1ª rata", 300);
        String a2 = addebito(segr, luca, "Corso Squali, 1ª rata", 200);
        String pagamento = paga(segr, marco, 500, a1, a2);

        String prossima = (String) elenco(json(segr.get("/api/ricevute/impostazioni")).get("serie")).getFirst().get("prossima");
        assertThat(staff.post("/api/ricevute", "{\"pagamentoId\":\"" + pagamento + "\"}").statusCode()).isEqualTo(403);

        // una per famiglia: intestata a chi paga, con i due figli, il bollo (sopra 77,47 €) e la dicitura riempita
        var famiglia = emetti(segr, pagamento, "FAMIGLIA");
        assertThat(famiglia).hasSize(1);
        var r = famiglia.getFirst();
        assertThat(r).containsEntry("numero", prossima).containsEntry("totale", 500.0).containsEntry("bollo", 2.0).containsEntry("metodi", "Contanti")
            .containsEntry("data", "2028-03-10").containsEntry("tipo", "RICEVUTA");
        assertThat(mappa(r.get("intestatario"))).containsEntry("nome", "Marco").containsEntry("codiceFiscale", "BNCMRC80A01F205F")
            .containsEntry("indirizzo", "Via delle Prove 10 · 20100 Milano (MI)").containsEntry("email", "marco.bianchi@esempio.it");
        assertThat(elenco(r.get("beneficiari"))).extracting(b -> b.get("nome")).containsExactly("Sofia", "Luca");
        assertThat(elenco(r.get("righe"))).extracting(x -> x.get("per")).containsExactly("Sofia Bianchi", "Luca Bianchi");
        assertThat((String) r.get("dicitura")).contains("Attività svolta da Sofia Bianchi e Luca Bianchi, nato/a il 14/04/2016 e 02/03/2018.");
        // chiesta di nuovo, è la stessa
        assertThat(emetti(segr, pagamento, "PERSONA")).extracting(x -> x.get("id")).containsExactly(r.get("id"));
        String ricevuta = (String) r.get("id");

        // il PDF
        var pdf = segr.get("/api/ricevute/" + ricevuta + "/pdf");
        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(pdf.headers().firstValue("Content-Type")).contains("application/pdf");
        assertThat(pdf.body()).startsWith("%PDF");
        assertThat(staff.get("/api/ricevute/" + ricevute(segr, marco).getFirst().get("id") + "/pdf").statusCode()).isEqualTo(403);

        // per email a chi paga, con il PDF allegato
        int primaDi = casella.conAllegati.size();
        var invio = json(segr.post("/api/ricevute/" + ricevuta + "/invia", "{}"));
        assertThat(invio).containsEntry("stato", "INVIATA");
        assertThat(mappa(invio.get("ricevuta"))).containsEntry("inviataA", "marco.bianchi@esempio.it");
        var email = casella.conAllegati.get(primaDi);
        assertThat(email.getSubject()).startsWith("Ricevuta n. " + prossima);
        email.saveChanges(); // come farebbe il server di posta: calcola i tipi delle parti
        var parti = (MimeMultipart) email.getContent();
        assertThat(parti.getCount()).isEqualTo(2);
        assertThat(parti.getBodyPart(1).getFileName()).endsWith(".pdf");
        assertThat(parti.getBodyPart(1).getContentType()).startsWith("application/pdf");
        assertThat(segr.post("/api/ricevute/" + ricevuta + "/invia", "{\"email\":\"non-una-email\"}").statusCode()).isEqualTo(400);
        assertThat(json(segr.post("/api/ricevute/" + ricevuta + "/stampata", "{}")).get("stampataIl")).isNotNull();

        // annullare: il motivo serve e il permesso anche; rifatta, prende il numero dopo e la vecchia resta
        assertThat(segr.post("/api/ricevute/" + ricevuta + "/annulla", "{\"motivo\":\"x\"}").statusCode()).isEqualTo(403);
        assertThat(coord.post("/api/ricevute/" + ricevuta + "/annulla", "{\"motivo\":\" \"}").statusCode()).isEqualTo(400);
        var rifatta = lista(coord.post("/api/ricevute/" + ricevuta + "/annulla", "{\"motivo\":\"Indirizzo sbagliato\",\"rifai\":true}"));
        assertThat(rifatta).hasSize(1);
        var nuova = mappa(rifatta.getFirst());
        assertThat((String) nuova.get("numero")).isNotEqualTo(prossima);
        var vecchia = ricevute(segr, marco).stream().filter(x -> ricevuta.equals(x.get("id"))).findFirst().orElseThrow();
        assertThat(vecchia).containsEntry("motivoAnnullamento", "Indirizzo sbagliato").containsEntry("sostituitaDa", nuova.get("id"))
            .containsEntry("sostituitaDaNumero", nuova.get("numero"));
        assertThat(segr.post("/api/ricevute/" + ricevuta + "/invia", "{}").statusCode()).isEqualTo(409);
        assertThat(coord.post("/api/ricevute/" + ricevuta + "/annulla", "{\"motivo\":\"di nuovo\"}").statusCode()).isEqualTo(400);
        assertThat(segr.get("/api/ricevute/" + ricevuta + "/pdf").body()).startsWith("%PDF");

        // una per figlio: si annulla quella di famiglia e se ne fanno due, intestate al padre
        assertThat(coord.post("/api/ricevute/" + nuova.get("id") + "/annulla", "{\"motivo\":\"Una per figlio, per le detrazioni\"}").statusCode()).isEqualTo(200);
        var perFiglio = emetti(segr, pagamento, "PERSONA");
        assertThat(perFiglio).hasSize(2);
        assertThat(perFiglio).extracting(x -> elenco(x.get("beneficiari")).getFirst().get("nome")).containsExactly("Sofia", "Luca");
        assertThat(perFiglio).extracting(x -> mappa(x.get("intestatario")).get("nome")).containsOnly("Marco");
        assertThat(perFiglio).extracting(x -> x.get("totale")).containsExactly(300.0, 200.0);
        // ognuno ritrova le sue: Sofia la sua (e quelle di famiglia annullate), Marco tutte
        assertThat(ricevute(segr, sofia)).extracting(x -> x.get("id")).contains(perFiglio.get(0).get("id")).doesNotContain(perFiglio.get(1).get("id"));
        assertThat(ricevute(segr, marco)).hasSize(4);

        // annullato il pagamento, le sue ricevute si annullano; e non se ne fanno più
        assertThat(coord.post("/api/pagamenti/" + pagamento + "/annulla", "{\"motivo\":\"Errore di cassa\"}").statusCode()).isEqualTo(204);
        assertThat(lista(segr.get("/api/ricevute?pagamentoId=" + pagamento))).map(ProvaApi::mappa)
            .allSatisfy(x -> assertThat(x.get("annullataIl")).isNotNull());
        assertThat(lista(segr.get("/api/ricevute?pagamentoId=" + pagamento))).map(ProvaApi::mappa)
            .filteredOn(x -> x.get("id").equals(perFiglio.getFirst().get("id"))).singleElement()
            .satisfies(x -> assertThat(x).containsEntry("motivoAnnullamento", "Pagamento annullato: Errore di cassa"));
        assertThat(segr.post("/api/ricevute", "{\"pagamentoId\":\"" + pagamento + "\"}").statusCode()).isEqualTo(409);
        assertThat(segr.post("/api/ricevute", "{\"pagamentoId\":\"" + UUID.randomUUID() + "\"}").statusCode()).isEqualTo(404);
    }

    private List<Map<String, Object>> ricevute(ClientApi c, String persona) throws Exception {
        return lista(c.get("/api/ricevute?personaId=" + persona)).stream().map(ProvaApi::mappa).toList();
    }

    @Test
    void leImpostazioniDelleRicevute() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var pagina = json(segr.get("/api/ricevute/impostazioni"));
        var imp = mappa(pagina.get("impostazioni"));
        assertThat(imp).containsEntry("intestata", "PAGANTE").containsEntry("famiglia", "CHIEDI").containsEntry("bolloSoglia", 77.47);
        var serie = elenco(pagina.get("serie"));
        assertThat(serie).extracting(s -> s.get("nome")).containsExactly("Ricevute", "Fatture");
        assertThat(serie.get(1)).containsEntry("attiva", false);
        String fatture = (String) serie.get(1).get("id");

        String valide = "{\"intestata\":\"PAGANTE\",\"codiciFiscali\":true,\"bollo\":true,\"bolloSoglia\":77.47,\"bolloImporto\":2,\"dicitura\":\"Testo\","
            + "\"firma\":true,\"dopo\":\"CHIEDI\",\"email\":true,\"famiglia\":\"CHIEDI\"}";
        assertThat(segr.put("/api/ricevute/impostazioni", valide).statusCode()).isEqualTo(403);
        assertThat(admin.put("/api/ricevute/impostazioni", valide.replace("\"PAGANTE\"", "\"ZIO\"")).statusCode()).isEqualTo(400);
        assertThat(admin.put("/api/ricevute/impostazioni", valide.replace("77.47", "-1")).statusCode()).isEqualTo(400);
        assertThat(admin.put("/api/ricevute/impostazioni", valide.replace("\"CHIEDI\",\"email\"", "\"BOH\",\"email\"")).statusCode()).isEqualTo(400);

        // una serie: il formato vuole {numero}; il numero di partenza si sceglie finché il periodo è vuoto
        String serieFatture = "/api/ricevute/serie/" + fatture;
        assertThat(admin.put(serieFatture, "{\"nome\":\"Fatture\",\"attiva\":true,\"formato\":\"F{anno}\",\"cifre\":4,\"riparte\":\"ANNO\"}").statusCode()).isEqualTo(400);
        assertThat(admin.put(serieFatture, "{\"nome\":\"Fatture\",\"attiva\":true,\"formato\":\"F{anno}/{numero}\",\"cifre\":9,\"riparte\":\"ANNO\"}").statusCode()).isEqualTo(400);
        orologio.alle("2028-05-02T10:00");
        var cambiata = json(admin.put(serieFatture, "{\"nome\":\"Fatture\",\"attiva\":true,\"formato\":\"F{anno}/{numero}\",\"cifre\":3,\"riparte\":\"ANNO\",\"prossimoNumero\":120}"));
        assertThat(cambiata).containsEntry("prossima", "F2028/120").containsEntry("periodo", "2028").containsEntry("emesse", 0);
        assertThat(admin.put(serieFatture, "{\"nome\":\"Fatture\",\"attiva\":false,\"formato\":\"F{anno}/{numero}\",\"cifre\":4,\"riparte\":\"ANNO\"}").statusCode())
            .isEqualTo(200);

        // la serie delle ricevute, che ha già ricevute in questa stagione (dall'altro test, o le si fa ora), non cambia partenza
        var nuova = crea(admin, "/api/ricevute/serie", "{\"nome\":\"Ricevute del campo estivo\",\"tipo\":\"RICEVUTA\"}");
        assertThat(nuova).containsEntry("attiva", false).containsEntry("formato", "{stagione}-{numero}");
        assertThat(admin.post("/api/ricevute/serie", "{\"nome\":\" \",\"tipo\":\"RICEVUTA\"}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/ricevute/serie", "{\"nome\":\"Senza tipo\"}").statusCode()).isEqualTo(400);
        assertThat(pagina.get("mancaIntestazione")).isNotNull();
    }
}
