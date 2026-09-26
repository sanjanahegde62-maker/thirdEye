package com.thirdeye.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-user notification preferences.
 * One row per authenticated user (identified by userId).
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private boolean reviewCompleted = true;
    private boolean newFindings     = true;
    private boolean testsGenerated  = true;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public NotificationPreferences() {}

    public NotificationPreferences(Long userId) {
        this.userId = userId;
    }

    @PreUpdate
    public void touch() { this.updatedAt = LocalDateTime.now(); }

    // ── accessors ────────────────────────────────────────────────────────────

    public Long          getId()                                  { return id; }
    public Long          getUserId()                              { return userId; }
    public boolean       isReviewCompleted()                      { return reviewCompleted; }
    public void          setReviewCompleted(boolean v)            { this.reviewCompleted = v; }
    public boolean       isNewFindings()                          { return newFindings; }
    public void          setNewFindings(boolean v)                { this.newFindings = v; }
    public boolean       isTestsGenerated()                       { return testsGenerated; }
    public void          setTestsGenerated(boolean v)             { this.testsGenerated = v; }
    public LocalDateTime getUpdatedAt()                           { return updatedAt; }
}
