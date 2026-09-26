package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.AppUser;
import com.thirdeye.backend.entity.NotificationPreferences;
import com.thirdeye.backend.entity.UserProfile;
import com.thirdeye.backend.service.AppUserService;
import com.thirdeye.backend.service.NotificationPreferencesService;
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

/**
 * Settings endpoint.
 *
 * GET  /api/settings              — returns combined { profile, preferences }
 * PATCH /api/settings/profile     — updates profile name/email
 * PATCH /api/settings/preferences — updates notification preferences
 *
 * All endpoints require an authenticated session.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AppUserService                userService;
    private final UserProfileService            profileService;
    private final NotificationPreferencesService prefsService;

    public SettingsController(AppUserService userService,
                              UserProfileService profileService,
                              NotificationPreferencesService prefsService) {
        this.userService    = userService;
        this.profileService = profileService;
        this.prefsService   = prefsService;
    }

    /** GET /api/settings — combined profile + preferences */
    @GetMapping
    public ResponseEntity<Object> getSettings() {
        AppUser user = resolveCurrentUser();
        if (user == null) return unauthorized();

        UserProfile            profile = profileService.getProfileByUserId(
                user.getId(), user.getFullName(), user.getEmail());
        NotificationPreferences prefs  = prefsService.getPrefs(user.getId());

        return ResponseEntity.ok(Map.of("profile", profile, "preferences", prefs));
    }

    /** PATCH /api/settings/profile — update name / email */
    @PatchMapping("/profile")
    public ResponseEntity<Object> updateProfile(@RequestBody Map<String, String> body,
                                                HttpServletRequest request) {
        AppUser user = resolveCurrentUser();
        if (user == null) return unauthorized();

        UserProfile profile = profileService.updateProfileByUserId(
                user.getId(), user.getFullName(), user.getEmail(),
                body.get("name"), body.get("email"));
        refreshSessionIdentity(user.getId(), request);
        return ResponseEntity.ok(profile);
    }

    /** Keep the active session's principal in sync when the account email changes. */
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

    /** PATCH /api/settings/preferences — update notification preferences */
    @PatchMapping("/preferences")
    public ResponseEntity<Object> updatePreferences(@RequestBody Map<String, Object> body) {
        AppUser user = resolveCurrentUser();
        if (user == null) return unauthorized();

        Boolean reviewCompleted = getBool(body, "reviewCompleted");
        Boolean newFindings     = getBool(body, "newFindings");
        Boolean testsGenerated  = getBool(body, "testsGenerated");

        NotificationPreferences prefs = prefsService.updatePrefs(
                user.getId(), reviewCompleted, newFindings, testsGenerated);
        return ResponseEntity.ok(prefs);
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

    private static Boolean getBool(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}
