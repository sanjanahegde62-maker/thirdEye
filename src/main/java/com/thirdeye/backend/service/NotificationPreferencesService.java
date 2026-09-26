package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.NotificationPreferences;
import com.thirdeye.backend.repository.NotificationPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages per-user notification preferences.
 * A default row is created on first access.
 */
@Service
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository repo;

    public NotificationPreferencesService(NotificationPreferencesRepository repo) {
        this.repo = repo;
    }

    /**
     * Returns preferences for userId, creating defaults if absent.
     */
    @Transactional
    public NotificationPreferences getPrefs(Long userId) {
        return repo.findByUserId(userId)
                .orElseGet(() -> repo.save(new NotificationPreferences(userId)));
    }

    /**
     * Updates preferences for userId. Null values leave the current setting unchanged.
     */
    @Transactional
    public NotificationPreferences updatePrefs(Long userId,
                                               Boolean reviewCompleted,
                                               Boolean newFindings,
                                               Boolean testsGenerated) {
        NotificationPreferences prefs = getPrefs(userId);
        if (reviewCompleted != null) prefs.setReviewCompleted(reviewCompleted);
        if (newFindings     != null) prefs.setNewFindings(newFindings);
        if (testsGenerated  != null) prefs.setTestsGenerated(testsGenerated);
        return repo.save(prefs);
    }
}
