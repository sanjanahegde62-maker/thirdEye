package com.thirdeye.backend;

import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.entity.Project;
import com.thirdeye.backend.entity.Review;
import com.thirdeye.backend.repository.FindingRepository;
import com.thirdeye.backend.repository.ProjectRepository;
import com.thirdeye.backend.repository.ReviewRepository;
import com.thirdeye.backend.service.MockAnalysisService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4.x: @AutoConfigureMockMvc moved to spring-boot-webmvc-test module
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ThirdEye AI backend.
 *
 * Each test starts from a clean database state (repositories are cleared in @BeforeEach).
 * MockAnalysisService is tested synchronously by calling analyzeAsync() directly and
 * waiting for its @Transactional completion within the same test thread context,
 * since Spring's async proxy is bypassed when calling from the same bean.
 *
 * All requests use .with(user("test")) to satisfy Spring Security auth.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ThirdeyeBackendApplicationTests {

    @Autowired MockMvc mockMvc;

    @Autowired ProjectRepository    projectRepository;
    @Autowired ReviewRepository     reviewRepository;
    @Autowired FindingRepository    findingRepository;
    @Autowired MockAnalysisService  mockAnalysisService;

    @BeforeEach
    void setUp() {
        // Brief pause to allow any @Async analysis tasks from the previous test to complete
        // before we wipe the DB. Without this, async tasks writing findings may race with
        // reviewRepository.deleteAll(), causing FK constraint violations.
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        // Delete in FK-safe order: findings first (FK → reviews), then reviews (FK → projects)
        findingRepository.deleteAll();
        reviewRepository.deleteAll();
        projectRepository.deleteAll();
    }

    // ── context ───────────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Passes if Spring context starts without errors
    }

    // ── projects ──────────────────────────────────────────────────────────────

    @Test
    void createProject_returnsCreatedProject() throws Exception {
        String body = """
                {"name":"TestProject","description":"desc","repositoryUrl":"https://github.com/x","status":"ACTIVE"}
                """;

        mockMvc.perform(auth(post("/api/projects"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TestProject"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void getProjects_returnsAllProjects() throws Exception {
        projectRepository.save(new Project("P1", "d1", "http://r1", "ACTIVE"));
        projectRepository.save(new Project("P2", "d2", "http://r2", "ACTIVE"));

        mockMvc.perform(auth(get("/api/projects")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getProjectById_notFound_returns404() throws Exception {
        mockMvc.perform(auth(get("/api/projects/99999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Project not found with id: 99999"));
    }

    // ── reviews ───────────────────────────────────────────────────────────────

    @Test
    void createReview_returnsPendingReview() throws Exception {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));

        String body = String.format("""
                {"title":"My Review","codeDiff":"+int x = 1;","filesChanged":1,"linesAdded":1,"linesRemoved":0,"commits":1}
                """);

        mockMvc.perform(auth(post("/api/projects/" + project.getId() + "/reviews"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Review"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createReview_missingTitle_returns400() throws Exception {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));

        String body = """
                {"codeDiff":"+int x = 1;"}
                """;

        mockMvc.perform(auth(post("/api/projects/" + project.getId() + "/reviews"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createReview_unknownProject_returns404() throws Exception {
        String body = """
                {"title":"R","codeDiff":"+x","filesChanged":0,"linesAdded":0,"linesRemoved":0,"commits":0}
                """;

        mockMvc.perform(auth(post("/api/projects/99999/reviews"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReviewById_notFound_returns404() throws Exception {
        mockMvc.perform(auth(get("/api/reviews/99999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getReviewsByProject_returnsListForProject() throws Exception {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));
        Review r = new Review(project, "Rev1", "+code");
        reviewRepository.save(r);

        mockMvc.perform(auth(get("/api/projects/" + project.getId() + "/reviews")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Rev1"));
    }

    // ── analysis engine integration ───────────────────────────────────────────

    @Test
    void analysis_emptyDiff_producesEmptyDiffFinding() {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));
        Review review = reviewRepository.save(new Review(project, "Rev", ""));

        mockAnalysisService.analyzeAsync(review.getId());
        awaitAnalysisComplete(review.getId());

        Review updated = reviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getProgress()).isEqualTo(100);

        List<Finding> findings = findingRepository.findByReview_Id(review.getId());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getSeverity()).isEqualTo("low");
        assertThat(findings.get(0).getCategory()).isEqualTo("quality");
    }

    @Test
    void analysis_credentialPattern_producesCriticalFinding() {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));
        String diff = "+++ b/config.java\n+++ b/config.java\n@@ -0,0 +1 @@\n+String password = \"secret123\";\n";
        Review review = reviewRepository.save(new Review(project, "Rev", diff));

        mockAnalysisService.analyzeAsync(review.getId());
        awaitAnalysisComplete(review.getId());

        List<Finding> findings = findingRepository.findByReview_Id(review.getId());
        boolean hasCritical = findings.stream()
                .anyMatch(f -> "critical".equals(f.getSeverity())
                               && "security".equals(f.getCategory()));
        assertThat(hasCritical).isTrue();
    }

    @Test
    void mockAnalysis_rerun_doesNotDuplicateFindings() {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));
        String diff = "+String password = \"abc\";\n";
        Review review = reviewRepository.save(new Review(project, "Rev", diff));

        // First run
        mockAnalysisService.analyzeAsync(review.getId());
        awaitAnalysisComplete(review.getId());
        long countAfterFirst = findingRepository.findByReview_Id(review.getId()).size();

        // Reset to PENDING so the second run can proceed
        Review r = reviewRepository.findById(review.getId()).orElseThrow();
        r.setStatus("PENDING");
        reviewRepository.save(r);

        // Second run
        mockAnalysisService.analyzeAsync(review.getId());
        awaitAnalysisComplete(review.getId());
        long countAfterSecond = findingRepository.findByReview_Id(review.getId()).size();

        assertThat(countAfterFirst).isEqualTo(countAfterSecond);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Waits up to 5 s for a review to reach COMPLETED or FAILED status. */
    private void awaitAnalysisComplete(Long reviewId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    String status = reviewRepository.findById(reviewId)
                            .map(Review::getStatus).orElse("UNKNOWN");
                    return "COMPLETED".equals(status) || "FAILED".equals(status);
                });
    }

    // ── findings summary ──────────────────────────────────────────────────────

    @Test
    void findingsSummary_returnsCorrectCounts() throws Exception {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));
        Review review = reviewRepository.save(new Review(project, "Rev", ""));

        findingRepository.save(new Finding(review, "F1", "critical", "security", "f.java", 1, "d", "s"));
        findingRepository.save(new Finding(review, "F2", "high",     "bugs",     "f.java", 2, "d", "s"));
        findingRepository.save(new Finding(review, "F3", "high",     "bugs",     "f.java", 3, "d", "s"));
        findingRepository.save(new Finding(review, "F4", "medium",   "quality",  "f.java", 4, "d", "s"));
        findingRepository.save(new Finding(review, "F5", "low",      "quality",  "f.java", 5, "d", "s"));

        mockMvc.perform(auth(get("/api/reviews/" + review.getId() + "/findings/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.critical").value(1))
                .andExpect(jsonPath("$.high").value(2))
                .andExpect(jsonPath("$.medium").value(1))
                .andExpect(jsonPath("$.low").value(1));
    }

    @Test
    void findingsSummary_unknownReview_returns404() throws Exception {
        mockMvc.perform(auth(get("/api/reviews/99999/findings/summary")))
                .andExpect(status().isNotFound());
    }

    // ── /analyze endpoint ─────────────────────────────────────────────────────

    @Test
    void triggerAnalyze_returnsReviewWithPendingStatus() throws Exception {
        Project project = projectRepository.save(
                new Project("Proj", "d", "http://r", "ACTIVE"));
        Review review = reviewRepository.save(new Review(project, "Rev", "+x=1"));

        mockMvc.perform(auth(post("/api/reviews/" + review.getId() + "/analyze")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(review.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void triggerAnalyze_unknownReview_returns404() throws Exception {
        mockMvc.perform(auth(post("/api/reviews/99999/analyze")))
                .andExpect(status().isNotFound());
    }

    /** Adds a mock authenticated user to a request builder. */
    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.with(user("testuser").roles("USER"));
    }
}
