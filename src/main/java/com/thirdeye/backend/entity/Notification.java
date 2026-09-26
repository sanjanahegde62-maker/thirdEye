package com.thirdeye.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A single in-app notification event.
 *
 * Notifications are created automatically by the review pipeline when:
 *   - analysis completes (REVIEW_COMPLETED)
 *   - new findings are produced (NEW_FINDINGS)
 *   - test generation completes (TESTS_GENERATED)
 *
 * They can also be created manually via the API.
 * read = false  →  unread (shown with the red dot)
 * read = true   →  dismissed
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum EventType {
        REVIEW_COMPLETED,
        NEW_FINDINGS,
        TESTS_GENERATED,
        GENERAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    /** Optional link to the relevant review (may be null for GENERAL events). */
    private Long reviewId;

    /** Nullable — null means broadcast (visible to all users). */
    private Long userId;

    private boolean read = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(EventType eventType, String title, String message, Long reviewId) {
        this.eventType = eventType;
        this.title     = title;
        this.message   = message;
        this.reviewId  = reviewId;
    }

    public Notification(EventType eventType, String title, String message, Long reviewId, Long userId) {
        this(eventType, title, message, reviewId);
        this.userId = userId;
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public Long      getId()                      { return id; }
    public EventType getEventType()               { return eventType; }
    public String    getTitle()                   { return title; }
    public String    getMessage()                 { return message; }
    public Long      getReviewId()                { return reviewId; }
    public Long      getUserId()                  { return userId; }
    public void      setUserId(Long userId)       { this.userId = userId; }
    public boolean   isRead()                     { return read; }
    public void      setRead(boolean read)        { this.read = read; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
}
