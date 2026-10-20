package it.leonexiii.gestionale.config;

import it.leonexiii.gestionale.identita.Autorizzazioni;
import it.leonexiii.gestionale.identita.Utente;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A ogni richiesta (ADR 0012): un account disattivato perde subito la sessione (401); con una password temporanea si
 * possono solo leggere i propri dati, cambiare la password e uscire (403 sul resto).
 */
class AccountAttivoFilter extends OncePerRequestFilter {

    private static final Set<String> CON_PASSWORD_TEMPORANEA = Set.of("/api/me", "/api/me/password", "/api/auth/logout", "/api/auth/csrf");

    private final Autorizzazioni autorizzazioni;

    AccountAttivoFilter(Autorizzazioni autorizzazioni) {
        this.autorizzazioni = autorizzazioni;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utente u && req.getRequestURI().startsWith("/api/")) {
            Autorizzazioni.StatoAccount stato = autorizzazioni.stato(u);
            if (!stato.attivo()) {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                SecurityContextHolder.clearContext();
                rispondi(res, 401, "Il tuo account è stato disattivato.");
                return;
            }
            if (stato.deveCambiarePassword() && !CON_PASSWORD_TEMPORANEA.contains(req.getRequestURI())) {
                rispondi(res, 403, "Prima di continuare scegli una nuova password.");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static void rispondi(HttpServletResponse res, int stato, String messaggio) throws IOException {
        res.setStatus(stato);
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + stato + ",\"detail\":\"" + messaggio + "\"}");
    }
}
