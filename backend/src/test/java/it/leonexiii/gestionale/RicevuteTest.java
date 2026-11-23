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

    /** L'ordine delle voci è quello scelto da chi registra: né alfabetico né di creazione degli addebiti; è salvato (colonna «posizione»). */
    @Test
    void leVociRestanoNellOrdineScelto() throws Exception {
        var segr = come("segreteria@demo.leone");
        orologio.alle("2028-03-11T10:00");
        String marco = persona(segr, "Marco", "1981-01-01", "M", "RSSMRC81A01F205A");
        String sofia = persona(segr, "Sofia", "2017-04-14", "F", "VRDSFO17D54F205A");
        String luca = persona(segr, "Luca", "2019-03-02", "M", "NRILCU19C02F205E");
        String anna = persona(segr, "Anna", "2020-05-20", "F", "GLLNNA20E60F205D");
        // creati: Sofia, Luca, Anna (alfabetico: Anna, Luca, Sofia); pagati: Luca, Anna, Sofia
        String aSofia = addebito(segr, sofia, "Corso Delfini, 1ª rata", 100);
        String aLuca = addebito(segr, luca, "Corso Squali, 1ª rata", 200);
        String aAnna = addebito(segr, anna, "Corso Trote, 1ª rata", 300);
        String idPagamento = creaId(segr, "/api/pagamenti", "{\"paganteId\":\"" + marco + "\",\"addebiti\":[\"" + aLuca + "\",\"" + aAnna + "\",\"" + aSofia
            + "\"],\"metodi\":[{\"metodoId\":\"" + metodoPagamento(segr, "Contanti") + "\",\"importo\":600}],\"separati\":false}");
        var conto = json(segr.get("/api/pagamenti/persone/" + sofia));
        assertThat(elenco(elenco(conto.get("pagamenti")).getFirst().get("voci"))).extracting(v -> v.get("persona")).containsExactly("Luca Bianchi", "Anna Bianchi", "Sofia Bianchi");

        var r = emetti(segr, idPagamento, "FAMIGLIA").getFirst();
        assertThat(elenco(r.get("beneficiari"))).extracting(b -> b.get("nome")).containsExactly("Luca", "Anna", "Sofia");
        assertThat(elenco(r.get("righe"))).extracting(x -> x.get("per")).containsExactly("Luca Bianchi", "Anna Bianchi", "Sofia Bianchi");
        assertThat((String) r.get("dicitura")).contains("Attività svolta da Luca Bianchi e Anna Bianchi e Sofia Bianchi, nato/a il 02/03/2019 e 20/05/2020 e 14/04/2017.");
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

    private static String corpoOrdine(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    private static List<String> idSerie(List<Map<String, Object>> serie) {
        return serie.stream().map(x -> (String) x.get("id")).toList();
    }

    @Test
    void leSerieSiRiordinanoESiEliminanoSoloSeMaiUsate() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        var anonimo = anonimo();
        List<String> prima = idSerie(elenco(json(admin.get("/api/ricevute/impostazioni")).get("serie")));
        assertThat(prima).hasSizeGreaterThanOrEqualTo(2);

        // riordino: serve il permesso, e gli id di tutte le serie, ognuna una volta
        List<String> rovesciato = new java.util.ArrayList<>(prima);
        java.util.Collections.reverse(rovesciato);
        assertThat(segr.put("/api/ricevute/serie/ordine", corpoOrdine(rovesciato)).statusCode()).isEqualTo(403);
        assertThat(anonimo.put("/api/ricevute/serie/ordine", corpoOrdine(rovesciato)).statusCode()).isIn(401, 403);
        assertThat(admin.put("/api/ricevute/serie/ordine", "{\"ids\":[]}").statusCode()).isEqualTo(400);
        var manca = admin.put("/api/ricevute/serie/ordine", corpoOrdine(prima.subList(1, prima.size())));
        assertThat(manca.statusCode()).isEqualTo(400);
        assertThat(manca.body()).contains("L'elenco delle serie è cambiato");
        List<String> sconosciuta = new java.util.ArrayList<>(prima);
        sconosciuta.set(0, UUID.randomUUID().toString());
        assertThat(admin.put("/api/ricevute/serie/ordine", corpoOrdine(sconosciuta)).statusCode()).isEqualTo(400);
        List<String> doppione = new java.util.ArrayList<>(prima);
        doppione.set(0, prima.get(1));
        var doppio = admin.put("/api/ricevute/serie/ordine", corpoOrdine(doppione));
        assertThat(doppio.statusCode()).isEqualTo(400);
        assertThat(doppio.body()).contains("compare due volte");
        assertThat(idSerie(elenco(json(admin.get("/api/ricevute/impostazioni")).get("serie")))).isEqualTo(prima);

        var r = admin.put("/api/ricevute/serie/ordine", corpoOrdine(rovesciato));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(idSerie(lista(r).stream().map(ProvaApi::mappa).toList())).isEqualTo(rovesciato);
        assertThat(idSerie(elenco(json(segr.get("/api/ricevute/impostazioni")).get("serie")))).isEqualTo(rovesciato);
        assertThat(admin.put("/api/ricevute/serie/ordine", corpoOrdine(prima)).statusCode()).isEqualTo(200);
        assertThat(idSerie(elenco(json(admin.get("/api/ricevute/impostazioni")).get("serie")))).isEqualTo(prima);

        // una serie mai usata si elimina; chi non gestisce le impostazioni non lo sa né lo fa
        var nuova = crea(admin, "/api/ricevute/serie", "{\"nome\":\"Serie di prova\",\"tipo\":\"FATTURA\"}");
        assertThat(nuova).containsEntry("eliminabile", true).containsEntry("motivoBlocco", null).containsEntry("richiesta", false);
        assertThat(elenco(json(segr.get("/api/ricevute/impostazioni")).get("serie"))).allSatisfy(x -> {
            assertThat(x.get("eliminabile")).isNull();
            assertThat(x.get("motivoBlocco")).isNull();
        });
        String urlNuova = "/api/ricevute/serie/" + nuova.get("id");
        assertThat(segr.delete(urlNuova).statusCode()).isEqualTo(403);
        assertThat(anonimo.delete(urlNuova).statusCode()).isIn(401, 403);
        assertThat(admin.delete(urlNuova).statusCode()).isEqualTo(204);
        assertThat(idSerie(elenco(json(admin.get("/api/ricevute/impostazioni")).get("serie")))).doesNotContain((String) nuova.get("id"));
        assertThat(admin.delete(urlNuova).statusCode()).isEqualTo(404);

        // l'ultima serie di ricevute accesa serve al sistema: non si elimina e non si spegne
        var serie = elenco(json(admin.get("/api/ricevute/impostazioni")).get("serie"));
        var ricevute = serie.stream().filter(x -> "Ricevute".equals(x.get("nome"))).findFirst().orElseThrow();
        String urlRicevute = "/api/ricevute/serie/" + ricevute.get("id");
        assertThat(ricevute).containsEntry("richiesta", true).containsEntry("eliminabile", false);
        var ultima = admin.delete(urlRicevute);
        assertThat(ultima.statusCode()).isEqualTo(409);
        assertThat((String) json(ultima).get("detail")).contains("Non si può eliminare: «Ricevute» è l'ultima serie di ricevute accesa.").contains("\nPer eliminarla accendine prima un'altra");
        String corpoSpenta = "{\"nome\":\"Ricevute\",\"attiva\":false,\"formato\":\"" + ricevute.get("formato") + "\",\"cifre\":" + ricevute.get("cifre")
            + ",\"riparte\":\"" + ricevute.get("riparte") + "\"}";
        var spegni = admin.put(urlRicevute, corpoSpenta);
        assertThat(spegni.statusCode()).isEqualTo(409);
        assertThat((String) json(spegni).get("detail")).contains("Non si può spegnere: «Ricevute» è l'ultima serie di ricevute accesa.").contains("\nAccendine prima un'altra");
        // le serie di fatture non sono mai richieste
        assertThat(serie.stream().filter(x -> "FATTURA".equals(x.get("tipo")))).allSatisfy(x -> assertThat(x).containsEntry("richiesta", false));

        // con un'altra serie di ricevute accesa, «Ricevute» non è più l'ultima: se ha emesso ricevute si spegne ma non si elimina
        var seconda = crea(admin, "/api/ricevute/serie", "{\"nome\":\"Seconda serie\",\"tipo\":\"RICEVUTA\"}");
        String urlSeconda = "/api/ricevute/serie/" + seconda.get("id");
        String corpoSeconda = "{\"nome\":\"Seconda serie\",\"attiva\":true,\"formato\":\"{stagione}-{numero}\",\"cifre\":4,\"riparte\":\"STAGIONE\"}";
        var accesa = admin.put(urlSeconda, corpoSeconda);
        assertThat(accesa.statusCode()).as(accesa.body()).isEqualTo(200);
        assertThat(json(accesa)).containsEntry("richiesta", false).containsEntry("eliminabile", true);
        orologio.alle("2028-03-10T10:00");
        String marco = creaId(segr, "/api/clienti", "{\"nome\":\"Serie\",\"cognome\":\"Prova\",\"dataNascita\":\"1985-02-03\"}");
        String pagamento = paga(segr, marco, 40, addebito(segr, marco, "Ingresso", 40));
        var emesse = segr.post("/api/ricevute", "{\"pagamentoId\":\"" + pagamento + "\",\"serieId\":\"" + ricevute.get("id") + "\"}");
        assertThat(emesse.statusCode()).as(emesse.body()).isEqualTo(201);

        var usata = admin.delete(urlRicevute);
        assertThat(usata.statusCode()).isEqualTo(409);
        String dettaglio = (String) json(usata).get("detail");
        assertThat(dettaglio).matches("(?s)Non si può eliminare: la serie «Ricevute» ha emesso \\d+ ricevut[ae]\\.\\n"
            + "Puoi spegnerla: le ricevute restano con il loro numero, ma la serie non verrà più usata per le nuove\\.");
        var conBlocco = elenco(json(admin.get("/api/ricevute/impostazioni")).get("serie")).stream().filter(x -> "Ricevute".equals(x.get("nome"))).findFirst().orElseThrow();
        assertThat(conBlocco).containsEntry("richiesta", false).containsEntry("eliminabile", false).containsEntry("motivoBlocco", dettaglio);

        // pulizia: la seconda serie (mai usata) si elimina, e «Ricevute» torna l'ultima accesa
        assertThat(admin.delete(urlSeconda).statusCode()).isEqualTo(204);
        assertThat(admin.put(urlRicevute, corpoSpenta).statusCode()).isEqualTo(409);
    }
}
