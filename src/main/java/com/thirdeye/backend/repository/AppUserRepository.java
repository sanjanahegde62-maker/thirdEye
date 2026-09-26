package com.thirdeye.backend.repository;

import com.thirdeye.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    @Query("select count(u) from AppUser u where lower(trim(u.email)) = lower(trim(:email))")
    long countByNormalizedEmail(@Param("email") String email);

    @Query("select count(u) from AppUser u where lower(trim(u.email)) = lower(trim(:email)) and u.id <> :id")
    long countByNormalizedEmailAndIdNot(@Param("email") String email, @Param("id") Long id);
}
