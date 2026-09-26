package com.thirdeye.backend.service;

import com.thirdeye.backend.analysis.StaticAnalysisEngine;
import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.entity.Notification.EventType;
import com.thirdeye.backend.entity.Review;
import com.thirdeye.backend.repository.FindingRepository;
import com.thirdeye.backend.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Asynchronous code-review analysis service.
 *
 * <p>Milestone 1 implementation: replaces the previous mock/placeholder
 * pipeline with a genuine deterministic static-analysis engine
 * ({@link StaticAnalysisEngine}).  The engine applies four rule families
 * to the <em>added lines</em> of the submitted unified diff:
 * <ul>
 *   <li>SQLI-001 — SQL injection via string concatenation</li>
 *   <li>CRED-001 — Hardcoded credential literals</li>
 *   <li>EXEC-001 — Dangerous OS command execution</li>
 *   <li>DESER-001 — Unsafe Java deserialization</li>
 * </ul>
 *
 * <p><strong>What this is NOT</strong>: not an AI/LLM model, not a full
 * compiler or parser.  Rules use regex-based pattern matching on individual
 * diff lines and report <em>potential</em> issues, not confirmed
 * vulnerabilities.  See {@code ANALYSIS_ENGINE.md} for limitations.
 *
 * <p>The class name {@code MockAnalysisService} is intentionally retained
 * so that existing Spring wiring and integration-test {@code @Autowired}
 * references continue to resolve without change.
 *
 * <p><strong>Historical data</strong>: findings persisted by previous runs
 * (including the recovered historical review ID 1) are not touched by this
 * service.  The engine only processes reviews that are explicitly submitted
 * or re-triggered.
 */
@Service
public class MockAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(MockAnalysisService.class);

    private final ReviewRepository     reviewRepository;
    private final FindingRepository    findingRepository;
    private final StaticAnalysisEngine engine;
    private final NotificationService  notificationService;

    public MockAnalysisService(ReviewRepository reviewRepository,
                               FindingRepository findingRepository,
                               StaticAnalysisEngine engine,
                               NotificationService notificationService) {
        this.reviewRepository    = reviewRepository;
        this.findingRepository   = findingRepository;
        this.engine              = engine;
        this.notificationService = notificationService;
    }

    /**
     * Runs analysis asynchronously so that {@code POST /reviews} returns
     * immediately while analysis proceeds in the background.
     *
     * <p>Steps:
     * <ol>
     *   <li>Mark review ANALYZING (progress 10 %)</li>
     *   <li>Delete any existing findings for this review (idempotent re-run)</li>
     *   <li>Run the static-analysis engine against the stored diff</li>
     *   <li>Persist findings, mark COMPLETED (progress 100 %)</li>
     *   <li>Emit REVIEW_COMPLETED and NEW_FINDINGS notifications</li>
     * </ol>
     *
     * <p>On unexpected error the review is marked FAILED.
     */
    @Async
    @Transactional
    public void analyzeAsync(Long reviewId) {
        log.info("[ANALYSIS] Starting analysis for review {}", reviewId);

        Review review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            log.warn("[ANALYSIS] Review {} not found — skipping", reviewId);
            return;
        }

        try {
            // Step 1 — mark ANALYZING
            review.setStatus("ANALYZING");
            review.setProgress(10);
            reviewRepository.save(review);

            // Step 2 — clear stale findings (idempotent re-run)
            findingRepository.deleteByReview_Id(reviewId);

            // Step 3 — run the engine
            String diff = review.getCodeDiff() == null ? "" : review.getCodeDiff();
            List<Finding> findings = engine.analyze(review, diff);

            // Step 4 — progress update
            review.setProgress(70);
            reviewRepository.save(review);

            // Step 5 — persist findings
            findingRepository.saveAll(findings);

            // Step 6 — mark COMPLETED
            review.setStatus("COMPLETED");
            review.setProgress(100);
            reviewRepository.save(review);

            log.info("[ANALYSIS] Completed review {} — {} findings (rules: {})",
                    reviewId, findings.size(), engine.registeredRuleIds());

            // Step 7 — emit notifications
            notificationService.emit(
                    EventType.REVIEW_COMPLETED,
                    "Review completed: " + review.getTitle(),
                    "Analysis finished for review #" + reviewId + ".",
                    reviewId);

            if (!findings.isEmpty()) {
                String topSeverity = findings.stream()
                        .map(Finding::getSeverity)
                        .reduce((a, b) -> severityRank(a) >= severityRank(b) ? a : b)
                        .orElse("low");
                notificationService.emit(
                        EventType.NEW_FINDINGS,
                        findings.size() + " finding" + (findings.size() == 1 ? "" : "s")
                                + " in \u201c" + review.getTitle() + "\u201d",
                        findings.size() + " issue" + (findings.size() == 1 ? "" : "s")
                                + " detected (highest severity: " + topSeverity + ").",
                        reviewId);
            }

        } catch (Exception ex) {
            log.error("[ANALYSIS] Analysis failed for review {}: {}", reviewId, ex.getMessage(), ex);
            try {
                review.setStatus("FAILED");
                reviewRepository.save(review);
            } catch (Exception saveEx) {
                log.error("[ANALYSIS] Could not persist FAILED status for review {}", reviewId, saveEx);
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static int severityRank(String s) {
        return switch (s == null ? "" : s.toLowerCase()) {
            case "critical" -> 4;
            case "high"     -> 3;
            case "medium"   -> 2;
            case "low"      -> 1;
            default         -> 0;
        };
    }
}
