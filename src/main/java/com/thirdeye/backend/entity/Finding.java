package com.thirdeye.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "findings")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "review_id", nullable = false)
    @JsonIgnoreProperties({"findings", "codeDiff", "project"})
    private Review review;

    @Column(nullable = false)
    private String title;

    private String severity;

    private String category;

    private String file;

    private int line;

    @Column(length = 5000)
    private String description;

    @Column(length = 5000)
    private String suggestion;

    public Finding() {
    }

    public Finding(
            Review review,
            String title,
            String severity,
            String category,
            String file,
            int line,
            String description,
            String suggestion) {
        this.review = review;
        this.title = title;
        this.severity = severity;
        this.category = category;
        this.file = file;
        this.line = line;
        this.description = description;
        this.suggestion = suggestion;
    }

    public Long getId() {
        return id;
    }

    public Review getReview() {
        return review;
    }

    public void setReview(Review review) {
        this.review = review;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}