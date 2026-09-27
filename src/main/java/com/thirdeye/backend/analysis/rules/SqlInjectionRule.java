package com.thirdeye.backend.analysis.rules;

import com.thirdeye.backend.analysis.AnalysisRule;
import com.thirdeye.backend.analysis.DiffLine;
import com.thirdeye.backend.analysis.RuleFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule SQLI-001 — SQL query built by string concatenation.
 *
 * <h3>What it detects</h3>
 * Lines where a string literal that begins with a SQL keyword
 * ({@code SELECT}, {@code INSERT}, {@code UPDATE}, {@code DELETE},
 * {@code MERGE}) is concatenated with a variable or expression using
 * {@code +} (Java) or {@code ||} (SQL/Groovy string concat).
 *
 * <h3>Confidence signal</h3>
 * A match is strengthened when the same line also references
 * {@code Statement} (not {@code PreparedStatement}) or executes via
 * {@code .execute(} / {@code .executeQuery(}, which indicates the
 * concatenated string is actually sent to the database driver.
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Pattern-match only — no data-flow or taint tracking.</li>
 *   <li>Multi-line query construction is not detected if individual
 *       lines do not each contain a SQL keyword.</li>
 *   <li>ORMs (Hibernate HQL, Spring Data JPQL) that use string concat
 *       may trigger a false positive.</li>
 * </ul>
 */
public class SqlInjectionRule implements AnalysisRule {

    // SQL keyword at the start of a string literal
    private static final Pattern SQL_STRING_START = Pattern.compile(
            "\"\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE)\\b",
            Pattern.CASE_INSENSITIVE);

    // String concatenation operator in the same line
    private static final Pattern CONCAT_OP = Pattern.compile("\\+|\\|\\|");

    // Unsafe execution: plain Statement (not PreparedStatement) or .execute(
    private static final Pattern UNSAFE_EXEC = Pattern.compile(
            "\\bStatement\\b(?!\\s*\\.\\s*prepare)|" +  // Statement but not PreparedStatement
            "\\.execute(?:Query|Update|Batch)?\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /**
     * Detects a Statement execute call passing a <em>variable</em> (not a string
     * literal). This catches the common two-line pattern:
     * <pre>
     *   String query = "SELECT ..." + userInput;   // line A (caught by concat check above)
     *   stmt.executeQuery(query);                   // line B (caught by THIS pattern)
     * </pre>
     * The pattern matches {@code .executeQuery(identifier)} but NOT
     * {@code .executeQuery("literal")} or {@code ps.executeQuery()} (PreparedStatement).
     *
     * <p>Not fired when the line also contains {@code prepareStatement} or
     * {@code PreparedStatement}, which indicates safe parameterised use.
     */
    private static final Pattern EXEC_WITH_VARIABLE = Pattern.compile(
            "\\.execute(?:Query|Update|Batch)?\\s*\\(\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*[,)]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PREPARED_STMT = Pattern.compile(
            "(?i)prepareStatement|PreparedStatement");

    @Override
    public String ruleId() { return "SQLI-001"; }

    @Override
    public String name() { return "SQL Injection via String Concatenation"; }

    @Override
    public List<RuleFinding> analyze(List<DiffLine> addedLines) {
        List<RuleFinding> findings = new ArrayList<>();

        for (DiffLine dl : addedLines) {
            String content = dl.getContent();

            // ── Pattern 1: SQL string concat on same line ──────────────────────
            boolean hasSqlKeyword = SQL_STRING_START.matcher(content).find();
            boolean hasConcat     = CONCAT_OP.matcher(content).find();

            if (hasSqlKeyword && hasConcat) {
                boolean hasUnsafeExec = UNSAFE_EXEC.matcher(content).find();
                String severity  = hasUnsafeExec ? "critical" : "high";
                String evidence  = truncate(content.trim(), 120);

                String description = String.format(
                        "[%s] A SQL query string appears to be built by concatenating " +
                        "non-literal values. This pattern is a classic SQL injection vector " +
                        "when untrusted input reaches the concatenated expression. " +
                        "Evidence: `%s`%s",
                        ruleId(),
                        evidence,
                        hasUnsafeExec
                            ? " A Statement (not PreparedStatement) execute call was also detected " +
                              "on this line, increasing confidence."
                            : " NOTE: This is a potential risk — confirm whether the concatenated " +
                              "values originate from untrusted input before treating as confirmed.");

                findings.add(RuleFinding.builder()
                        .ruleId(ruleId())
                        .title("SQL injection risk: query built by string concatenation")
                        .severity(severity)
                        .category("security")
                        .location(dl.getFile(), dl.getLineNumber())
                        .description(description)
                        .suggestion(
                                "Replace string concatenation with a parameterised query. " +
                                "Use java.sql.PreparedStatement with '?' placeholders, or " +
                                "Spring Data JPA @Query with named :param bindings. " +
                                "Never concatenate user-supplied values directly into SQL strings.")
                        .build());
                continue; // already flagged this line
            }

            // ── Pattern 2: Statement.executeXxx(variable) without PreparedStatement ──
            // Catches the execution half of a two-line SQL injection where the
            // query string was assembled on a previous line.
            if (!PREPARED_STMT.matcher(content).find()
                    && EXEC_WITH_VARIABLE.matcher(content).find()) {

                String evidence = truncate(content.trim(), 120);
                findings.add(RuleFinding.builder()
                        .ruleId(ruleId())
                        .title("SQL injection risk: Statement.execute called with variable argument")
                        .severity("high")
                        .category("security")
                        .location(dl.getFile(), dl.getLineNumber())
                        .description(String.format(
                                "[%s] A JDBC Statement execute method is called with a variable " +
                                "argument rather than a string literal or PreparedStatement. " +
                                "If the variable was constructed by concatenating user-supplied input, " +
                                "this is a SQL injection vulnerability (CWE-89). Evidence: `%s`",
                                ruleId(), evidence))
                        .suggestion(
                                "Use java.sql.PreparedStatement with '?' placeholders instead of " +
                                "passing a dynamically-built string to Statement.execute. " +
                                "Bind user-supplied values as parameters, never via concatenation.")
                        .build());
            }
        }

        return findings;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
