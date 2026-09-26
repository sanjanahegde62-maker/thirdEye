package com.thirdeye.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/**
 * Represents a generated test case associated with a review.
 *
 * <h3>Status values</h3>
 * <ul>
 *   <li>{@code NOT_RUN} — test was generated but never executed (default for
 *       all deterministically generated tests).</li>
 *   <li>{@code PASSED}  — test was executed and passed.</li>
 *   <li>{@code FAILED}  — test was executed and failed.</li>
 *   <li>{@code PENDING} — legacy value kept for backward compatibility with
 *       older records in the database.</li>
 * </ul>
 *
 * <p>Tests are generated deterministically from finding metadata (rule ID,
 * file, line, evidence). No AI model is involved.
 */
@Entity
@Table(name = "generated_tests")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class GeneratedTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "review_id", nullable = false)
    @JsonIgnoreProperties({"findings", "codeDiff", "project"})
    private Review review;

    /** Human-readable test name, e.g. "should reject SQL query built by string concatenation". */
    @Column(nullable = false)
    private String name;

    /** Relative path where the generated test file would live. */
    private String file;

    /**
     * Execution status.
     * Values: NOT_RUN | PASSED | FAILED | PENDING (legacy)
     * All newly generated tests start as NOT_RUN.
     */
    private String status = "NOT_RUN";

    /** Duration string, e.g. "14ms". Null for tests that have not been run. */
    private String duration;

    /** Optional reference to the Finding id that this test covers. */
    private String linkedFindingId;

    /** The generated test source code. */
    @Column(length = 10000)
    private String testCode;

    /**
     * Short label for the type/template of generated test, e.g.
     * {@code "SQLI-001"}, {@code "CRED-001"}, {@code "EXEC-001"},
     * {@code "DESER-001"}, {@code "COVERAGE"}, {@code "SCAFFOLD"}.
     */
    private String testType;

    /**
     * Error output or failure message from the last execution.
     * Null when status is NOT_RUN or PASSED.
     */
    @Column(length = 2000)
    private String errorOutput;

    public GeneratedTest() {}

    public GeneratedTest(Review review, String name, String file,
                         String status, String duration,
                         String linkedFindingId, String testCode) {
        this.review = review;
        this.name = name;
        this.file = file;
        this.status = status;
        this.duration = duration;
        this.linkedFindingId = linkedFindingId;
        this.testCode = testCode;
    }

    public Long getId()                  { return id; }
    public Review getReview()            { return review; }
    public void setReview(Review r)      { this.review = r; }
    public String getName()              { return name; }
    public void setName(String n)        { this.name = n; }
    public String getFile()              { return file; }
    public void setFile(String f)        { this.file = f; }
    public String getStatus()            { return status; }
    public void setStatus(String s)      { this.status = s; }
    public String getDuration()          { return duration; }
    public void setDuration(String d)    { this.duration = d; }
    public String getLinkedFindingId()   { return linkedFindingId; }
    public void setLinkedFindingId(String id) { this.linkedFindingId = id; }
    public String getTestCode()          { return testCode; }
    public void setTestCode(String c)    { this.testCode = c; }
    public String getTestType()          { return testType; }
    public void setTestType(String t)    { this.testType = t; }
    public String getErrorOutput()       { return errorOutput; }
    public void setErrorOutput(String e) { this.errorOutput = e; }
}
