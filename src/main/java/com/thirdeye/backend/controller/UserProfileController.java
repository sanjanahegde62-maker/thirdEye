package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.AppUser;
import com.thirdeye.backend.entity.UserProfile;
import com.thirdeye.backend.service.AppUserService;
import com.thirdeye.backend.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService service;
    private final AppUserService     userService;

    public UserProfileController(UserProfileService service, AppUserService userService) {
        this.service     = service;
        this.userService = userService;
    }

    // ── Legacy unauthenticated endpoints (kept for backward compatibility) ────

    /** GET /api/profile */
    @GetMapping
    public UserProfile getProfile() {
        return service.getProfile();
    }

    /**
     * PATCH /api/profile
     * Body: { "name": "...", "email": "..." }  (both fields optional)
     */
    @PatchMapping
    public UserProfile updateProfile(@RequestBody Map<String, String> body) {
        return service.updateProfile(body.get("name"), body.get("email"));
    }

    // ── Authenticated /me endpoints ───────────────────────────────────────────

    /** GET /api/profile/me — current user's profile */
    @GetMapping("/me")
    public ResponseEntity<Object> getMyProfile() {
        AppUser user = resolveCurrentUser();
        if (user == null) return unauthorized();
        UserProfile profile = service.getProfileByUserId(
                user.getId(), user.getFullName(), user.getEmail());
        return ResponseEntity.ok(profile);
    }

    /** PATCH /api/profile/me — update current user's profile */
    @PatchMapping("/me")
    public ResponseEntity<Object> updateMyProfile(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        AppUser user = resolveCurrentUser();
        if (user == null) return unauthorized();
        UserProfile profile = service.updateProfileByUserId(
                user.getId(), user.getFullName(), user.getEmail(),
                body.get("name"), body.get("email"));
        refreshSessionIdentity(user.getId(), request);
        return ResponseEntity.ok(profile);
    }

    private void refreshSessionIdentity(Long userId, HttpServletRequest request) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        AppUser updatedUser = userService.getById(userId);
        if (current == null || updatedUser.getEmail().equals(current.getName())) return;

        UserDetails principal = userService.loadUserByUsername(updatedUser.getEmail());
        Authentication updated = new UsernamePasswordAuthenticationToken(
                principal, null, current.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);

        var session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUser resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userService.getByEmail(auth.getName());
    }

    private static ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthorized", "message", "Authentication required."));
    }
}
