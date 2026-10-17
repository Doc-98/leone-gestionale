package it.leonexiii.gestionale.identita;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AuthController {

    record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    record Me(UUID accountId, UUID personaId, String email, String nome, String cognome, List<RuoloAssegnato> ruoli) {
        static Me da(Utente u) {
            return new Me(u.accountId(), u.personaId(), u.email(), u.nome(), u.cognome(), u.ruoli());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /** Il frontend lo chiama all'avvio: imposta il cookie XSRF-TOKEN da rimandare nelle richieste che modificano dati. */
    @GetMapping("/auth/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.email().trim(), req.password()));
        } catch (AuthenticationException e) {
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email o password non corretti.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(p);
        }
        // Nuovo id di sessione dopo il login (contro la session fixation)
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext ctx = holder.createEmptyContext();
        ctx.setAuthentication(auth);
        holder.setContext(ctx);
        securityContextRepository.saveContext(ctx, request, response);
        return ResponseEntity.ok(Me.da((Utente) auth.getPrincipal()));
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal Utente utente) {
        return Me.da(utente);
    }
}
