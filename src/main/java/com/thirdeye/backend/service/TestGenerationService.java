package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.entity.GeneratedTest;
import com.thirdeye.backend.entity.Notification.EventType;
import com.thirdeye.backend.entity.Review;
import com.thirdeye.backend.exception.ResourceNotFoundException;
import com.thirdeye.backend.repository.FindingRepository;
import com.thirdeye.backend.repository.GeneratedTestRepository;
import com.thirdeye.backend.repository.ReviewRepository;
import com.thirdeye.backend.testgen.TestCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates meaningful, deterministic test scaffolds from static-analysis findings.
 *
 * <h3>How tests are generated</h3>
 * <p>Each finding produced by the static-analysis engine carries a rule ID
 * ({@code SQLI-001}, {@code CRED-001}, {@code EXEC-001}, {@code DESER-001})
 * and an evidence snippet (the actual offending code).  {@link TestCodeGenerator}
 * uses this information to produce a Java JUnit 5 test file that:
 * <ul>
 *   <li>References the exact file and line number of the finding.</li>
 *   <li>Embeds the offending code as a comment.</li>
 *   <li>Provides rule-specific scaffolding instructions and assertion stubs.</li>
 *   <li>Calls {@code fail()} so the test fails fast if run without being completed —
 *       preventing a false "green" result.</li>
 * </ul>
 *
 * <h3>Execution status</h3>
 * <p>All generated tests start as {@code NOT_RUN}.  Tests are <strong>never
 * executed automatically</strong> by this service.  The status must only be
 * changed to {@code PASSED} or {@code FAILED} after actual test execution.
 * Do not mark a test as PASSED unless it was actually run and passed.
 *
 * <h3>No AI involved</h3>
 * <p>Generation is 100% deterministic pattern-based templating — no IBM Granite,
 * no watsonx, no external API call.
 */
@Service
public class TestGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TestGenerationService.class);

    private final ReviewRepository        reviewRepository;
    private final FindingRepository       findingRepository;
    private final GeneratedTestRepository testRepository;
    private final NotificationService     notificationService;

    public TestGenerationService(ReviewRepository reviewRepository,
                                 FindingRepository findingRepository,
                                 GeneratedTestRepository testRepository,
                                 NotificationService notificationService) {
        this.reviewRepository    = reviewRepository;
        this.findingRepository   = findingRepository;
        this.testRepository      = testRepository;
        this.notificationService = notificationService;
    }

    // ── queries ───────────────────────────────────────────────────────────────

    public List<GeneratedTest> getTestsForReview(Long reviewId) {
        getReviewOrThrow(reviewId);
        return testRepository.findByReview_Id(reviewId);
    }

    // ── generation ────────────────────────────────────────────────────────────

    /**
     * Generate (or re-generate) test scaffolds from the findings of a
     * {@code COMPLETED} review.
     *
     * <p>The operation is idempotent — any previously generated tests for this
     * review are deleted before new ones are written.
     *
     * @param reviewId review to generate tests for
     * @return list of persisted generated tests, each with status {@code NOT_RUN}
     * @throws ResourceNotFoundException if the review does not exist
     * @throws IllegalArgumentException  if the review is not yet {@code COMPLETED}
     */
    @Transactional
    public List<GeneratedTest> generateTests(Long reviewId) {
        Review review = getReviewOrThrow(reviewId);

        if (!"COMPLETED".equals(review.getStatus())) {
            throw new IllegalArgumentException(
                "Tests can only be generated for COMPLETED reviews. Current status: "
                + review.getStatus());
        }

        // Clear any previously generated tests (idempotent re-generation)
        testRepository.deleteByReview_Id(reviewId);

        List<Finding> findings = findingRepository.findByReview_Id(reviewId);
        List<GeneratedTest> tests = new ArrayList<>();

        for (Finding f : findings) {
            GeneratedTest t = buildTestForFinding(review, f);
            tests.add(t);
        }

        // If there are no findings at all, add one honest coverage scaffold
        if (findings.isEmpty()) {
            tests.add(buildNoFindingsScaffold(review));
        }

        List<GeneratedTest> saved = testRepository.saveAll(tests);
        log.info("[TESTGEN] Generated {} test scaffold(s) for review {} (all status=NOT_RUN)",
                 saved.size(), reviewId);

        notificationService.emit(
                EventType.TESTS_GENERATED,
                saved.size() + " test scaffold" + (saved.size() == 1 ? "" : "s") + " generated",
                saved.size() + " scaffold" + (saved.size() == 1 ? "" : "s")
                        + " created for review #" + reviewId + " (status: NOT_RUN — not executed).",
                reviewId);

        return saved;
    }

    // ── builders ──────────────────────────────────────────────────────────────

    private GeneratedTest buildTestForFinding(Review review, Finding f) {
        String code     = TestCodeGenerator.generate(f);
        String filePath = TestCodeGenerator.testFilePath(f);
        String type     = TestCodeGenerator.testType(f);
        String name     = buildTestName(f);

        GeneratedTest t = new GeneratedTest(
            review,
            name,
            filePath,
            "NOT_RUN",   // never executed automatically
            null,        // duration unknown until actually run
            String.valueOf(f.getId()),
            code
        );
        t.setTestType(type);
        return t;
    }

    private GeneratedTest buildNoFindingsScaffold(Review review) {
        String code =
            "// ─────────────────────────────────────────────────────────────────────────\n" +
            "// GENERATED BY ThirdEye static-analysis test generator\n" +
            "// Status: NOT_RUN  (never executed)\n" +
            "// No security findings were detected in this review.\n" +
            "// ─────────────────────────────────────────────────────────────────────────\n\n" +
            "import org.junit.jupiter.api.DisplayName;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.assertj.core.api.Assertions.assertThat;\n\n" +
            "/**\n" +
            " * Coverage scaffold for review #" + review.getId() + ".\n" +
            " * No security findings were detected by the static analyser.\n" +
            " * Add tests here to verify the expected behaviour of the reviewed code.\n" +
            " */\n" +
            "// SCAFFOLD — implement the body before using this test\n" +
            "class Review" + review.getId() + "CoverageTest {\n\n" +
            "    @Test\n" +
            "    @DisplayName(\"Review #" + review.getId() + " — no security regressions\")\n" +
            "    void reviewedCode_hasNoSecurityRegressions() {\n" +
            "        // TODO: add assertions that the reviewed code behaves correctly.\n" +
            "        // The static analyser found no issues, but functional tests\n" +
            "        // should still cover the changed code paths.\n" +
            "        fail(\"Test not yet implemented.\");\n" +
            "    }\n" +
            "}\n";

        GeneratedTest t = new GeneratedTest(
            review,
            "review #" + review.getId() + " — no security regressions scaffold",
            "src/test/java/Review" + review.getId() + "CoverageTest.java",
            "NOT_RUN",
            null,
            null,
            code
        );
        t.setTestType("COVERAGE");
        return t;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildTestName(Finding f) {
        String ruleId = TestCodeGenerator.testType(f);
        String file   = f.getFile() == null ? "unknown" : f.getFile();
        // Derive the short filename for readability
        String shortFile = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;

        return switch (ruleId) {
            case "SQLI-001"  -> shortFile + ":" + f.getLine() +
                                " — SQL query must not concatenate user input";
            case "CRED-001"  -> shortFile + ":" + f.getLine() +
                                " — credential must not be a hardcoded literal";
            case "EXEC-001"  -> shortFile + ":" + f.getLine() +
                                " — command execution must reject shell-injection payloads";
            case "DESER-001" -> shortFile + ":" + f.getLine() +
                                " — deserialization must reject untrusted class types";
            default          -> shortFile + ":" + f.getLine() +
                                " — " + lowerFirst(f.getTitle());
        };
    }

    private String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private Review getReviewOrThrow(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found with id: " + reviewId));
    }
}
