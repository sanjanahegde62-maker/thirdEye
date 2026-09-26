package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.GeneratedTest;
import com.thirdeye.backend.service.TestGenerationService;
import com.thirdeye.backend.service.TestReadinessService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews/{reviewId}/tests")
public class TestController {

    private final TestGenerationService testGenerationService;
    private final TestReadinessService testReadinessService;

    public TestController(
            TestGenerationService testGenerationService,
            TestReadinessService testReadinessService) {
        this.testGenerationService = testGenerationService;
        this.testReadinessService = testReadinessService;
    }

    /**
     * GET /api/reviews/{reviewId}/tests
     * Returns generated tests for a review.
     */
    @GetMapping
    public List<GeneratedTest> getTests(@PathVariable Long reviewId) {
        return testGenerationService.getTestsForReview(reviewId);
    }

    /**
     * POST /api/reviews/{reviewId}/tests/generate
     * Generates deterministic test scaffolds.
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<GeneratedTest> generateTests(@PathVariable Long reviewId) {
        return testGenerationService.generateTests(reviewId);
    }

    /**
     * POST /api/reviews/{reviewId}/tests/{testId}/readiness
     * Checks for common scaffold placeholders.
     * Does not execute the test or change its status.
     */
    @PostMapping("/{testId}/readiness")
    public Map<String, Object> checkReadiness(
            @PathVariable Long reviewId,
            @PathVariable Long testId) {
        return testReadinessService.checkReadiness(reviewId, testId);
    }
}