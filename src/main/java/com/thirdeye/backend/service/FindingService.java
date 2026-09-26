package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.entity.Review;
import com.thirdeye.backend.exception.ResourceNotFoundException;
import com.thirdeye.backend.repository.FindingRepository;
import com.thirdeye.backend.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FindingService {

    private final FindingRepository findingRepository;
    private final ReviewRepository reviewRepository;

    public FindingService(FindingRepository findingRepository,
                          ReviewRepository reviewRepository) {
        this.findingRepository = findingRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<Finding> getFindingsByReview(Long reviewId) {
        getReviewOrThrow(reviewId); // 404 if review missing
        return findingRepository.findByReview_Id(reviewId);
    }

    public Finding createFinding(Long reviewId,
                                 String title,
                                 String severity,
                                 String category,
                                 String file,
                                 int line,
                                 String description,
                                 String suggestion) {

        Review review = getReviewOrThrow(reviewId);

        Finding finding = new Finding(review, title, severity, category,
                                      file, line, description, suggestion);
        return findingRepository.save(finding);
    }

    /**
     * Returns a severity summary for a given review.
     *
     * <p>Response shape:
     * <pre>{@code
     * {
     *   "reviewId": 1,
     *   "total": 5,
     *   "critical": 1,
     *   "high": 2,
     *   "medium": 1,
     *   "low": 1
     * }
     * }</pre>
     *
     * @throws ResourceNotFoundException if reviewId does not exist
     */
    public Map<String, Object> getSummary(Long reviewId) {
        getReviewOrThrow(reviewId); // 404 if review missing

        long total    = findingRepository.findByReview_Id(reviewId).size();
        long critical = findingRepository.countByReview_IdAndSeverityIgnoreCase(reviewId, "critical");
        long high     = findingRepository.countByReview_IdAndSeverityIgnoreCase(reviewId, "high");
        long medium   = findingRepository.countByReview_IdAndSeverityIgnoreCase(reviewId, "medium");
        long low      = findingRepository.countByReview_IdAndSeverityIgnoreCase(reviewId, "low");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reviewId", reviewId);
        summary.put("total", total);
        summary.put("critical", critical);
        summary.put("high", high);
        summary.put("medium", medium);
        summary.put("low", low);
        return summary;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Review getReviewOrThrow(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found with id: " + reviewId));
    }
}
