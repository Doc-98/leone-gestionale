package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Il tornello: tutti i motivi del rosso insieme, con il nome della persona; fascia oraria configurabile; più
 * braccialetti; ban del braccialetto o della persona; smarrito; golden pass; carnet con rientro. Il tempo è fermato
 * su domenica 2 gennaio 2028 (la domenica è libera nei dati demo).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TornelloTest.Tempo.class})
@ActiveProfiles("demo")
class TornelloTest {

    static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    static final String IMPIANTO = "00000000-0000-0000-0000-0000000000b1";
    static final String VASCA_PICCOLA = "00000000-0000-0000-0000-0000000000c2";

    /** Un orologio che i test spostano a piacere. */
    static final class OrologioDiProva extends Clock {
        private volatile Instant adesso = Instant.now();

        void alle(String quando) {
            adesso = LocalDateTime.parse(quando).atZone(ROMA).toInstant();
        }

        @Override public ZoneId getZone() { return ROMA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return adesso; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Tempo {
        @Bean
        @Primary
        OrologioDiProva orologioDiProva() {
            return new OrologioDiProva();
        }
    }

    @LocalServerPort
    int porta;

    @Autowired
    OrologioDiProva orologio;

    private ClientApi come(String email) throws Exception {
        var c = new ClientApi(porta);
        assertThat(c.login(email, "demo1234").statusCode()).isEqualTo(200);
        return c;
    }

    private static Map<String, Object> json(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseMap(r.body());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> motivi(Map<String, Object> esito) {
        return (List<Map<String, Object>>) esito.get("motivi");
    }

    private static List<Object> codici(Map<String, Object> esito) {
        return motivi(esito).stream().map(m -> m.get("codice")).toList();
    }

    private static String nuovoCodice() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private Map<String, Object> passa(ClientApi c, String quando, String codice) throws Exception {
        orologio.alle(quando);
        var r = c.post("/api/accessi/passaggi", "{\"codice\":\"" + codice + "\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return json(r);
    }

    private String crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return (String) json(r).get("id");
    }

    private String cliente(ClientApi segr, String nome) throws Exception {
        return crea(segr, "/api/clienti", "{\"nome\":\"" + nome + "\",\"cognome\":\"Prova\",\"dataNascita\":\"2019-03-10\"}");
    }

    @Test
    void ilTornelloMostraTuttiIMotiviEIlNome() throws Exception {
        var segr = come("segreteria@demo.leone");
        var staff = come("staff@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var admin = come("admin@demo.leone");
        orologio.alle("2028-01-02T06:00");

        String persona = cliente(segr, "Arturo");
        String primo = nuovoCodice();
        String secondo = nuovoCodice();
        // il codice si normalizza: «ab:cd:…» diventa «ABCD…»
        var b1 = segr.post("/api/accessi/persone/" + persona + "/braccialetti",
            "{\"codice\":\"" + primo.substring(0, 2).toLowerCase() + ":" + primo.substring(2) + "\"}");
        assertThat(b1.statusCode()).as(b1.body()).isEqualTo(201);
        String idPrimo = (String) json(b1).get("id");
        assertThat(json(b1).get("codice")).isEqualTo(primo);
        String idSecondo = crea(segr, "/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + secondo + "\"}");
        assertThat(segr.post("/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + secondo + "\"}").statusCode()).isEqualTo(409);

        // niente certificato, tesseramento, abbonamento: tre motivi insieme, e il nome di chi porta il braccialetto
        var e = passa(staff, "2028-01-02T06:00", primo);
        assertThat(e.get("verde")).isEqualTo(false);
        assertThat(codici(e)).containsExactly("CERTIFICATO_MANCANTE", "TESSERAMENTO_MANCANTE", "ABBONAMENTO_MANCANTE");
        @SuppressWarnings("unchecked") var p = (Map<String, Object>) e.get("persona");
        assertThat(p.get("nome")).isEqualTo("Arturo");
        assertThat(p.get("eta")).isEqualTo(8);
        assertThat(p.get("braccialettiAttivi")).isEqualTo(2);

        // certificato scaduto ieri: blocca; poi quello nuovo
        crea(segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-01-01\"}");
        assertThat(codici(passa(staff, "2028-01-02T06:00", primo))).contains("CERTIFICATO_SCADUTO");
        crea(segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-01-02\"}");
        assertThat(codici(passa(staff, "2028-01-02T06:00", primo))).doesNotContain("CERTIFICATO_SCADUTO", "CERTIFICATO_MANCANTE");

        // tesseramento senza dati: la stagione in corso (impostata dalla società), 2027/28
        crea(segr, "/api/clienti/" + persona + "/tesseramenti", "{}");
        assertThat(segr.post("/api/clienti/" + persona + "/tesseramenti", "{}").statusCode()).isEqualTo(409);
        var scheda = json(segr.get("/api/clienti/" + persona));
        assertThat(scheda.toString()).contains("stagione=2027/28").contains("dal=2027-09-01").contains("al=2028-08-31");

        // un turno la domenica alle 06:00-06:45, iscrizione e abbonamento al corso
        String corso = crea(coord, "/api/corsi", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"Corso del tornello\",\"tipo\":\"ALTRO\"}");
        var l = coord.post("/api/corsi/" + corso + "/lezioni", """
            {"giornoSettimana":7,"inizio":"06:00","fine":"06:45","validaDal":"2028-01-01","spazi":["%s"]}""".formatted(VASCA_PICCOLA));
        assertThat(l.statusCode()).as(l.body()).isEqualTo(201);
        @SuppressWarnings("unchecked") String lezione = (String) ((Map<String, Object>) json(l).get("valore")).get("id");
        assertThat(segr.post("/api/lezioni/" + lezione + "/iscritti", "{\"personaId\":\"" + persona + "\",\"dal\":\"2028-01-01\"}").statusCode()).isEqualTo(201);
        crea(segr, "/api/clienti/" + persona + "/abbonamenti",
            "{\"tipo\":\"CORSO\",\"corsoId\":\"" + corso + "\",\"volteASettimana\":1,\"dal\":\"2027-09-01\",\"al\":\"2028-06-30\"}");

        // fascia: da 10 minuti prima dell'inizio fino alla fine
        var presto = passa(staff, "2028-01-02T05:49", primo);
        assertThat(codici(presto)).containsExactly("FUORI_ORARIO");
        assertThat(motivi(presto).getFirst().get("messaggio").toString()).contains("si entra dalle 05:50");
        assertThat(passa(staff, "2028-01-02T05:50", primo).get("verde")).isEqualTo(true);
        assertThat(passa(staff, "2028-01-02T06:45", primo).get("verde")).isEqualTo(true);
        assertThat(motivi(passa(staff, "2028-01-02T06:46", primo)).getFirst().get("messaggio").toString()).contains("fino alle 06:45");

        // la chiusura si cambia dalle regole (solo admin): fino a 15 minuti dopo l'inizio
        assertThat(coord.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":true,\"parametri\":{\"minutiDopoInizio\":15}}").statusCode()).isEqualTo(403);
        assertThat(admin.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":true,\"parametri\":{\"minutiPrima\":10,\"minutiDopoInizio\":15}}").statusCode()).isEqualTo(204);
        assertThat(codici(passa(staff, "2028-01-02T06:20", primo))).containsExactly("FUORI_ORARIO");
        assertThat(admin.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":true,\"parametri\":{\"minutiDopoInizio\":null}}").statusCode()).isEqualTo(204);
        assertThat(passa(staff, "2028-01-02T06:20", primo).get("verde")).isEqualTo(true);
        assertThat(admin.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":true,\"parametri\":{\"minutiPrima\":-1}}").statusCode()).isEqualTo(400);
        // una regola spenta non conta
        assertThat(admin.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":false}").statusCode()).isEqualTo(204);
        assertThat(passa(staff, "2028-01-02T12:00", primo).get("verde")).isEqualTo(true);
        assertThat(admin.put("/api/accessi/regole/FASCIA_ORARIA", "{\"attiva\":true}").statusCode()).isEqualTo(204);
        assertThat(coord.get("/api/accessi/regole").body()).contains("\"codice\":\"FASCIA_ORARIA\"").contains("\"minutiPrima\"");

        // ban del singolo braccialetto: l'altro funziona
        String banB = crea(segr, "/api/accessi/ban", "{\"braccialettoId\":\"" + idPrimo + "\",\"motivo\":\"Ceduto a un'altra persona\"}");
        var bannato = passa(staff, "2028-01-02T06:00", primo);
        assertThat(codici(bannato)).containsExactly("BRACCIALETTO_BANNATO");
        assertThat(motivi(bannato).getFirst().get("messaggio").toString()).contains("Ceduto a un'altra persona");
        assertThat(passa(staff, "2028-01-02T06:00", secondo).get("verde")).isEqualTo(true);
        // ban della persona: tutti i suoi braccialetti
        String banP = crea(segr, "/api/accessi/ban", "{\"personaId\":\"" + persona + "\",\"motivo\":\"Comportamento scorretto\",\"al\":\"2028-01-31\"}");
        assertThat(codici(passa(staff, "2028-01-02T06:00", secondo))).containsExactly("PERSONA_BANNATA");
        assertThat(codici(passa(staff, "2028-01-02T06:00", primo))).containsExactly("BRACCIALETTO_BANNATO", "PERSONA_BANNATA");
        assertThat(segr.post("/api/accessi/ban/" + banB + "/revoca", "{}").statusCode()).isEqualTo(204);
        assertThat(segr.post("/api/accessi/ban/" + banP + "/revoca", "{}").statusCode()).isEqualTo(204);
        assertThat(passa(staff, "2028-01-02T06:00", primo).get("verde")).isEqualTo(true);

        // smarrito: rosso con il nome; l'altro braccialetto va; ritrovato torna verde
        assertThat(segr.put("/api/accessi/braccialetti/" + idPrimo + "/stato", "{\"stato\":\"SMARRITO\"}").statusCode()).isEqualTo(200);
        var smarrito = passa(staff, "2028-01-02T06:00", primo);
        assertThat(codici(smarrito)).containsExactly("BRACCIALETTO_SMARRITO");
        assertThat(smarrito.get("statoBraccialetto")).isEqualTo("SMARRITO");
        assertThat(motivi(smarrito).getFirst().get("rimedio").toString()).contains("un altro braccialetto attivo");
        assertThat(passa(staff, "2028-01-02T06:00", secondo).get("verde")).isEqualTo(true);

        // golden pass: verde anche fuori orario e senza certificato valido, ma il braccialetto smarrito resta rosso
        assertThat(staff.post("/api/accessi/persone/" + persona + "/golden-pass", "{}").statusCode()).isEqualTo(403);
        String gp = crea(coord, "/api/accessi/persone/" + persona + "/golden-pass", "{\"motivo\":\"Prova\"}");
        var dorato = passa(staff, "2028-01-03T21:00", secondo);
        assertThat(dorato.get("verde")).isEqualTo(true);
        assertThat(dorato.get("goldenPass")).isEqualTo(true);
        assertThat(codici(passa(staff, "2028-01-02T21:00", primo))).containsExactly("BRACCIALETTO_SMARRITO");
        assertThat(coord.post("/api/accessi/golden-pass/" + gp + "/revoca", "{}").statusCode()).isEqualTo(204);
        assertThat(codici(passa(staff, "2028-01-03T21:00", secondo))).containsExactly("CERTIFICATO_SCADUTO", "NESSUN_TURNO_OGGI");

        // disattivato: il codice si può dare a un'altra persona
        assertThat(segr.put("/api/accessi/braccialetti/" + idPrimo + "/stato", "{\"stato\":\"DISATTIVATO\"}").statusCode()).isEqualTo(200);
        assertThat(codici(passa(staff, "2028-01-02T06:00", primo))).containsExactly("BRACCIALETTO_DISATTIVATO");
        String altra = cliente(segr, "Bruna");
        assertThat(segr.post("/api/accessi/persone/" + altra + "/braccialetti", "{\"codice\":\"" + primo + "\"}").statusCode()).isEqualTo(201);
        @SuppressWarnings("unchecked") var ora = (Map<String, Object>) passa(staff, "2028-01-02T06:00", primo).get("persona");
        assertThat(ora.get("nome")).isEqualTo("Bruna");
        assertThat(segr.put("/api/accessi/braccialetti/" + idPrimo + "/stato", "{\"stato\":\"ATTIVO\"}").statusCode()).isEqualTo(409);
        assertThat(idSecondo).isNotNull();

        // lo stesso seriale in decimale a 10 cifre (gestionale attuale, lettori fissi) e in esadecimale (telefono)
        String decimale = cliente(segr, "Decimo");
        var dec = segr.post("/api/accessi/persone/" + decimale + "/braccialetti", "{\"codice\":\"0983503556\"}");
        assertThat(dec.statusCode()).as(dec.body()).isEqualTo(201);
        assertThat(json(dec).get("codice")).isEqualTo("3A9F12C4");
        @SuppressWarnings("unchecked") var chi = (Map<String, Object>) passa(staff, "2028-01-02T06:00", "3a:9f:12:c4").get("persona");
        assertThat(chi.get("nome")).isEqualTo("Decimo");
        assertThat(segr.get("/api/accessi/persone/" + decimale).body()).contains("\"decimale\":\"0983503556\"");

        // sconosciuto; la scheda accessi non è per lo staff; lo storico dei passaggi sì
        assertThat(codici(passa(staff, "2028-01-02T06:00", "FFFF0000FFFF"))).containsExactly("BRACCIALETTO_SCONOSCIUTO");
        assertThat(staff.post("/api/accessi/passaggi", "{\"codice\":\"x\"}").statusCode()).isEqualTo(400);
        assertThat(staff.get("/api/accessi/persone/" + persona).statusCode()).isEqualTo(403);
        assertThat(staff.get("/api/clienti/" + persona).statusCode()).isEqualTo(403);
        assertThat(staff.get("/api/accessi/passaggi?quanti=100").body()).contains("FFFF0000FFFF").contains("Bruna");
        var acc = segr.get("/api/accessi/persone/" + persona);
        assertThat(acc.statusCode()).isEqualTo(200);
        assertThat(acc.body()).contains("\"stato\":\"DISATTIVATO\"").contains("Comportamento scorretto").contains("\"adesso\"");
        assertThat(segr.get("/api/clienti/" + persona).body()).contains("Corso del tornello").contains("\"volteASettimana\":1");
    }

    @Test
    void ilCarnetScalaUnaVoltaEPerdonaIlRientro() throws Exception {
        var segr = come("segreteria@demo.leone");
        var staff = come("giulia@demo.leone");
        orologio.alle("2028-01-02T11:00");
        String persona = cliente(segr, "Carla");
        String codice = nuovoCodice();
        crea(segr, "/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}");
        crea(segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-12-31\"}");
        crea(segr, "/api/clienti/" + persona + "/tesseramenti", "{}");
        crea(segr, "/api/clienti/" + persona + "/abbonamenti", "{\"tipo\":\"CARNET\",\"ingressi\":2,\"dal\":\"2028-01-01\"}");

        // il carnet non ha fascia oraria: primo passaggio, scala
        var e1 = passa(staff, "2028-01-02T12:00", codice);
        assertThat(e1.get("verde")).isEqualTo(true);
        assertThat(e1.get("carnet").toString()).contains("usati=1").contains("scalato=true");
        // esce e rientra entro 90 minuti: non scala
        var e2 = passa(staff, "2028-01-02T13:29", codice);
        assertThat(e2.get("carnet").toString()).contains("usati=1").contains("rientro=true").contains("scalato=false");
        // dopo 90 minuti dal primo: secondo ingresso
        assertThat(passa(staff, "2028-01-02T13:30", codice).get("carnet").toString()).contains("usati=2").contains("scalato=true");
        // rientro dopo l'ultimo ingresso: passa anche se il carnet ora è finito
        assertThat(passa(staff, "2028-01-02T14:00", codice).get("verde")).isEqualTo(true);
        // più tardi: esaurito
        var fine = passa(staff, "2028-01-02T17:00", codice);
        assertThat(fine.get("verde")).isEqualTo(false);
        assertThat(codici(fine)).containsExactly("CARNET_ESAURITO");
        assertThat(motivi(fine).getFirst().get("messaggio").toString()).contains("2 ingressi su 2");
        assertThat(segr.get("/api/clienti/" + persona).body()).contains("\"usati\":2").contains("\"residui\":0");
    }

    @Test
    void ilCarnetAOrarioValeSoloNegliOrariDelSuoCorso() throws Exception {
        var segr = come("segreteria@demo.leone");
        var coord = come("coordinatore@demo.leone");
        var staff = come("marta@demo.leone");
        orologio.alle("2028-01-02T09:00");
        // un «nuoto libero» della domenica 12:00-14:00
        String corso = crea(coord, "/api/corsi", "{\"impiantoId\":\"" + IMPIANTO + "\",\"nome\":\"Libero domenicale\",\"tipo\":\"NUOTO_LIBERO\"}");
        var l = coord.post("/api/corsi/" + corso + "/lezioni", """
            {"giornoSettimana":7,"inizio":"12:00","fine":"14:00","validaDal":"2028-01-01","spazi":["%s"]}""".formatted(VASCA_PICCOLA));
        assertThat(l.statusCode()).as(l.body()).isEqualTo(201);

        String persona = cliente(segr, "Dario");
        String codice = nuovoCodice();
        crea(segr, "/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}");
        crea(segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-12-31\"}");
        crea(segr, "/api/clienti/" + persona + "/tesseramenti", "{}");
        crea(segr, "/api/clienti/" + persona + "/abbonamenti", "{\"tipo\":\"CARNET\",\"ingressi\":5,\"corsoId\":\"" + corso + "\",\"dal\":\"2028-01-01\"}");

        // prima delle 11:50 no; nella fascia sì e scala; il lunedì il corso non c'è
        var presto = passa(staff, "2028-01-02T11:00", codice);
        assertThat(codici(presto)).containsExactly("FUORI_ORARIO");
        assertThat(motivi(presto).getFirst().get("messaggio").toString()).contains("Libero domenicale 12:00–14:00").contains("dalle 11:50");
        var dentro = passa(staff, "2028-01-02T11:50", codice);
        assertThat(dentro.get("verde")).isEqualTo(true);
        assertThat(dentro.get("carnet").toString()).contains("scalato=true");
        assertThat(motivi(passa(staff, "2028-01-03T12:30", codice)).getFirst().get("messaggio").toString()).contains("che oggi non c'è");
        // a entrata libera, invece, a qualsiasi ora
        String altro = cliente(segr, "Elio");
        String codice2 = nuovoCodice();
        crea(segr, "/api/accessi/persone/" + altro + "/braccialetti", "{\"codice\":\"" + codice2 + "\"}");
        crea(segr, "/api/clienti/" + altro + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-12-31\"}");
        crea(segr, "/api/clienti/" + altro + "/tesseramenti", "{}");
        crea(segr, "/api/clienti/" + altro + "/abbonamenti", "{\"tipo\":\"CARNET\",\"ingressi\":5,\"dal\":\"2028-01-01\"}");
        assertThat(passa(staff, "2028-01-03T21:00", codice2).get("verde")).isEqualTo(true);
    }

    private String clienteInRegola(ClientiDemo d, String nome, String nascita) throws Exception {
        String persona = crea(d.segr, "/api/clienti", "{\"nome\":\"" + nome + "\",\"cognome\":\"Prova\",\"dataNascita\":\"" + nascita + "\"}");
        crea(d.segr, "/api/clienti/" + persona + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-12-31\"}");
        crea(d.segr, "/api/clienti/" + persona + "/tesseramenti", "{}");
        return persona;
    }

    private record ClientiDemo(ClientApi segr, ClientApi staff, ClientApi admin) {}

    private String braccialetto(ClientApi segr, String persona) throws Exception {
        String codice = nuovoCodice();
        crea(segr, "/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}");
        return codice;
    }

    private Map<String, Object> esci(ClientApi c, String quando, String codice) throws Exception {
        orologio.alle(quando);
        var r = c.post("/api/accessi/passaggi", "{\"codice\":\"" + codice + "\",\"verso\":\"USCITA\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return json(r);
    }

    @Test
    void sottoControlloCooldownUscitaProvvisorioESospensioni() throws Exception {
        var d = new ClientiDemo(come("segreteria@demo.leone"), come("staff@demo.leone"), come("admin@demo.leone"));
        orologio.alle("2028-01-02T09:00");

        // sotto controllo: entra, ma con un avviso, e la segreteria lo vede tra le segnalazioni
        String adulto = clienteInRegola(d, "Guido", "1980-05-05");
        crea(d.segr, "/api/clienti/" + adulto + "/abbonamenti", "{\"tipo\":\"CARNET\",\"ingressi\":20,\"dal\":\"2028-01-01\"}");
        String cod = braccialetto(d.segr, adulto);
        crea(d.segr, "/api/accessi/ban", "{\"tipo\":\"SORVEGLIANZA\",\"personaId\":\"" + adulto + "\",\"motivo\":\"Verificare con chi entra\"}");
        var e = passa(d.staff, "2028-01-02T10:00", cod);
        assertThat(e.get("verde")).isEqualTo(true);
        assertThat(codici(e)).containsExactly("PERSONA_SOTTO_CONTROLLO");
        assertThat(motivi(e).getFirst().get("blocca")).isEqualTo(false);
        assertThat(d.segr.get("/api/accessi/segnalazioni?dopo=2028-01-02T08:00:00Z").body()).contains("Guido").contains("\"segnalato\":true");
        assertThat(d.staff.get("/api/accessi/segnalazioni").statusCode()).isEqualTo(403);

        // cooldown (spento di base): acceso a 5 minuti, il braccialetto non ripassa subito; l'accompagnatore sì
        assertThat(d.admin.put("/api/accessi/regole/RIPASSAGGIO", "{\"attiva\":true,\"parametri\":{\"minuti\":5}}").statusCode()).isEqualTo(204);
        try {
            var subito = passa(d.staff, "2028-01-02T10:02", cod);
            assertThat(codici(subito)).contains("PASSAGGIO_RIPETUTO");
            assertThat(motivi(subito).stream().filter(m -> m.get("codice").equals("PASSAGGIO_RIPETUTO")).findFirst().orElseThrow().get("messaggio").toString())
                .contains("alle 10:00").contains("dalle 10:05");
            assertThat(passa(d.staff, "2028-01-02T10:05", cod).get("verde")).isEqualTo(true);
            crea(d.segr, "/api/accessi/persone/" + adulto + "/autorizzazioni", "{\"tipo\":\"ACCOMPAGNATORE\"}");
            assertThat(passa(d.staff, "2028-01-02T10:06", cod).get("verde")).isEqualTo(true);
        } finally {
            assertThat(d.admin.put("/api/accessi/regole/RIPASSAGGIO", "{\"attiva\":false}").statusCode()).isEqualTo(204);
        }

        // uscita (spenta di base): acceso, il minorenne esce solo con il permesso firmato; l'adulto sempre
        String bambino = crea(d.segr, "/api/clienti", "{\"nome\":\"Ivo\",\"cognome\":\"Prova\",\"dataNascita\":\"2016-04-04\"}");
        String codB = braccialetto(d.segr, bambino);
        assertThat(esci(d.staff, "2028-01-02T11:00", codB).get("verde")).isEqualTo(true); // regola spenta
        assertThat(d.admin.put("/api/accessi/regole/USCITA", "{\"attiva\":true}").statusCode()).isEqualTo(204);
        try {
            var fuori = esci(d.staff, "2028-01-02T11:00", codB);
            assertThat(codici(fuori)).containsExactly("USCITA_NON_AUTORIZZATA"); // all'uscita non contano certificato e abbonamento
            assertThat(fuori.get("verso")).isEqualTo("USCITA");
            assertThat(d.segr.post("/api/accessi/persone/" + bambino + "/autorizzazioni", "{\"tipo\":\"USCITA_AUTONOMA\"}").statusCode()).isEqualTo(400);
            crea(d.segr, "/api/accessi/persone/" + bambino + "/autorizzazioni",
                "{\"tipo\":\"USCITA_AUTONOMA\",\"firmataDa\":\"Un genitore\",\"firmataIl\":\"2028-01-01\"}");
            assertThat(esci(d.staff, "2028-01-02T11:01", codB).get("verde")).isEqualTo(true);
            assertThat(esci(d.staff, "2028-01-02T11:01", cod).get("verde")).isEqualTo(true);
        } finally {
            assertThat(d.admin.put("/api/accessi/regole/USCITA", "{\"attiva\":false}").statusCode()).isEqualTo(204);
        }

        // certificato provvisorio: firmato il 1/1, vale al massimo 30 giorni (fino al 30/1), con un avviso
        String nuovo = crea(d.segr, "/api/clienti", "{\"nome\":\"Lia\",\"cognome\":\"Prova\",\"dataNascita\":\"1990-01-01\"}");
        crea(d.segr, "/api/clienti/" + nuovo + "/tesseramenti", "{}");
        String abb = crea(d.segr, "/api/clienti/" + nuovo + "/abbonamenti", "{\"tipo\":\"CARNET\",\"ingressi\":20,\"dal\":\"2028-01-01\",\"al\":\"2028-02-29\"}");
        String codL = braccialetto(d.segr, nuovo);
        assertThat(d.segr.post("/api/clienti/" + nuovo + "/certificati", "{\"tipo\":\"PROVVISORIO\",\"rilasciatoIl\":\"2028-01-01\",\"scadenza\":\"2028-03-01\"}").statusCode())
            .isEqualTo(400); // manca chi l'ha firmato
        crea(d.segr, "/api/clienti/" + nuovo + "/certificati",
            "{\"tipo\":\"PROVVISORIO\",\"rilasciatoIl\":\"2028-01-01\",\"scadenza\":\"2028-03-01\",\"firmatoDa\":\"Lia Prova\"}");
        var prov = passa(d.staff, "2028-01-02T12:00", codL);
        assertThat(prov.get("verde")).isEqualTo(true);
        assertThat(codici(prov)).containsExactly("CERTIFICATO_PROVVISORIO");
        assertThat(motivi(prov).getFirst().get("messaggio").toString()).contains("30/01/2028");
        assertThat(codici(passa(d.staff, "2028-01-31T12:00", codL))).containsExactly("CERTIFICATO_SCADUTO");

        // sospensione: nei giorni sospesi non vale, la scadenza si allunga di altrettanti giorni
        crea(d.segr, "/api/clienti/" + nuovo + "/certificati", "{\"tipo\":\"NON_AGONISTICO\",\"scadenza\":\"2028-12-31\"}");
        crea(d.segr, "/api/clienti/abbonamenti/" + abb + "/sospensioni", "{\"dal\":\"2028-01-10\",\"al\":\"2028-01-19\",\"motivo\":\"Infortunio\"}");
        assertThat(d.segr.post("/api/clienti/abbonamenti/" + abb + "/sospensioni", "{\"dal\":\"2028-01-15\",\"al\":\"2028-01-20\"}").statusCode()).isEqualTo(409);
        var sospeso = passa(d.staff, "2028-01-12T12:00", codL);
        assertThat(codici(sospeso)).containsExactly("ABBONAMENTO_SOSPESO");
        assertThat(motivi(sospeso).getFirst().get("messaggio").toString()).contains("Infortunio");
        assertThat(passa(d.staff, "2028-03-05T12:00", codL).get("verde")).isEqualTo(true); // 29/2 + 10 giorni = 10/3
        assertThat(codici(passa(d.staff, "2028-03-11T12:00", codL))).containsExactly("ABBONAMENTO_SCADUTO");
        assertThat(d.segr.get("/api/clienti/" + nuovo).body()).contains("\"alEffettivo\":\"2028-03-10\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void laGiornataAlBancoContaIFermatiEISistemati() throws Exception {
        var segr = come("segreteria@demo.leone");
        var staff = come("staff@demo.leone");
        var coord = come("coordinatore@demo.leone");
        orologio.alle("2028-06-11T08:00"); // una domenica in cui nessun altro test passa dal tornello

        String persona = crea(segr, "/api/clienti", "{\"nome\":\"Ornella\",\"cognome\":\"Banco\",\"dataNascita\":\"1990-05-01\"}");
        String codice = nuovoCodice();
        crea(segr, "/api/accessi/persone/" + persona + "/braccialetti", "{\"codice\":\"" + codice + "\"}");
        String sconosciuto = nuovoCodice();
        // passando il braccialetto nella ricerca dei clienti si apre la sua scheda
        assertThat(json(segr.get("/api/accessi/braccialetti?codice=" + codice)).get("personaId")).isEqualTo(persona);
        assertThat(json(segr.get("/api/accessi/braccialetti?codice=" + sconosciuto)).get("personaId")).isNull();
        assertThat(staff.get("/api/accessi/braccialetti?codice=" + codice).statusCode()).isEqualTo(403);

        // due rossi di fila e un braccialetto che non è di nessuno: tre passaggi, due «da sistemare»
        assertThat(passa(staff, "2028-06-11T09:00", codice).get("verde")).isEqualTo(false);
        assertThat(passa(staff, "2028-06-11T09:05", codice).get("verde")).isEqualTo(false);
        assertThat(passa(staff, "2028-06-11T09:06", sconosciuto).get("verde")).isEqualTo(false);
        var g = json(staff.get("/api/accessi/giornata"));
        var fuori = (List<Map<String, Object>>) g.get("daSistemare");
        assertThat(fuori).hasSize(2);
        assertThat(fuori.get(0).get("codice")).isEqualTo(sconosciuto); // il più recente per primo
        assertThat(fuori.get(0).get("persona")).isNull();
        assertThat(fuori.get(1).get("volte")).isEqualTo(2);
        assertThat((String) fuori.get(1).get("primaVolta")).startsWith("2028-06-11T07:00"); // le 9 a Roma, in UTC
        assertThat(((Map<String, Object>) fuori.get(1).get("persona")).get("nome")).isEqualTo("Ornella");
        assertThat((List<?>) g.get("linea")).hasSize(3);
        assertThat(((Map<String, Object>) g.get("conteggi")).get("fermati")).isEqualTo(3);

        // con il golden pass passa in verde: non è più da sistemare, è tra i sistemati
        crea(coord, "/api/accessi/persone/" + persona + "/golden-pass", "{\"motivo\":\"Prova del banco\"}");
        assertThat(passa(staff, "2028-06-11T09:10", codice).get("verde")).isEqualTo(true);
        g = json(staff.get("/api/accessi/giornata"));
        assertThat((List<?>) g.get("daSistemare")).hasSize(1);
        var sistemati = (List<Map<String, Object>>) g.get("sistemati");
        assertThat(sistemati).hasSize(1);
        assertThat((String) sistemati.get(0).get("fermatoIl")).startsWith("2028-06-11T07:05");
        assertThat((List<?>) sistemati.get(0).get("motivi")).isNotEmpty();
        var conti = (Map<String, Object>) g.get("conteggi");
        assertThat(conti.get("entrati")).isEqualTo(1);
        assertThat(conti.get("dentro")).isNull(); // nessuno è passato dall'uscita: non si sa chi è dentro

        // all'uscita: dentro non c'è più nessuno
        assertThat(esci(staff, "2028-06-11T11:00", codice).get("verde")).isEqualTo(true);
        conti = (Map<String, Object>) json(staff.get("/api/accessi/giornata")).get("conteggi");
        assertThat(conti.get("dentro")).isEqualTo(0);
        assertThat(conti.get("uscite")).isEqualTo(1);
    }
}
