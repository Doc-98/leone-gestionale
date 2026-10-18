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

    ClientApi(int porta) {
        this.base = "http://localhost:" + porta;
        this.http = HttpClient.newBuilder().cookieHandler(cookie).build();
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
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
