package com.thirdeye.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Authenticated user account.
 *
 * Table is named "app_users" to avoid conflict with the SQL reserved
 * word "user" on some databases (including H2 in some modes).
 * Named "AppUser" to avoid collision with Spring Security's own "User" class.
 *
 * passwordHash is never serialised to JSON — always access via service only.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private LocalDateTime createdAt = LocalDateTime.now();

    public AppUser() {}

    public AppUser(String fullName, String email, String passwordHash) {
        this.fullName     = fullName;
        this.email        = email;
        this.passwordHash = passwordHash;
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public Long          getId()                           { return id; }
    public String        getFullName()                     { return fullName; }
    public void          setFullName(String fullName)      { this.fullName = fullName; }
    public String        getEmail()                        { return email; }
    public void          setEmail(String email)            { this.email = email; }
    public String        getPasswordHash()                 { return passwordHash; }
    public void          setPasswordHash(String h)         { this.passwordHash = h; }
    public LocalDateTime getCreatedAt()                    { return createdAt; }
}
