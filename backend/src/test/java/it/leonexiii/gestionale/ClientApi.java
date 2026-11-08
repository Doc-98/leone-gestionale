package it.leonexiii.gestionale;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Un browser minimo per i test: tiene i cookie (sessione e XSRF-TOKEN) e manda il token CSRF come fa il frontend. */
final class ClientApi {

    private final String base;
    private final CookieManager cookie = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    /** La cassa di questo «computer» (intestazione X-Cassa, come la manda il frontend), o null. */
    private String cassa;

    ClientApi(int porta) {
        this.base = "http://localhost:" + porta;
        this.http = HttpClient.newBuilder().cookieHandler(cookie).build();
    }

    /** Da qui in poi le richieste dicono di venire da quella cassa (null = nessuna). */
    ClientApi dallaCassa(String cassaId) {
        this.cassa = cassaId;
        return this;
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> scarica(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        if (tokenCsrf() == null) get("/api/auth/csrf");
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).DELETE().header("X-XSRF-TOKEN", tokenCsrf()).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        return invia("POST", path, json, true);
    }

    HttpResponse<String> put(String path, String json) throws IOException, InterruptedException {
        return invia("PUT", path, json, true);
    }

    HttpResponse<String> post(String path, String json, boolean conCsrf) throws IOException, InterruptedException {
        return invia("POST", path, json, conCsrf);
    }

    private HttpResponse<String> invia(String metodo, String path, String json, boolean conCsrf) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .method(metodo, HttpRequest.BodyPublishers.ofString(json));
        if (conCsrf) {
            if (tokenCsrf() == null) get("/api/auth/csrf");
            b.header("X-XSRF-TOKEN", tokenCsrf());
        }
        if (cassa != null) b.header("X-Cassa", cassa);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Come una macchina di phon e docce: niente sessione né CSRF, solo la chiave del dispositivo (se c'è). Con il corpo
     * a {@code null} la richiesta non ha corpo.
     */
    HttpResponse<String> macchina(String metodo, String path, String chiave, String json) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
            .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) b.header("Content-Type", "application/json");
        if (chiave != null) b.header("X-Chiave-Dispositivo", chiave);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> login(String email, String password) throws IOException, InterruptedException {
        return post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", true);
    }

    String tokenCsrf() {
        return cookie.getCookieStore().getCookies().stream()
            .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElse(null);
    }
}
