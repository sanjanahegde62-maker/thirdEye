package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.Notification;
import com.thirdeye.backend.entity.Notification.EventType;
import com.thirdeye.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Creates, retrieves, and marks notifications.
 *
 * Notifications are emitted automatically by {@link MockAnalysisService}
 * and {@link TestGenerationService} via this service so the frontend's
 * notification bell always reflects real backend events.
 *
 * A null userId on a notification means "broadcast" — visible to all users.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    // ── Broadcast read (all rows, no user filter) ─────────────────────────────

    public List<Notification> getAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public Map<String, Object> getSummary() {
        long unread = repo.countByReadFalse();
        long total  = repo.count();
        return Map.of("total", total, "unread", unread);
    }

    // ── User-scoped read ──────────────────────────────────────────────────────

    public List<Notification> getByUserId(Long userId) {
        return repo.findByUserIdOrBroadcast(userId);
    }

    public Map<String, Object> getSummaryByUserId(Long userId) {
        long unread = repo.countUnreadByUserIdOrBroadcast(userId);
        long total  = repo.findByUserIdOrBroadcast(userId).size();
        return Map.of("total", total, "unread", unread);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public Notification markRead(Long id) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> new com.thirdeye.backend.exception.ResourceNotFoundException(
                        "Notification not found: " + id));
        n.setRead(true);
        return repo.save(n);
    }

    @Transactional
    public Notification markReadByUserId(Long id, Long userId) {
        // Allow marking any notification that is visible to this user
        Notification n = repo.findById(id)
                .orElseThrow(() -> new com.thirdeye.backend.exception.ResourceNotFoundException(
                        "Notification not found: " + id));
        n.setRead(true);
        return repo.save(n);
    }

    @Transactional
    public void markAllRead() {
        repo.markAllRead();
    }

    @Transactional
    public void markAllReadByUserId(Long userId) {
        repo.markAllReadByUserIdOrBroadcast(userId);
    }

    // ── emit ─────────────────────────────────────────────────────────────────

    /** Emit a broadcast notification (userId=null — visible to all users). */
    @Transactional
    public void emit(EventType type, String title, String message, Long reviewId) {
        repo.save(new Notification(type, title, message, reviewId));
    }

    /** Emit a user-scoped notification. */
    @Transactional
    public void emit(EventType type, String title, String message, Long reviewId, Long userId) {
        repo.save(new Notification(type, title, message, reviewId, userId));
    }
}
