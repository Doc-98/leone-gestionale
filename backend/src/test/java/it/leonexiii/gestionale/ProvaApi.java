package it.leonexiii.gestionale;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * La base dei test che parlano con le API: entra con gli account demo, legge il JSON delle risposte, crea risorse.
 * Dopo ogni test l'orologio torna all'ora vera, così un test che ferma il tempo non sposta quelli dopo.
 */
@TestGestionale
abstract class ProvaApi {

    static final String PASSWORD_DEMO = "demo1234";

    @LocalServerPort
    protected int porta;

    @Autowired
    protected AmbienteDiProva.OrologioDiProva orologio;

    @AfterEach
    void tempoVero() {
        orologio.vero();
    }

    /** Un client nuovo, già entrato con l'account demo indicato. */
    protected ClientApi come(String email) throws Exception {
        return come(email, PASSWORD_DEMO);
    }

    protected ClientApi come(String email, String password) throws Exception {
        var c = new ClientApi(porta);
        var r = c.login(email, password);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return c;
    }

    /** Un client senza accesso. */
    protected ClientApi anonimo() {
        return new ClientApi(porta);
    }

    /** POST che deve creare (201): la risorsa creata. */
    protected static Map<String, Object> crea(ClientApi c, String path, String corpo) throws Exception {
        var r = c.post(path, corpo);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return json(r);
    }

    /** POST che deve creare (201): l'id della risorsa creata. */
    protected static String creaId(ClientApi c, String path, String corpo) throws Exception {
        return (String) crea(c, path, corpo).get("id");
    }

    /** L'id del metodo di pagamento con quel nome (es. «Contanti», «Borsellino»). */
    protected static String metodoPagamento(ClientApi c, String nome) throws Exception {
        return (String) lista(c.get("/api/pagamenti/metodi")).stream().map(ProvaApi::mappa)
            .filter(m -> nome.equals(m.get("nome"))).findFirst().orElseThrow().get("id");
    }

    protected static Map<String, Object> json(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseMap(r.body());
    }

    protected static List<Object> lista(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseList(r.body());
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> mappa(Object o) {
        return (Map<String, Object>) o;
    }

    /** Un elenco di oggetti dentro una risposta (es. gli addebiti di un conto). */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> elenco(Object o) {
        return (List<Map<String, Object>>) o;
    }
}
