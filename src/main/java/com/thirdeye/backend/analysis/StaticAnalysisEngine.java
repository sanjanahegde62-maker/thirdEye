package com.thirdeye.backend.analysis;

import com.thirdeye.backend.analysis.rules.DangerousExecutionRule;
import com.thirdeye.backend.analysis.rules.HardcodedCredentialRule;
import com.thirdeye.backend.analysis.rules.SqlInjectionRule;
import com.thirdeye.backend.analysis.rules.UnsafeDeserializationRule;
import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.entity.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the static analysis pipeline for a submitted code diff.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>{@link DiffParser} extracts all <em>added</em> lines with file/line
 *       metadata from the unified diff.</li>
 *   <li>Each registered {@link AnalysisRule} is applied to the extracted
 *       lines.</li>
 *   <li>{@link RuleFinding}s are converted to {@link Finding} entities
 *       ready for persistence.</li>
 * </ol>
 *
 * <h3>What this engine is NOT</h3>
 * <ul>
 *   <li>It is <strong>not</strong> an AI or LLM-based analyser.</li>
 *   <li>It does <strong>not</strong> perform full language parsing, type
 *       inference, or cross-file data-flow analysis.</li>
 *   <li>Each rule operates on regex/text pattern matching against individual
 *       added diff lines. A match indicates a <em>potential</em> issue.</li>
 *   <li>Supported input: unified diff format (output of {@code git diff})
 *       or raw source lines with a leading {@code +} on each line.</li>
 * </ul>
 *
 * <p>To add a new rule, implement {@link AnalysisRule} and add an instance
 * to the {@code rules} list in the constructor.
 */
@Component
public class StaticAnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisEngine.class);

    private final List<AnalysisRule> rules;

    public StaticAnalysisEngine() {
        this.rules = List.of(
                new SqlInjectionRule(),
                new HardcodedCredentialRule(),
                new DangerousExecutionRule(),
                new UnsafeDeserializationRule()
        );
    }

    /**
     * Analyse the diff and return {@link Finding} entities (not yet persisted).
     *
     * @param review the review entity (used to set the FK on each finding)
     * @param diff   unified diff text; may be blank or {@code null}
     * @return ordered list of findings, possibly empty; never {@code null}
     */
    public List<Finding> analyze(Review review, String diff) {
        List<Finding> findings = new ArrayList<>();

        if (diff == null || diff.isBlank()) {
            log.info("[ANALYSIS] Empty diff for review {} — returning empty-diff finding", review.getId());
            findings.add(emptyDiffFinding(review));
            return findings;
        }

        List<DiffLine> addedLines = DiffParser.parse(diff);
        log.info("[ANALYSIS] review={} — {} added lines parsed from diff", review.getId(), addedLines.size());

        for (AnalysisRule rule : rules) {
            try {
                List<RuleFinding> ruleResults = rule.analyze(addedLines);
                log.debug("[ANALYSIS] review={} rule={} — {} findings", review.getId(), rule.ruleId(), ruleResults.size());
                for (RuleFinding rf : ruleResults) {
                    findings.add(toEntity(review, rf));
                }
            } catch (Exception ex) {
                // An individual rule failure must not abort the whole analysis run.
                log.error("[ANALYSIS] Rule {} threw an unexpected exception for review {}: {}",
                        rule.ruleId(), review.getId(), ex.getMessage(), ex);
            }
        }

        log.info("[ANALYSIS] review={} — analysis complete, {} total findings", review.getId(), findings.size());
        return findings;
    }

    /** Returns the names of all registered rules (useful for documentation/logging). */
    public List<String> registeredRuleIds() {
        return rules.stream().map(AnalysisRule::ruleId).toList();
    }

    // ── conversion ────────────────────────────────────────────────────────────

    private static Finding toEntity(Review review, RuleFinding rf) {
        return new Finding(
                review,
                rf.getTitle(),
                rf.getSeverity(),
                rf.getCategory(),
                rf.getFile(),
                rf.getLine(),
                rf.getDescription(),
                rf.getSuggestion()
        );
    }

    private static Finding emptyDiffFinding(Review review) {
        return new Finding(
                review,
                "No code changes submitted",
                "low",
                "quality",
                "unknown",
                0,
                "The submitted diff was empty or contained no content. " +
                "ThirdEye static analysis requires actual code changes to analyse.",
                "Submit a non-empty unified diff (e.g. the output of 'git diff') " +
                "to receive security and quality findings."
        );
    }
}
