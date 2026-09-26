package com.thirdeye.backend;

import com.thirdeye.backend.entity.Notification;
import com.thirdeye.backend.entity.UserProfile;
import com.thirdeye.backend.repository.NotificationRepository;
import com.thirdeye.backend.repository.UserProfileRepository;
import com.thirdeye.backend.service.NotificationService;
import com.thirdeye.backend.service.UserProfileService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserProfile and Notification endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileAndNotificationTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserProfileService profileService;
    @Autowired UserProfileRepository profileRepo;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepo;

    @BeforeEach
    void reset() {
        notificationRepo.deleteAll();
        profileRepo.deleteAll();
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void getProfile_createsDefaultOnFirstCall() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ThirdEye User"))
                .andExpect(jsonPath("$.email").value("user@thirdeye.local"));
    }

    @Test
    @Order(2)
    void patchProfile_updatesName() throws Exception {
        profileService.getProfile(); // ensure row exists
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("user@thirdeye.local"));
    }

    @Test
    @Order(3)
    void patchProfile_updatesEmail() throws Exception {
        profileService.getProfile();
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @Order(4)
    void patchProfile_blankNameIsIgnored() throws Exception {
        profileService.updateProfile("Bob", null);
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    @Order(5)
    void profileService_updatePersists() {
        UserProfile p = profileService.updateProfile("Carol", "carol@test.com");
        assertThat(p.getName()).isEqualTo("Carol");
        assertThat(p.getEmail()).isEqualTo("carol@test.com");

        UserProfile reloaded = profileService.getProfile();
        assertThat(reloaded.getName()).isEqualTo("Carol");
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    @Test
    @Order(6)
    void getNotifications_emptyState() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Order(7)
    void getSummary_zeroWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @Order(8)
    void emit_and_retrieveNotification() throws Exception {
        notificationService.emit(
                Notification.EventType.REVIEW_COMPLETED,
                "Review completed",
                "Analysis done for review #1.",
                1L);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Review completed"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @Order(9)
    void markRead_singleNotification() throws Exception {
        notificationService.emit(Notification.EventType.GENERAL, "Test", "msg", null);
        List<Notification> all = notificationRepo.findAllByOrderByCreatedAtDesc();
        Long id = all.get(0).getId();

        mockMvc.perform(patch("/api/notifications/" + id + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @Order(10)
    void markAllRead_clearsUnread() throws Exception {
        notificationService.emit(Notification.EventType.NEW_FINDINGS, "F1", "m1", 1L);
        notificationService.emit(Notification.EventType.NEW_FINDINGS, "F2", "m2", 2L);

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @Order(11)
    void summary_countsUnreadCorrectly() {
        notificationService.emit(Notification.EventType.REVIEW_COMPLETED, "T1", "m", 1L);
        notificationService.emit(Notification.EventType.NEW_FINDINGS,     "T2", "m", 1L);

        var summary = notificationService.getSummary();
        assertThat((long) (Long) summary.get("total")).isEqualTo(2L);
        assertThat((long) (Long) summary.get("unread")).isEqualTo(2L);

        notificationService.markAllRead();
        var after = notificationService.getSummary();
        assertThat((long) (Long) after.get("unread")).isEqualTo(0L);
    }

    @Test
    @Order(12)
    void markRead_unknownId_returns404() throws Exception {
        mockMvc.perform(patch("/api/notifications/999999/read"))
                .andExpect(status().isNotFound());
    }
}
