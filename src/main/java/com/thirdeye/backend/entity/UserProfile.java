package com.thirdeye.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * User profile row.
 *
 * Legacy behaviour: one row with id=1, userId=null is used by the
 * unauthenticated /api/profile endpoint (ProfileAndNotificationTests).
 *
 * New behaviour: each authenticated user gets their own row identified
 * by userId. The /api/profile/me endpoint uses this.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable — legacy row (id=1) has userId=null. */
    @Column(nullable = true)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public UserProfile() {}

    public UserProfile(String name, String email) {
        this.name  = name;
        this.email = email;
    }

    public UserProfile(Long userId, String name, String email) {
        this.userId = userId;
        this.name   = name;
        this.email  = email;
    }

    @PreUpdate
    public void touch() { this.updatedAt = LocalDateTime.now(); }

    // ── accessors ────────────────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public Long getUserId()                { return userId; }
    public void setUserId(Long userId)     { this.userId = userId; }
    public String getName()               { return name; }
    public void   setName(String name)    { this.name = name; }
    public String getEmail()              { return email; }
    public void   setEmail(String email)  { this.email = email; }
    public LocalDateTime getUpdatedAt()   { return updatedAt; }
}
