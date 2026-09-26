package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.service.FindingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews/{reviewId}/findings")
public class FindingController {

    private final FindingService findingService;

    public FindingController(FindingService findingService) {
        this.findingService = findingService;
    }

    /** GET /api/reviews/{reviewId}/findings */
    @GetMapping
    public List<Finding> getFindings(@PathVariable Long reviewId) {
        return findingService.getFindingsByReview(reviewId);
    }

    /** POST /api/reviews/{reviewId}/findings */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Finding createFinding(@PathVariable Long reviewId,
                                 @Valid @RequestBody CreateFindingRequest request) {
        return findingService.createFinding(
                reviewId,
                request.getTitle(),
                request.getSeverity(),
                request.getCategory(),
                request.getFile(),
                request.getLine(),
                request.getDescription(),
                request.getSuggestion()
        );
    }

    /**
     * GET /api/reviews/{reviewId}/findings/summary
     *
     * <p>Returns total finding counts grouped by severity.
     *
     * <p>Example response:
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
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary(@PathVariable Long reviewId) {
        return findingService.getSummary(reviewId);
    }

    // ── Request body ──────────────────────────────────────────────────────────

    public static class CreateFindingRequest {

        @NotBlank(message = "title must not be blank")
        private String title;

        private String severity;
        private String category;
        private String file;
        private int    line;
        private String description;
        private String suggestion;

        public String getTitle()                  { return title; }
        public void   setTitle(String t)          { this.title = t; }

        public String getSeverity()               { return severity; }
        public void   setSeverity(String s)       { this.severity = s; }

        public String getCategory()               { return category; }
        public void   setCategory(String c)       { this.category = c; }

        public String getFile()                   { return file; }
        public void   setFile(String f)           { this.file = f; }

        public int  getLine()                     { return line; }
        public void setLine(int l)                { this.line = l; }

        public String getDescription()            { return description; }
        public void   setDescription(String d)    { this.description = d; }

        public String getSuggestion()             { return suggestion; }
        public void   setSuggestion(String s)     { this.suggestion = s; }
    }
}
