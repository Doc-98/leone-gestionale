package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Impostazioni della società, stagioni con la quota e gruppi di clienti (ADR 0018, parti 1 e 2). */
class SocietaEGruppiTest extends ProvaApi {

    static final String ISTITUTO = "00000000-0000-0000-0000-000000000901", SQUADRA = "00000000-0000-0000-0000-000000000902";
    /** Il vecchio PUT /api/gruppi/precedenze ora cade su /gruppi/{id} con un id non valido. */
    static final int CODICE_VECCHIO_PERCORSO = 400;
    static final String DELFINO_GIALLO = "00000000-0000-0000-0000-000000000615";

    @Autowired
    JdbcTemplate jdbc;

    /** Gli id dei gruppi nell'ordine dell'elenco. */
    private static List<String> idGruppi(ClientApi c) throws Exception {
        return elenco(lista(c.get("/api/gruppi"))).stream().map(g -> (String) g.get("id")).toList();
    }

    /** Nome → precedenza (null = nessuna) nell'ordine dell'elenco. */
    private static Map<String, Object> precedenze(ClientApi c) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var g : elenco(lista(c.get("/api/gruppi")))) out.put((String) g.get("nome"), g.get("precedenza"));
        return out;
    }

    private static Map<String, Object> gruppoDelElenco(ClientApi c, String id) throws Exception {
        return elenco(lista(c.get("/api/gruppi"))).stream().filter(g -> id.equals(g.get("id"))).findFirst().orElseThrow();
    }

    private static String ordineJson(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
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

        // chi è passato di lì non lascia conseguenze: il gruppo si elimina con i due tocchi normali
        assertThat(segr.delete("/api/gruppi/" + gruppo).statusCode()).isEqualTo(403);
        assertThat(admin.delete("/api/gruppi/" + gruppo).statusCode()).isEqualTo(204);
    }

    @Test
    void gruppiConVantaggi() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        // demo: gli alunni dell'Istituto hanno la precedenza 1, 7 giorni per pagare e si possono dichiarare
        String elenco = segr.get("/api/gruppi").body();
        assertThat(elenco).contains("\"precedenza\":1").contains("\"giorniPerPagare\":7").contains("Frequenta la scuola dell'Istituto")
            .contains("\"daVerificare\":1");

        // un gruppo nuovo con precedenza va in fondo all'ordine; i giorni vanno da 1 a 60; la frase serve se è dichiarabile
        assertThat(admin.post("/api/gruppi", "{\"nome\":\"Troppi giorni\",\"giorniPerPagare\":90}").statusCode()).isEqualTo(400);
        assertThat(admin.post("/api/gruppi", "{\"nome\":\"Senza frase\",\"dichiarabile\":true}").statusCode()).isEqualTo(400);
        var r = admin.post("/api/gruppi", "{\"nome\":\"Dipendenti comunali\",\"conPrecedenza\":true,\"giorniPerPagare\":5}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String nuovo = (String) json(r).get("id");
        assertThat(segr.get("/api/gruppi").body()).contains("\"id\":\"" + nuovo + "\",\"nome\":\"Dipendenti comunali\"")
            .containsPattern("\"nome\":\"Dipendenti comunali\"[^}]*\"precedenza\":3");

        // l'ordine dell'elenco è l'ordine delle precedenze: portato in cima, il gruppo ha la precedenza 1
        List<String> prima = idGruppi(admin);
        List<String> nuovoInCima = new ArrayList<>(prima);
        nuovoInCima.remove(nuovo);
        nuovoInCima.addFirst(nuovo);
        var ordina = admin.put("/api/gruppi/ordine", ordineJson(nuovoInCima));
        assertThat(ordina.statusCode()).as(ordina.body()).isEqualTo(200);
        assertThat(segr.get("/api/gruppi").body()).containsPattern("\"nome\":\"Dipendenti comunali\"[^}]*\"precedenza\":1")
            .containsPattern("\"nome\":\"Squadra agonistica\"[^}]*\"precedenza\":3");
        // spenta la precedenza, le altre si rinumerano
        assertThat(admin.put("/api/gruppi/" + nuovo, "{\"nome\":\"Dipendenti comunali\",\"conPrecedenza\":false,\"giorniPerPagare\":5}").statusCode()).isEqualTo(204);
        assertThat(segr.get("/api/gruppi").body()).containsPattern("\"nome\":\"Alunni dell'Istituto\"[^}]*\"precedenza\":1")
            .containsPattern("\"nome\":\"Squadra agonistica\"[^}]*\"precedenza\":2");
        // rimessa com'era: il gruppo nuovo se ne va (senza persone né prezzi, con i due tocchi)
        assertThat(admin.delete("/api/gruppi/" + nuovo).statusCode()).isEqualTo(204);
        assertThat(idGruppi(admin)).isEqualTo(prima.stream().filter(i -> !i.equals(nuovo)).toList());

        // la dichiarazione di Emma aspetta la verifica; verificata, sparisce dall'elenco
        String da = segr.get("/api/gruppi/da-verificare").body();
        assertThat(da).contains("Emma").contains("Frequenta la scuola dell'Istituto");
        String appartenenza = da.replaceAll("(?s).*\"appartenenzaId\":\"([^\"]+)\".*", "$1");
        assertThat(come("staff@demo.leone").post("/api/clienti/appartenenze/" + appartenenza + "/verifica", "{}").statusCode()).isEqualTo(403);
        assertThat(segr.post("/api/clienti/appartenenze/" + appartenenza + "/verifica", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.post("/api/clienti/appartenenze/" + appartenenza + "/verifica", "{}").statusCode()).isEqualTo(409);
        assertThat(segr.get("/api/gruppi/da-verificare").body()).doesNotContain("Emma");
    }

    @Test
    void ordineDeiGruppi() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        List<String> originale = idGruppi(admin);
        Map<String, Object> precedenzeOriginali = precedenze(admin);
        assertThat(precedenzeOriginali).containsEntry("Alunni dell'Istituto", 1).containsEntry("Squadra agonistica", 2);

        // l'elenco dice la posizione di ognuno: 1, 2, 3…
        assertThat(elenco(lista(admin.get("/api/gruppi"))).stream().map(g -> g.get("ordine")).toList())
            .isEqualTo(java.util.stream.IntStream.rangeClosed(1, originale.size()).boxed().toList());

        // il gruppo nuovo va in fondo; con la precedenza accesa è l'ultimo dei gruppi con precedenza
        String nuovo = creaId(admin, "/api/gruppi", "{\"nome\":\"Ordine prova\"}");
        assertThat(idGruppi(admin)).last().isEqualTo(nuovo);
        assertThat(gruppoDelElenco(admin, nuovo)).containsEntry("ordine", originale.size() + 1).containsEntry("precedenza", null);

        // si riordina elencandoli tutti, una volta sola
        List<String> tutti = idGruppi(admin);
        assertThat(segr.put("/api/gruppi/ordine", ordineJson(tutti)).statusCode()).isEqualTo(403);
        String errore = "L'ordine deve contenere, una volta sola, tutti i gruppi.";
        var mancante = admin.put("/api/gruppi/ordine", ordineJson(tutti.subList(1, tutti.size())));
        assertThat(mancante.statusCode()).isEqualTo(400);
        assertThat(json(mancante)).containsEntry("detail", errore);
        List<String> doppio = new ArrayList<>(tutti);
        doppio.set(0, doppio.get(1));
        assertThat(json(admin.put("/api/gruppi/ordine", ordineJson(doppio)))).containsEntry("detail", errore);
        List<String> estraneo = new ArrayList<>(tutti.subList(1, tutti.size()));
        estraneo.add(UUID.randomUUID().toString());
        assertThat(admin.put("/api/gruppi/ordine", ordineJson(estraneo)).statusCode()).isEqualTo(400);
        assertThat(admin.put("/api/gruppi/ordine", "{}").statusCode()).isEqualTo(400);
        assertThat(idGruppi(admin)).isEqualTo(tutti);

        // risponde con l'elenco aggiornato, nel nuovo ordine, e le precedenze seguono la posizione
        List<String> nuovoInCima = new ArrayList<>(tutti);
        nuovoInCima.remove(nuovo);
        nuovoInCima.addFirst(nuovo);
        assertThat(admin.put("/api/gruppi/" + nuovo, "{\"nome\":\"Ordine prova\",\"conPrecedenza\":true}").statusCode()).isEqualTo(204);
        assertThat(precedenze(admin)).containsEntry("Ordine prova", 3);
        var risposta = admin.put("/api/gruppi/ordine", ordineJson(nuovoInCima));
        assertThat(risposta.statusCode()).as(risposta.body()).isEqualTo(200);
        var elencoNuovo = elenco(lista(risposta));
        assertThat(elencoNuovo.stream().map(g -> g.get("id")).toList()).isEqualTo(nuovoInCima);
        assertThat(elencoNuovo.stream().map(g -> g.get("ordine")).toList()).isEqualTo(java.util.stream.IntStream.rangeClosed(1, tutti.size()).boxed().toList());
        assertThat(precedenze(admin)).containsEntry("Ordine prova", 1).containsEntry("Alunni dell'Istituto", 2).containsEntry("Squadra agonistica", 3);

        // il gruppo in mezzo: Istituto e Squadra scambiati, il nuovo tra i due
        List<String> misto = new ArrayList<>(nuovoInCima);
        misto.removeAll(List.of(ISTITUTO, SQUADRA, nuovo));
        misto.addAll(0, List.of(SQUADRA, nuovo, ISTITUTO));
        assertThat(admin.put("/api/gruppi/ordine", ordineJson(misto)).statusCode()).isEqualTo(200);
        assertThat(precedenze(admin)).containsEntry("Squadra agonistica", 1).containsEntry("Ordine prova", 2).containsEntry("Alunni dell'Istituto", 3);

        // accesa o spenta la precedenza, le posizioni si ricalcolano dall'ordine dell'elenco
        assertThat(admin.put("/api/gruppi/" + nuovo, "{\"nome\":\"Ordine prova\",\"conPrecedenza\":false}").statusCode()).isEqualTo(204);
        assertThat(precedenze(admin)).containsEntry("Squadra agonistica", 1).containsEntry("Alunni dell'Istituto", 2).containsEntry("Ordine prova", null);
        // l'interruttore della riga manda solo nome, descrizione e attivo: la precedenza resta com'è
        assertThat(admin.put("/api/gruppi/" + SQUADRA, "{\"nome\":\"Squadra agonistica\",\"descrizione\":\"Atleti tesserati per le gare\",\"attivo\":false}").statusCode()).isEqualTo(204);
        assertThat(precedenze(admin)).containsEntry("Squadra agonistica", 1);
        assertThat(admin.put("/api/gruppi/" + SQUADRA, "{\"nome\":\"Squadra agonistica\",\"descrizione\":\"Atleti tesserati per le gare\",\"attivo\":true}").statusCode()).isEqualTo(204);

        // com'era all'inizio
        assertThat(admin.put("/api/gruppi/ordine", ordineJson(originale.stream().filter(i -> !i.equals(nuovo)).toList())).statusCode()).isEqualTo(400);
        List<String> conNuovo = new ArrayList<>(originale);
        conNuovo.add(nuovo);
        assertThat(admin.put("/api/gruppi/ordine", ordineJson(conNuovo)).statusCode()).isEqualTo(200);
        assertThat(admin.delete("/api/gruppi/" + nuovo).statusCode()).isEqualTo(204);
        assertThat(idGruppi(admin)).isEqualTo(originale);
        assertThat(precedenze(admin)).isEqualTo(precedenzeOriginali);

        // vecchio percorso sparito
        assertThat(admin.put("/api/gruppi/precedenze", "{\"ordine\":[]}").statusCode()).isEqualTo(CODICE_VECCHIO_PERCORSO);
    }

    @Test
    void chiVedeCosaSiElimina() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        // chi gestisce le impostazioni sa quali gruppi hanno conseguenze (il demo ha persone negli Alunni)
        var istituto = gruppoDelElenco(admin, ISTITUTO);
        assertThat(istituto).containsEntry("eliminabile", true).containsEntry("eliminazionePesante", true).containsEntry("motivoBlocco", null);
        // chi lavora solo con i clienti vede i gruppi, ma non come si eliminano
        var perSegreteria = gruppoDelElenco(segr, ISTITUTO);
        assertThat(perSegreteria).containsEntry("eliminabile", null).containsEntry("eliminazionePesante", null).containsEntry("motivoBlocco", null);
        assertThat(perSegreteria).containsEntry("ordine", istituto.get("ordine"));
        // la finestra delle conseguenze la vede solo chi ha le impostazioni; un gruppo che non c'è dà 404
        assertThat(segr.get("/api/gruppi/" + ISTITUTO + "/eliminazione").statusCode()).isEqualTo(403);
        assertThat(admin.get("/api/gruppi/" + UUID.randomUUID() + "/eliminazione").statusCode()).isEqualTo(404);
        assertThat(admin.delete("/api/gruppi/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(admin.delete("/api/gruppi/" + UUID.randomUUID() + "?pesante=true").statusCode()).isEqualTo(404);
        // le conseguenze del demo: tante persone quante dice l'elenco (e la finestra non cambia niente)
        var e = json(admin.get("/api/gruppi/" + ISTITUTO + "/eliminazione"));
        assertThat(e).containsEntry("gruppo", "Alunni dell'Istituto").containsEntry("pesante", true).containsEntry("bloccata", false)
            .containsEntry("motivoBlocco", null).containsEntry("persone", ((Number) istituto.get("persone")).intValue());
        assertThat(idGruppi(admin)).contains(ISTITUTO);
    }

    @Test
    void eliminareUnGruppoConConseguenze() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        List<String> originale = idGruppi(admin);
        Map<String, Object> precedenzeOriginali = precedenze(admin);

        // un gruppo con precedenza in cima all'elenco, due offerte con il suo prezzo, quattro persone
        String gruppo = creaId(admin, "/api/gruppi", "{\"nome\":\"Agonisti prova\",\"conPrecedenza\":true}");
        List<String> conGruppo = new ArrayList<>(originale);
        conGruppo.add(gruppo);
        conGruppo.removeAll(List.of(gruppo));
        conGruppo.addFirst(gruppo);
        assertThat(admin.put("/api/gruppi/ordine", ordineJson(conGruppo)).statusCode()).isEqualTo(200);
        assertThat(precedenze(admin)).containsEntry("Agonisti prova", 1).containsEntry("Alunni dell'Istituto", 2).containsEntry("Squadra agonistica", 3);

        String stagione = creaId(admin, "/api/stagioni", "{\"nome\":\"2070/71\",\"dal\":\"2070-09-01\",\"al\":\"2071-08-31\"}");
        String scuola = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Scuola nuoto","tipo":"ALTRO","ordine":1,"prezzi":[{"importo":100},{"gruppoId":"%s","importo":80}]}""".formatted(stagione, gruppo));
        String adulti = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Nuoto adulti","tipo":"ALTRO","ordine":2,"prezzi":[{"importo":90},{"gruppoId":"%s","importo":70}]}""".formatted(stagione, gruppo));

        String p1 = creaId(segr, "/api/clienti", "{\"nome\":\"Uscente\",\"cognome\":\"Uno\"}");
        String p2 = creaId(segr, "/api/clienti", "{\"nome\":\"Uscente\",\"cognome\":\"Due\"}");
        String p3 = creaId(segr, "/api/clienti", "{\"nome\":\"Appena\",\"cognome\":\"Entrato\"}");
        String p4 = creaId(segr, "/api/clienti", "{\"nome\":\"Dichiarato\",\"cognome\":\"Quattro\"}");
        orologio.alle("2028-03-10T10:00");
        creaId(segr, "/api/clienti/" + p1 + "/gruppi", "{\"gruppoId\":\"" + gruppo + "\"}");
        creaId(segr, "/api/clienti/" + p2 + "/gruppi", "{\"gruppoId\":\"" + gruppo + "\"}");
        // una dichiarazione della famiglia che aspetta la verifica (le famiglie la fanno dalla richiesta, non da un'API del gestionale)
        jdbc.update("insert into appartenenza_gruppo (id, gruppo_id, persona_id, dal, verificata) values (?, ?, ?, date '2028-03-12', false)",
            UUID.randomUUID(), UUID.fromString(gruppo), UUID.fromString(p4));
        orologio.alle("2028-03-20T10:00");
        creaId(segr, "/api/clienti/" + p3 + "/gruppi", "{\"gruppoId\":\"" + gruppo + "\"}");

        // la finestra: numeri veri e righe già scritte, col plurale giusto
        var admin1 = gruppoDelElenco(admin, gruppo);
        assertThat(admin1).containsEntry("persone", 4).containsEntry("eliminabile", true).containsEntry("eliminazionePesante", true).containsEntry("daVerificare", 1);
        var e = json(admin.get("/api/gruppi/" + gruppo + "/eliminazione"));
        assertThat(e).containsEntry("gruppo", "Agonisti prova").containsEntry("pesante", true).containsEntry("bloccata", false)
            .containsEntry("motivoBlocco", null).containsEntry("persone", 4).containsEntry("daVerificare", 1).containsEntry("prezzi", 2)
            .containsEntry("offerte", List.of("Scuola nuoto", "Nuoto adulti"))
            .containsEntry("conseguenze", List.of("4 persone escono dal gruppo", "1 dichiarazione da verificare si chiude",
                "2 prezzi del listino per questo gruppo spariscono (Scuola nuoto, Nuoto adulti)"));

        // con i due tocchi non si elimina: il gruppo potrebbe essersi riempito nel frattempo
        assertThat(segr.delete("/api/gruppi/" + gruppo + "?pesante=true").statusCode()).isEqualTo(403);
        var senza = admin.delete("/api/gruppi/" + gruppo);
        assertThat(senza.statusCode()).isEqualTo(409);
        assertThat(json(senza)).containsEntry("detail", "Non si può eliminare così: «Agonisti prova» ha 4 persone e 2 prezzi nel listino.\n"
            + "Usa di nuovo il cestino: si apre la finestra con le conseguenze.");
        assertThat(admin.delete("/api/gruppi/" + gruppo + "?pesante=false").statusCode()).isEqualTo(409);
        assertThat(idGruppi(admin)).contains(gruppo);

        // si elimina con la finestra: le persone escono tutte insieme
        assertThat(admin.delete("/api/gruppi/" + gruppo + "?pesante=true").statusCode()).isEqualTo(204);
        assertThat(idGruppi(admin)).isEqualTo(originale);
        assertThat(precedenze(admin)).isEqualTo(precedenzeOriginali);
        assertThat(elenco(lista(admin.get("/api/gruppi"))).stream().map(g -> g.get("ordine")).toList())
            .isEqualTo(java.util.stream.IntStream.rangeClosed(1, originale.size()).boxed().toList());
        // chi era dentro da prima: ultimo giorno ieri, con la copia del nome; chi era entrato oggi: come se non fosse mai entrato
        var righe = jdbc.queryForList("select persona_id::text as persona, gruppo_id, gruppo_nome, dal::text as dal, al::text as al from appartenenza_gruppo "
            + "where persona_id in (?, ?, ?, ?) order by dal", UUID.fromString(p1), UUID.fromString(p2), UUID.fromString(p3), UUID.fromString(p4));
        assertThat(righe).hasSize(3);
        assertThat(righe).allSatisfy(r -> assertThat(r).containsEntry("gruppo_id", null).containsEntry("gruppo_nome", "Agonisti prova").containsEntry("al", "2028-03-19"));
        assertThat(righe.stream().map(r -> r.get("persona")).toList()).doesNotContain(p3);
        assertThat(segr.get("/api/clienti/" + p1).body()).contains("\"gruppi\":[]");
        // i prezzi del gruppo sono spariti dal listino; gli altri restano
        String listino = admin.get("/api/listino?stagione=" + stagione).body();
        assertThat(listino).doesNotContain(gruppo).contains("\"importo\":100.00").contains("\"importo\":90.00").doesNotContain("\"importo\":80.00");
        assertThat(admin.delete("/api/gruppi/" + gruppo + "?pesante=true").statusCode()).isEqualTo(404);
        assertThat(admin.get("/api/gruppi/" + gruppo + "/eliminazione").statusCode()).isEqualTo(404);

        // il listino di prova si toglie (mai venduto) e con lui la stagione
        assertThat(admin.delete("/api/listino/offerte/" + scuola).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/listino/offerte/" + adulti).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/stagioni/" + stagione).statusCode()).isEqualTo(204);
    }

    @Test
    void eliminareUnGruppoConUnaPersonaOUnPrezzoSolo() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        String stagione = creaId(admin, "/api/stagioni", "{\"nome\":\"2073/74\",\"dal\":\"2073-09-01\",\"al\":\"2074-08-31\"}");

        // un solo prezzo: singolare, e senza persone la riga delle persone non c'è
        String soloPrezzo = creaId(admin, "/api/gruppi", "{\"nome\":\"Solo prezzo\"}");
        String offerta = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Carnet ridotto","tipo":"CARNET","ingressi":10,"prezzi":[{"importo":100},{"gruppoId":"%s","importo":80}]}""".formatted(stagione, soloPrezzo));
        assertThat(json(admin.get("/api/gruppi/" + soloPrezzo + "/eliminazione"))).containsEntry("pesante", true).containsEntry("persone", 0)
            .containsEntry("prezzi", 1).containsEntry("conseguenze", List.of("1 prezzo del listino per questo gruppo sparisce (Carnet ridotto)"));
        assertThat(json(admin.delete("/api/gruppi/" + soloPrezzo))).containsEntry("detail",
            "Non si può eliminare così: «Solo prezzo» ha 1 prezzo nel listino.\nUsa di nuovo il cestino: si apre la finestra con le conseguenze.");
        assertThat(admin.delete("/api/gruppi/" + soloPrezzo + "?pesante=true").statusCode()).isEqualTo(204);
        assertThat(admin.get("/api/listino?stagione=" + stagione).body()).doesNotContain(soloPrezzo);

        // una sola persona, già dentro da ieri; con più di tre offerte i primi tre nomi e «e altre N»
        String unaPersona = creaId(admin, "/api/gruppi", "{\"nome\":\"Una persona\"}");
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Solo\",\"cognome\":\"Dentro\"}");
        orologio.alle("2028-04-10T10:00");
        creaId(segr, "/api/clienti/" + persona + "/gruppi", "{\"gruppoId\":\"" + unaPersona + "\"}");
        List<String> offerte = new ArrayList<>();
        offerte.add(offerta);
        for (int i = 1; i <= 5; i++)
            offerte.add(creaId(admin, "/api/listino/offerte", """
                {"stagioneId":"%s","nome":"Offerta %d","tipo":"ALTRO","ordine":%d,"prezzi":[{"importo":50},{"gruppoId":"%s","importo":40}]}""".formatted(stagione, i, i, unaPersona)));
        var e = json(admin.get("/api/gruppi/" + unaPersona + "/eliminazione"));
        assertThat(e).containsEntry("persone", 1).containsEntry("daVerificare", 0).containsEntry("prezzi", 5);
        assertThat(e.get("conseguenze")).isEqualTo(List.of("1 persona esce dal gruppo",
            "5 prezzi del listino per questo gruppo spariscono (Offerta 1, Offerta 2, Offerta 3 e altre 2)"));
        assertThat(json(admin.delete("/api/gruppi/" + unaPersona))).containsEntry("detail",
            "Non si può eliminare così: «Una persona» ha 1 persona e 5 prezzi nel listino.\nUsa di nuovo il cestino: si apre la finestra con le conseguenze.");
        orologio.alle("2028-04-11T10:00");
        assertThat(admin.delete("/api/gruppi/" + unaPersona + "?pesante=true").statusCode()).isEqualTo(204);
        var riga = jdbc.queryForMap("select gruppo_id, gruppo_nome, al::text as al from appartenenza_gruppo where persona_id = ?", UUID.fromString(persona));
        assertThat(riga).containsEntry("gruppo_id", null).containsEntry("gruppo_nome", "Una persona").containsEntry("al", "2028-04-10");

        // un gruppo da cui tutti sono usciti da tempo non ha conseguenze: due tocchi, e lo storico tiene il nome
        String vuoto = creaId(admin, "/api/gruppi", "{\"nome\":\"Vuoto di nuovo\"}");
        String ex = creaId(segr, "/api/clienti", "{\"nome\":\"Ex\",\"cognome\":\"Membro\"}");
        orologio.alle("2028-05-01T10:00");
        String app = creaId(segr, "/api/clienti/" + ex + "/gruppi", "{\"gruppoId\":\"" + vuoto + "\"}");
        orologio.alle("2028-05-05T10:00");
        assertThat(segr.post("/api/clienti/appartenenze/" + app + "/chiudi", "{}").statusCode()).isEqualTo(204);
        var vuotoE = json(admin.get("/api/gruppi/" + vuoto + "/eliminazione"));
        assertThat(vuotoE).containsEntry("pesante", false).containsEntry("persone", 0).containsEntry("prezzi", 0)
            .containsEntry("conseguenze", List.of()).containsEntry("offerte", List.of());
        assertThat(gruppoDelElenco(admin, vuoto)).containsEntry("eliminazionePesante", false).containsEntry("eliminabile", true);
        assertThat(admin.delete("/api/gruppi/" + vuoto).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForMap("select gruppo_id, gruppo_nome, al::text as al from appartenenza_gruppo where persona_id = ?", UUID.fromString(ex)))
            .containsEntry("gruppo_id", null).containsEntry("gruppo_nome", "Vuoto di nuovo").containsEntry("al", "2028-05-04");

        for (String o : offerte) assertThat(admin.delete("/api/listino/offerte/" + o).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/stagioni/" + stagione).statusCode()).isEqualTo(204);
    }

    @Test
    void offerteCheRestanoSenzaPrezzi() throws Exception {
        var admin = come("admin@demo.leone");
        String stagione = creaId(admin, "/api/stagioni", "{\"nome\":\"2074/75\",\"dal\":\"2074-09-01\",\"al\":\"2075-08-31\"}");
        String gruppo = creaId(admin, "/api/gruppi", "{\"nome\":\"Solo alunni prova\"}");
        String o1 = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Corso alunni","tipo":"ALTRO","ordine":1,"prezzi":[{"gruppoId":"%s","importo":60}]}""".formatted(stagione, gruppo));
        String misto = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Con base","tipo":"ALTRO","ordine":2,"prezzi":[{"importo":90},{"gruppoId":"%s","importo":70}]}""".formatted(stagione, gruppo));
        assertThat(json(admin.get("/api/gruppi/" + gruppo + "/eliminazione")).get("conseguenze")).isEqualTo(List.of(
            "2 prezzi del listino per questo gruppo spariscono (Corso alunni, Con base)",
            "L'offerta «Corso alunni» resta senza prezzi e si spegne: riaccendila dopo averle dato un prezzo."));
        String o3 = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Carnet alunni","tipo":"ALTRO","ordine":3,"prezzi":[{"gruppoId":"%s","importo":40}]}""".formatted(stagione, gruppo));
        assertThat(json(admin.get("/api/gruppi/" + gruppo + "/eliminazione")).get("conseguenze")).isEqualTo(List.of(
            "3 prezzi del listino per questo gruppo spariscono (Corso alunni, Con base, Carnet alunni)",
            "Le offerte «Corso alunni» e «Carnet alunni» restano senza prezzi e si spengono: riaccendile dopo aver dato loro un prezzo."));
        assertThat(admin.delete("/api/gruppi/" + gruppo + "?pesante=true").statusCode()).isEqualTo(204);

        // le offerte rimaste senza prezzi si sono spente; quella con il prezzo base no
        assertThat(offerta(admin, stagione, o1)).containsEntry("attiva", false);
        assertThat(offerta(admin, stagione, o3)).containsEntry("attiva", false);
        assertThat(offerta(admin, stagione, misto)).containsEntry("attiva", true);
        assertThat((List<?>) offerta(admin, stagione, o1).get("prezzi")).isEmpty();

        // il listino si copia lo stesso: le offerte senza prezzi si saltano e la risposta lo dice
        String nuova = creaId(admin, "/api/stagioni", "{\"nome\":\"2075/76\",\"dal\":\"2075-09-01\",\"al\":\"2076-08-31\"}");
        var copia = admin.post("/api/listino/copia", "{\"da\":\"" + stagione + "\",\"a\":\"" + nuova + "\"}");
        assertThat(copia.statusCode()).as(copia.body()).isEqualTo(200);
        assertThat(json(copia)).containsEntry("offerte", 1).containsEntry("saltate", List.of("Corso alunni", "Carnet alunni"));
        String copiata = (String) elenco(json(admin.get("/api/listino?stagione=" + nuova)).get("offerte")).getFirst().get("id");
        assertThat(admin.delete("/api/listino/offerte/" + copiata).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/stagioni/" + nuova).statusCode()).isEqualTo(204);

        // spegnerla dal foglio funziona anche senza prezzi; riaccenderla no, finché non ha un prezzo
        String corpo = "{\"stagioneId\":\"" + stagione + "\",\"nome\":\"Corso alunni\",\"tipo\":\"ALTRO\",\"ordine\":1,\"attiva\":%s,\"prezzi\":%s}";
        assertThat(admin.put("/api/listino/offerte/" + o1, corpo.formatted("false", "[]")).statusCode()).isEqualTo(200);
        var accesa = admin.put("/api/listino/offerte/" + o1, corpo.formatted("true", "[]"));
        assertThat(accesa.statusCode()).isEqualTo(400);
        assertThat(json(accesa)).containsEntry("detail", "Dai almeno un prezzo: un'offerta senza prezzi non si può accendere.");
        assertThat(admin.put("/api/listino/offerte/" + o1, corpo.formatted("true", "[{\"importo\":55}]")).statusCode()).isEqualTo(200);

        for (String o : List.of(o1, misto, o3)) assertThat(admin.delete("/api/listino/offerte/" + o).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/stagioni/" + stagione).statusCode()).isEqualTo(204);
    }

    private static Map<String, Object> offerta(ClientApi c, String stagione, String id) throws Exception {
        return elenco(json(c.get("/api/listino?stagione=" + stagione)).get("offerte")).stream().filter(o -> id.equals(o.get("id"))).findFirst().orElseThrow();
    }

    @Test
    void ungruppoPuoNascereSpentoESiBloccaIlNomeDellaStagione() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");
        // un gruppo può nascere spento: una chiamata sola
        String spento = creaId(admin, "/api/gruppi", "{\"nome\":\"Nato spento\",\"attivo\":false}");
        String acceso = creaId(admin, "/api/gruppi", "{\"nome\":\"Nato acceso\"}");
        assertThat(gruppoDelElenco(admin, spento)).containsEntry("attivo", false);
        assertThat(gruppoDelElenco(admin, acceso)).containsEntry("attivo", true);
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Gruppo\",\"cognome\":\"Spento\"}");
        assertThat(segr.post("/api/clienti/" + persona + "/gruppi", "{\"gruppoId\":\"" + spento + "\"}").statusCode()).isEqualTo(409);
        assertThat(admin.delete("/api/gruppi/" + spento).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/gruppi/" + acceso).statusCode()).isEqualTo(204);

        // una stagione con tesseramenti non si rinomina: il tesseramento ne tiene il nome
        String stagione = creaId(admin, "/api/stagioni", "{\"nome\":\"2090/91\",\"dal\":\"2090-09-01\",\"al\":\"2091-08-31\",\"quota\":10}");
        String corpo = "{\"nome\":\"%s\",\"dal\":\"2090-09-01\",\"al\":\"2091-08-31\",\"quota\":%s}";
        assertThat(admin.put("/api/stagioni/" + stagione, corpo.formatted("2090-91", "10")).statusCode()).isEqualTo(200);
        assertThat(admin.put("/api/stagioni/" + stagione, corpo.formatted("2090/91", "10")).statusCode()).isEqualTo(200);
        creaId(segr, "/api/clienti/" + persona + "/tesseramenti", "{\"stagione\":\"2090/91\"}");
        var rinomina = admin.put("/api/stagioni/" + stagione, corpo.formatted("2090-91", "10"));
        assertThat(rinomina.statusCode()).isEqualTo(409);
        assertThat(json(rinomina)).containsEntry("detail", "Non si può rinominare: 1 tesseramento usa il nome «2090/91».\nIl nome resta: le date e la quota si possono cambiare.");
        String altra = creaId(segr, "/api/clienti", "{\"nome\":\"Altra\",\"cognome\":\"Persona\"}");
        creaId(segr, "/api/clienti/" + altra + "/tesseramenti", "{\"stagione\":\"2090/91\"}");
        assertThat(json(admin.put("/api/stagioni/" + stagione, corpo.formatted("2090-91", "10")))).containsEntry("detail",
            "Non si può rinominare: 2 tesseramenti usano il nome «2090/91».\nIl nome resta: le date e la quota si possono cambiare.");
        // le date e la quota si cambiano (il nome è lo stesso)
        assertThat(admin.put("/api/stagioni/" + stagione, corpo.formatted("2090/91", "12")).statusCode()).isEqualTo(200);
        assertThat(stagioneDelElenco(admin, stagione)).containsEntry("quota", 12.00);
    }

    @Test
    void eliminareUnaStagione() throws Exception {
        var admin = come("admin@demo.leone");
        var segr = come("segreteria@demo.leone");

        // vuota: si può, e chi ha le impostazioni lo sa prima; gli altri non vedono né l'una né l'altra cosa
        String vuota = creaId(admin, "/api/stagioni", "{\"nome\":\"2080/81\",\"dal\":\"2080-09-01\",\"al\":\"2081-08-31\"}");
        assertThat(stagioneDelElenco(admin, vuota)).containsEntry("eliminabile", true).containsEntry("motivoBlocco", null);
        assertThat(stagioneDelElenco(segr, vuota)).containsEntry("eliminabile", null).containsEntry("motivoBlocco", null).containsEntry("nome", "2080/81");
        assertThat(segr.delete("/api/stagioni/" + vuota).statusCode()).isEqualTo(403);
        assertThat(admin.delete("/api/stagioni/" + UUID.randomUUID()).statusCode()).isEqualTo(404);

        // con delle offerte mai vendute: si svuota prima il listino
        String primaOfferta = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Quota prova","tipo":"ALTRO","prezzi":[{"importo":10}]}""".formatted(vuota));
        String blocco1 = "Non si può eliminare: la stagione 2080/81 ha 1 offerta nel listino.\nSvuota prima il listino: poi potrai eliminare la stagione.";
        var r1 = admin.delete("/api/stagioni/" + vuota);
        assertThat(r1.statusCode()).isEqualTo(409);
        assertThat(json(r1)).containsEntry("detail", blocco1);
        assertThat(stagioneDelElenco(admin, vuota)).containsEntry("eliminabile", false).containsEntry("motivoBlocco", blocco1);
        String secondaOfferta = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Carnet prova","tipo":"CARNET","ingressi":5,"prezzi":[{"importo":30}]}""".formatted(vuota));
        assertThat(json(admin.delete("/api/stagioni/" + vuota))).containsEntry("detail",
            "Non si può eliminare: la stagione 2080/81 ha 2 offerte nel listino.\nSvuota prima il listino: poi potrai eliminare la stagione.");

        // le regole di prezzo sono impostazioni della stagione: se ne vanno con lei
        assertThat(admin.put("/api/listino/regole/" + vuota + "/RATE", "{\"attiva\":true,\"parametri\":{\"numero\":3,\"primaPercentuale\":40}}").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from regola_prezzo where stagione_id = ?", Integer.class, UUID.fromString(vuota))).isEqualTo(1);
        assertThat(admin.delete("/api/listino/offerte/" + primaOfferta).statusCode()).isEqualTo(204);
        assertThat(admin.delete("/api/listino/offerte/" + secondaOfferta).statusCode()).isEqualTo(204);
        assertThat(stagioneDelElenco(admin, vuota)).containsEntry("eliminabile", true);
        assertThat(admin.delete("/api/stagioni/" + vuota).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from regola_prezzo where stagione_id = ?", Integer.class, UUID.fromString(vuota))).isZero();
        assertThat(admin.delete("/api/stagioni/" + vuota).statusCode()).isEqualTo(404);

        // usata: le vendite dal listino (abbonamenti e addebiti), i tesseramenti e gli esiti dei livelli restano nello storico
        String usata = creaId(admin, "/api/stagioni", "{\"nome\":\"2082/83\",\"dal\":\"2082-09-01\",\"al\":\"2083-08-31\",\"quota\":20}");
        String corso = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Corso storico","tipo":"CORSO","corsi":["%s"],"prezzi":[{"volte":1,"importo":300}]}""".formatted(usata, DELFINO_GIALLO));
        String altro = creaId(admin, "/api/listino/offerte", """
            {"stagioneId":"%s","nome":"Quota storica","tipo":"ALTRO","prezzi":[{"importo":15}]}""".formatted(usata));
        String persona = creaId(segr, "/api/clienti", "{\"nome\":\"Storico\",\"cognome\":\"Stagione\"}");
        creaId(segr, "/api/clienti/" + persona + "/abbonamenti", "{\"offertaId\":\"" + corso + "\",\"volteASettimana\":1,\"dal\":\"2082-10-01\"}");
        creaId(segr, "/api/pagamenti/addebiti", "{\"personaId\":\"" + persona + "\",\"descrizione\":\"Quota storica\",\"importo\":15,\"offertaId\":\"" + altro + "\"}");
        var vendite = json(admin.delete("/api/stagioni/" + usata));
        assertThat(vendite.get("detail")).isEqualTo("Non si può eliminare: la stagione 2082/83 ha 2 offerte nel listino e 2 vendite dal listino.\n"
            + "Resta nello storico: una stagione già usata non si elimina.");
        assertThat(creaId(segr, "/api/clienti/" + persona + "/tesseramenti", "{\"stagione\":\"2082/83\"}")).isNotBlank();
        jdbc.update("insert into esito_livello (id, stagione_id, persona_id, corso_id, esito, deciso_il) values (?, ?, ?, ?, 'PASSA', now())",
            UUID.randomUUID(), UUID.fromString(usata), UUID.fromString(persona), UUID.fromString(DELFINO_GIALLO));
        String storico = "Non si può eliminare: la stagione 2082/83 ha 2 offerte nel listino, 2 vendite dal listino, 1 tesseramento e 1 esito dei livelli.\n"
            + "Resta nello storico: una stagione già usata non si elimina.";
        var r2 = admin.delete("/api/stagioni/" + usata);
        assertThat(r2.statusCode()).isEqualTo(409);
        assertThat(json(r2)).containsEntry("detail", storico);
        assertThat(stagioneDelElenco(admin, usata)).containsEntry("eliminabile", false).containsEntry("motivoBlocco", storico);
        assertThat(stagioneDelElenco(segr, usata)).containsEntry("eliminabile", null).containsEntry("motivoBlocco", null);
        // niente cascata silenziosa: il listino c'è ancora
        assertThat(admin.get("/api/listino?stagione=" + usata).body()).contains("Corso storico").contains("Quota storica");
    }

    private static Map<String, Object> stagioneDelElenco(ClientApi c, String id) throws Exception {
        return elenco(lista(c.get("/api/stagioni"))).stream().filter(s -> id.equals(s.get("id"))).findFirst().orElseThrow();
    }
}
