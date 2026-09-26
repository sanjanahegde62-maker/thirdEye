package com.thirdeye.backend;

import com.thirdeye.backend.repository.AppUserRepository;
import com.thirdeye.backend.repository.NotificationPreferencesRepository;
import com.thirdeye.backend.repository.UserProfileRepository;
import com.thirdeye.backend.entity.AppUser;
import com.thirdeye.backend.entity.UserProfile;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication, settings, and protected endpoint access.
 *
 * Uses real Spring context + H2 in-memory database.
 * Sessions are tracked via MockHttpSession (Spring MockMvc does not issue JSESSIONID cookies).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthTests {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository userRepo;
    @Autowired UserProfileRepository profileRepo;
    @Autowired NotificationPreferencesRepository prefsRepo;

    private static final String REGISTER_URL  = "/api/auth/register";
    private static final String LOGIN_URL      = "/api/auth/login";
    private static final String LOGOUT_URL     = "/api/auth/logout";
    private static final String ME_URL         = "/api/auth/me";
    private static final String SETTINGS_URL   = "/api/settings";

    // Shared test credentials
    private static final String EMAIL    = "testauth@thirdeye.test";
    private static final String PASSWORD = "StrongPass1!";
    private static final String NAME     = "Auth Tester";

    @BeforeEach
    void resetUsers() {
        prefsRepo.deleteAll();
        profileRepo.deleteAll();
        userRepo.findAll().stream()
                .filter(u -> u.getEmail().endsWith("@thirdeye.test"))
                .forEach(u -> userRepo.delete(u));
    }

    // ── Registration ─────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void register_success_returns201WithUserData() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fullName":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
                            """.formatted(NAME, EMAIL, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value(NAME))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @Order(2)
    void register_duplicateEmail_returns409() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fullName":"Other","email":"%s","password":"%s","confirmPassword":"%s"}
                            """.formatted(EMAIL, PASSWORD, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void register_mismatchedPasswords_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fullName":"Test","email":"mismatch@thirdeye.test","password":"pass1234","confirmPassword":"different"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords do not match."));
    }

    @Test
    @Order(4)
    void register_weakPassword_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fullName":"Test","email":"weak@thirdeye.test","password":"short","confirmPassword":"short"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must be at least 8 characters."));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void login_correctCredentials_returns200() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","password":"%s"}
                            """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value(NAME));
    }

    @Test
    @Order(6)
    void login_wrongPassword_returns401() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","password":"wrongpassword"}
                            """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized());
    }

    // ── /me endpoint ──────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get(ME_URL).header("Origin", "http://localhost:5174"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void cors_preflight_isHandledBeforeAuthentication() throws Exception {
        mockMvc.perform(options(REGISTER_URL)
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void cors_rejectsUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options(REGISTER_URL)
                        .header("Origin", "http://localhost:5175")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @Order(8)
    void me_afterLogin_returns200WithUserData() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);
        assertThat(session).isNotNull();

        mockMvc.perform(get(ME_URL).session(session).header("Origin", "http://localhost:5174"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value(NAME));
    }

    @Test
    @Order(9)
    void logout_returns200() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        mockMvc.perform(post(LOGOUT_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out"));
    }

    @Test
    @Order(10)
    void me_afterLogout_returns401() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        // Logout
        mockMvc.perform(post(LOGOUT_URL).session(session));

        // After logout the session is invalidated — new request with old session should 401
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── Protected endpoint access ─────────────────────────────────────────────

    @Test
    @Order(11)
    void projects_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_and_login_arePublicAndHaveCorsHeaders() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .header("Origin", "http://localhost:5174")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fullName":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
                            """.formatted(NAME, EMAIL, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
        mockMvc.perform(post(LOGIN_URL)
                        .header("Origin", "http://localhost:5174")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Test
    @Order(12)
    void settings_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(SETTINGS_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    void settings_getDefaults_afterLogin() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);
        assertThat(session).isNotNull();

        mockMvc.perform(get(SETTINGS_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value(NAME))
                .andExpect(jsonPath("$.profile.email").value(EMAIL))
                .andExpect(jsonPath("$.preferences.reviewCompleted").value(true))
                .andExpect(jsonPath("$.preferences.newFindings").value(true))
                .andExpect(jsonPath("$.preferences.testsGenerated").value(true));
    }

    @Test
    @Order(14)
    void settings_patchPreferences_persists() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        // Disable newFindings
        mockMvc.perform(patch(SETTINGS_URL + "/preferences")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newFindings\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newFindings").value(false))
                .andExpect(jsonPath("$.reviewCompleted").value(true));

        // Verify via GET that the change persisted
        mockMvc.perform(get(SETTINGS_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.newFindings").value(false));
    }

    @Test
    @Order(15)
    void settings_patchProfile_persists() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);

        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        mockMvc.perform(patch(SETTINGS_URL + "/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));

        mockMvc.perform(get(SETTINGS_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("Updated Name"));
    }

    @Test
    @Order(16)
    void settings_patchProfile_updatesAccountAndCanLoginWithNewEmail() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);
        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        mockMvc.perform(patch(SETTINGS_URL + "/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\",\"email\":\"updated@thirdeye.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("updated@thirdeye.test"));

        mockMvc.perform(get(ME_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("updated@thirdeye.test"));

        mockMvc.perform(get(SETTINGS_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("Updated Name"))
                .andExpect(jsonPath("$.profile.email").value("updated@thirdeye.test"));

        mockMvc.perform(post(LOGOUT_URL).session(session)).andExpect(status().isOk());
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"updated@thirdeye.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"));
    }

    @Test
    @Order(17)
    void settings_patchProfile_rejectsEmailOwnedByAnotherUser() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);
        registerUser("Other User", "otherauth@thirdeye.test", PASSWORD);
        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        mockMvc.perform(patch(SETTINGS_URL + "/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed\",\"email\":\"otherauth@thirdeye.test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That email address is already in use."));

        mockMvc.perform(get(ME_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(NAME))
                .andExpect(jsonPath("$.email").value(EMAIL));
        assertThat(userRepo.findByEmail("otherauth@thirdeye.test")).isPresent();
    }

    @Test
    @Order(18)
    void settings_patchProfile_createsPerUserProfileWithoutReassigningLegacyRow() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);
        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);
        UserProfile legacyProfile = profileRepo.save(new UserProfile("Legacy profile", "legacy@example.test"));
        Long accountId = userRepo.findByEmail(EMAIL).orElseThrow().getId();

        mockMvc.perform(patch(SETTINGS_URL + "/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mapped User\",\"email\":\"mapped@thirdeye.test\"}"))
                .andExpect(status().isOk());

        UserProfile linkedProfile = profileRepo.findByUserId(accountId).orElseThrow();
        assertThat(linkedProfile.getName()).isEqualTo("Mapped User");
        assertThat(linkedProfile.getEmail()).isEqualTo("mapped@thirdeye.test");
        assertThat(profileRepo.findById(legacyProfile.getId()).orElseThrow().getUserId()).isNull();
        assertThat(profileRepo.findById(legacyProfile.getId()).orElseThrow().getName())
                .isEqualTo("Legacy profile");
    }

    @Test
    @Order(19)
    void settings_patchProfile_rejectsCaseAndWhitespaceEmailDuplicate() throws Exception {
        registerUser(NAME, EMAIL, PASSWORD);
        userRepo.save(new AppUser("Other User", "  MitUser@thirdeye.test", "test-only-hash"));
        MockHttpSession session = loginAndGetSession(EMAIL, PASSWORD);

        mockMvc.perform(patch(SETTINGS_URL + "/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed Name\",\"email\":\"mituser@thirdeye.test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That email address is already in use."));

        mockMvc.perform(get(ME_URL).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(NAME))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void registerUser(String fullName, String email, String password) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fullName":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
                            """.formatted(fullName, email, password, password)));
    }

    /**
     * Performs a login and returns the MockHttpSession from the response,
     * which carries the Spring Security context for subsequent requests.
     */
    private MockHttpSession loginAndGetSession(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","password":"%s"}
                            """.formatted(email, password)))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
