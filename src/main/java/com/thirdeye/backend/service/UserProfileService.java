package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.UserProfile;
import com.thirdeye.backend.entity.AppUser;
import com.thirdeye.backend.repository.AppUserRepository;
import com.thirdeye.backend.repository.UserProfileRepository;
import com.thirdeye.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Manages user profiles.
 *
 * Legacy API (getProfile / updateProfile): operates on the first row with
 * userId=null — backward-compatible with ProfileAndNotificationTests.
 *
 * New API (getProfileByUserId / updateProfileByUserId): one row per
 * authenticated user, identified by userId.
 */
@Service
public class UserProfileService {

    private static final String DEFAULT_NAME  = "ThirdEye User";
    private static final String DEFAULT_EMAIL = "user@thirdeye.local";
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserProfileRepository repo;
    private final AppUserRepository userRepo;

    public UserProfileService(UserProfileRepository repo, AppUserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    // ── Legacy API (unauthenticated /api/profile) ─────────────────────────────

    /**
     * Returns the legacy (userId=null) profile, creating it with defaults if
     * it does not exist yet.
     */
    @Transactional
    public UserProfile getProfile() {
        return repo.findByUserId(null)
                .orElseGet(() -> repo.save(new UserProfile(DEFAULT_NAME, DEFAULT_EMAIL)));
    }

    /**
     * Updates name and/or email on the legacy profile.
     * Null / blank fields are ignored (PATCH semantics).
     */
    @Transactional
    public UserProfile updateProfile(String name, String email) {
        UserProfile profile = getProfile();
        if (name  != null && !name.isBlank())  profile.setName(name.trim());
        if (email != null && !email.isBlank()) profile.setEmail(email.trim());
        return repo.save(profile);
    }

    // ── New API (authenticated /api/profile/me) ───────────────────────────────

    /**
     * Returns the profile for the given authenticated userId, creating it with
     * the supplied default name/email if it does not exist yet.
     */
    @Transactional
    public UserProfile getProfileByUserId(Long userId, String defaultName, String defaultEmail) {
        return repo.findByUserId(userId)
                .orElseGet(() -> repo.save(new UserProfile(userId, defaultName, defaultEmail)));
    }

    /**
     * Updates name and/or email for the given authenticated userId.
     * The profile is created (with supplied defaults) if absent.
     */
    @Transactional
    public UserProfile updateProfileByUserId(Long userId, String defaultName, String defaultEmail,
                                             String name, String email) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (name != null && !name.isBlank()) {
            user.setFullName(name.trim());
        }
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (!EMAIL_RE.matcher(normalizedEmail).matches()) {
                throw new IllegalArgumentException("Please enter a valid email address.");
            }
            if (userRepo.countByNormalizedEmailAndIdNot(normalizedEmail, userId) > 0) {
                throw new IllegalArgumentException("That email address is already in use.");
            }
            user.setEmail(normalizedEmail);
        }
        userRepo.save(user);

        UserProfile profile = getProfileByUserId(userId, user.getFullName(), user.getEmail());
        profile.setName(user.getFullName());
        profile.setEmail(user.getEmail());
        return repo.save(profile);
    }
}
