package com.thirdeye.backend.analysis;

import java.util.List;

/**
 * Contract for a single static-analysis rule.
 *
 * <p>Each rule receives the fully-parsed diff as a list of {@link DiffLine}
 * objects (one per added line, with file name and target line number already
 * resolved) and returns zero or more {@link RuleFinding}s.
 *
 * <p><strong>Limitations</strong>: rules operate on regex/text patterns
 * applied line-by-line to the diff. They do NOT perform full AST parsing,
 * type inference, data-flow analysis, or cross-file analysis.  A match
 * indicates a <em>potential</em> issue, not a confirmed vulnerability.
 */
public interface AnalysisRule {

    /**
     * Short, stable identifier for this rule, e.g. {@code "SQLI-001"}.
     * Exposed in finding descriptions so results are traceable.
     */
    String ruleId();

    /**
     * Human-readable name, e.g. {@code "SQL Injection via String Concatenation"}.
     */
    String name();

    /**
     * Analyse the supplied list of added diff lines and return any findings.
     *
     * @param addedLines lines that were added (prefix {@code +}) in the diff,
     *                   with file/line metadata attached. Never {@code null};
     *                   may be empty.
     * @return list of findings; empty if nothing was detected.
     */
    List<RuleFinding> analyze(List<DiffLine> addedLines);
}
