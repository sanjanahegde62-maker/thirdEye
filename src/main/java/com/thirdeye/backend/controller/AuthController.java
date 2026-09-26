package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.AppUser;
import com.thirdeye.backend.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST endpoints for registration, login, logout, and current-user lookup.
 *
 * POST /api/auth/register  — create account
 * POST /api/auth/login     — authenticate, create session
 * POST /api/auth/logout    — invalidate session
 * GET  /api/auth/me        — return current user (requires active session)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserService      userService;
    private final AuthenticationManager authManager;

    public AuthController(AppUserService userService, AuthenticationManager authManager) {
        this.userService  = userService;
        this.authManager  = authManager;
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        String fullName        = body.getOrDefault("fullName", "").trim();
        String email           = body.getOrDefault("email", "").trim().toLowerCase();
        String password        = body.getOrDefault("password", "");
        String confirmPassword = body.getOrDefault("confirmPassword", "");

        // ── validation ────────────────────────────────────────────────────────
        if (fullName.isBlank()) {
            return bad("Full name is required.");
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            return bad("A valid email address is required.");
        }
        if (password.length() < 8) {
            return bad("Password must be at least 8 characters.");
        }
        if (!password.equals(confirmPassword)) {
            return bad("Passwords do not match.");
        }

        // ── create user ───────────────────────────────────────────────────────
        AppUser user;
        try {
            user = userService.register(fullName, email, password);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Conflict", "message", ex.getMessage()));
        }

        // ── auto-login after registration ─────────────────────────────────────
        authenticateAndSaveSession(email, password, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(userView(user));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String email    = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        try {
            authenticateAndSaveSession(email, password, request);
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Invalid email or password."));
        }

        AppUser user = userService.getByEmail(email);
        return ResponseEntity.ok(userView(user));
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<Object> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Not authenticated."));
        }
        String email = auth.getName();
        AppUser user = userService.getByEmail(email);
        return ResponseEntity.ok(userView(user));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void authenticateAndSaveSession(String email, String password,
                                             HttpServletRequest request) {
        Authentication authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Persist the security context into the HTTP session so subsequent
        // requests are recognised as authenticated.
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
    }

    private static Map<String, Object> userView(AppUser user) {
        return Map.of(
                "id",       user.getId(),
                "fullName", user.getFullName(),
                "email",    user.getEmail()
        );
    }

    private static ResponseEntity<Object> bad(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", message));
    }
}
