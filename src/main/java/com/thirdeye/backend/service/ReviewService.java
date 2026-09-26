package com.thirdeye.backend.service;

import com.thirdeye.backend.entity.Project;
import com.thirdeye.backend.entity.Review;
import com.thirdeye.backend.exception.ResourceNotFoundException;
import com.thirdeye.backend.repository.ProjectRepository;
import com.thirdeye.backend.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository   reviewRepository;
    private final ProjectRepository  projectRepository;
    private final MockAnalysisService analysisService;

    public ReviewService(ReviewRepository reviewRepository,
                         ProjectRepository projectRepository,
                         MockAnalysisService analysisService) {
        this.reviewRepository = reviewRepository;
        this.projectRepository = projectRepository;
        this.analysisService = analysisService;
    }

    public List<Review> getReviewsByProject(Long projectId) {
        // Verify project exists first (404 if not)
        getProjectOrThrow(projectId);
        return reviewRepository.findByProject_Id(projectId);
    }

    public Review getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found with id: " + reviewId));
    }

    /**
     * Creates the review, persists it, and fires off asynchronous mock analysis.
     * The HTTP response is returned before analysis completes.
     */
    public Review createReview(Long projectId,
                               String title,
                               String codeDiff,
                               int filesChanged,
                               int linesAdded,
                               int linesRemoved,
                               int commits) {

        Project project = getProjectOrThrow(projectId);

        Review review = new Review(project, title, codeDiff);
        review.setFilesChanged(filesChanged);
        review.setLinesAdded(linesAdded);
        review.setLinesRemoved(linesRemoved);
        review.setCommits(commits);
        review.setStatus("PENDING");
        review.setProgress(0);

        Review saved = reviewRepository.save(review);

        // Kick off analysis asynchronously — does NOT block the HTTP response
        analysisService.analyzeAsync(saved.getId());

        return saved;
    }

    /**
     * Triggers a fresh analysis run for an existing review.
     * Idempotent: previous findings are deleted before re-running.
     *
     * @throws ResourceNotFoundException if reviewId does not exist
     */
    public Review triggerAnalysis(Long reviewId) {
        Review review = getReviewById(reviewId);
        review.setStatus("PENDING");
        review.setProgress(0);
        Review saved = reviewRepository.save(review);
        analysisService.analyzeAsync(saved.getId());
        return saved;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));
    }
}
