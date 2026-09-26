package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.Review;
import com.thirdeye.backend.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** GET /api/projects/{projectId}/reviews */
    @GetMapping("/projects/{projectId}/reviews")
    public List<Review> getReviewsByProject(@PathVariable Long projectId) {
        return reviewService.getReviewsByProject(projectId);
    }

    /** GET /api/reviews/{reviewId} */
    @GetMapping("/reviews/{reviewId}")
    public Review getReviewById(@PathVariable Long reviewId) {
        return reviewService.getReviewById(reviewId);
    }

    /**
     * GET /api/reviews/{reviewId}/diff
     * Returns the raw code diff as plain text.
     */
    @GetMapping(value = "/reviews/{reviewId}/diff", produces = "text/plain")
    public String getReviewDiff(@PathVariable Long reviewId) {
        String diff = reviewService.getReviewById(reviewId).getCodeDiff();
        return diff != null ? diff : "";
    }

    /** POST /api/projects/{projectId}/reviews */
    @PostMapping("/projects/{projectId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public Review createReview(@PathVariable Long projectId,
                               @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.createReview(
                projectId,
                request.getTitle(),
                request.getCodeDiff(),
                request.getFilesChanged(),
                request.getLinesAdded(),
                request.getLinesRemoved(),
                request.getCommits()
        );
    }

    /**
     * POST /api/reviews/{reviewId}/analyze
     * Re-runs (or triggers) mock analysis for an existing review.
     * Idempotent — existing findings are deleted before re-analysis.
     */
    @PostMapping("/reviews/{reviewId}/analyze")
    public Review triggerAnalysis(@PathVariable Long reviewId) {
        return reviewService.triggerAnalysis(reviewId);
    }

    // ── Request body ──────────────────────────────────────────────────────────

    public static class CreateReviewRequest {

        @NotBlank(message = "title must not be blank")
        private String title;

        private String codeDiff;
        private int filesChanged;
        private int linesAdded;
        private int linesRemoved;
        private int commits;

        public String getTitle()                  { return title; }
        public void   setTitle(String t)          { this.title = t; }

        public String getCodeDiff()               { return codeDiff; }
        public void   setCodeDiff(String d)       { this.codeDiff = d; }

        public int  getFilesChanged()             { return filesChanged; }
        public void setFilesChanged(int n)        { this.filesChanged = n; }

        public int  getLinesAdded()               { return linesAdded; }
        public void setLinesAdded(int n)          { this.linesAdded = n; }

        public int  getLinesRemoved()             { return linesRemoved; }
        public void setLinesRemoved(int n)        { this.linesRemoved = n; }

        public int  getCommits()                  { return commits; }
        public void setCommits(int n)             { this.commits = n; }
    }
}
