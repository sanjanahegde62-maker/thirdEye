package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.AppUser;
import com.thirdeye.backend.exception.ResourceNotFoundException;
import com.thirdeye.backend.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Locale;

/**
 * Manages AppUser registration and Spring Security authentication.
 *
 * Implements UserDetailsService so Spring Security can load users by email
 * during the authentication process.
 */
@Service
public class AppUserService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder   passwordEncoder;

    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user.
     *
     * @throws IllegalArgumentException if the email is already in use
     */
    @Transactional
    public AppUser register(String fullName, String email, String rawPassword) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.countByNormalizedEmail(normalizedEmail) > 0) {
            throw new IllegalArgumentException("Email already registered.");
        }
        String hash = passwordEncoder.encode(rawPassword);
        return userRepository.save(new AppUser(fullName == null ? "" : fullName.trim(), normalizedEmail, hash));
    }

    /**
     * Loads a user by email for Spring Security — username IS email in this app.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return new User(user.getEmail(), user.getPasswordHash(), Collections.emptyList());
    }

    /**
     * Finds an AppUser by its primary key.
     *
     * @throws ResourceNotFoundException if no user with that id exists
     */
    public AppUser getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Finds an AppUser by email (used by AuthController to fetch the full entity after auth).
     */
    public AppUser getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
