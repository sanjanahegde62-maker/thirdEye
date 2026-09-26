package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.GeneratedTest;
import com.thirdeye.backend.exception.ResourceNotFoundException;
import com.thirdeye.backend.repository.GeneratedTestRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TestReadinessService {

    private final GeneratedTestRepository testRepository;

    public TestReadinessService(GeneratedTestRepository testRepository) {
        this.testRepository = testRepository;
    }

    public Map<String, Object> checkReadiness(Long reviewId, Long testId) {
        GeneratedTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Generated test not found with id: " + testId));

        if (test.getReview() == null
                || !reviewId.equals(test.getReview().getId())) {
            throw new ResourceNotFoundException(
                    "Test " + testId + " does not belong to review " + reviewId);
        }

        String code = test.getTestCode() == null
                ? ""
                : test.getTestCode();

        String normalized = code.toLowerCase(Locale.ROOT);

        boolean hasScaffoldMarker =
                normalized.contains("scaffold")
                        || normalized.contains("todo:")
                        || normalized.contains("test not yet implemented")
                        || normalized.contains("fail(\"");

        boolean ready = !code.isBlank() && !hasScaffoldMarker;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testId", test.getId());
        result.put("reviewId", reviewId);
        result.put("readyForExecution", ready);
        result.put("status", test.getStatus());

        if (ready) {
            result.put("message",
                    "No common scaffold placeholders were detected. "
                            + "This is not proof that the test is safe or valid to execute. "
                            + "Execution is not performed by this endpoint.");
        } else {
            result.put("message",
                    "Execution blocked: this generated test is empty or still "
                            + "contains scaffold/TODO placeholders. Implement and review "
                            + "the test before attempting execution.");
        }

        return result;
    }
}