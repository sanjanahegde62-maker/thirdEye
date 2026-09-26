package com.thirdeye.backend.analysis.rules;

import com.thirdeye.backend.analysis.AnalysisRule;
import com.thirdeye.backend.analysis.DiffLine;
import com.thirdeye.backend.analysis.RuleFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule CRED-001 — Hardcoded password, API key, token, or secret literal.
 *
 * <h3>What it detects</h3>
 * Lines that assign a string literal to an identifier whose name suggests
 * a credential ({@code password}, {@code passwd}, {@code secret},
 * {@code apikey}, {@code api_key}, {@code token}, {@code authtoken},
 * {@code access_key}, {@code private_key}).
 *
 * <h3>False-positive reduction</h3>
 * <ul>
 *   <li>Lines where the value is an empty string ({@code ""} or {@code ''})
 *       are skipped — empty is a placeholder, not a real secret.</li>
 *   <li>Lines where the value is a well-known placeholder
 *       ({@code <...>}, {@code ${...}}, {@code %s}, {@code ?},
 *       {@code changeme}, {@code yourpassword}, {@code example},
 *       {@code placeholder}, {@code todo}, {@code fixme}) are skipped.</li>
 *   <li>Lines inside obvious unit-test files (path ends with
 *       {@code Test.java} or {@code Tests.java}) are reported at
 *       {@code low} severity rather than {@code critical}.</li>
 *   <li>Lines that are pure comments are skipped.</li>
 * </ul>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Does not inspect values passed via constructors or method
 *       arguments unless the argument contains a quoted literal.</li>
 *   <li>Environment-variable lookups ({@code System.getenv(...)}) are
 *       not flagged — those are intentionally safe.</li>
 * </ul>
 */
public class HardcodedCredentialRule implements AnalysisRule {

    /**
     * Matches: identifier whose name is credential-like, followed by
     * {@code =}, {@code :}, or in a method-call context,
     * then a quoted string literal.
     *
     * Group 1: the credential-like keyword
     * Group 2: the literal value (content between the quotes)
     */
    private static final Pattern CREDENTIAL_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|secret|api[_-]?key|token|auth[_-]?token" +
            "|access[_-]?key|private[_-]?key)\\b" +
            "\\s*(?:=|:)\\s*" +
            "[\"']([^\"']{1,200})[\"']"
    );

    /**
     * Detects credential string literals passed as positional arguments to
     * known authentication / connection methods, e.g.:
     * {@code DriverManager.getConnection(url, "root", "mypassword")}
     * {@code authenticate("admin", "secret123")}
     */
    private static final Pattern CREDENTIAL_METHOD_ARG = Pattern.compile(
            "(?i)(?:getConnection|authenticate|login|connect|createConnection|openConnection)" +
            "\\s*\\([^)]*[\"']([^\"']{1,200})[\"']\\s*,\\s*[\"']([^\"']{1,200})[\"']"
    );

    /**
     * Detects a line that is a continuation of a DB connection call, i.e.
     * contains a JDBC URL and two more quoted string literals (user + password).
     * Handles split-line patterns like:
     * {@code     "jdbc:mysql://...", "root", "password");}
     */
    private static final Pattern JDBC_CREDENTIAL_LINE = Pattern.compile(
            "(?i)[\"']jdbc:[^\"']+[\"']\\s*,\\s*[\"']([^\"']{1,200})[\"']\\s*,\\s*[\"']([^\"']{1,200})[\"']"
    );

    /** Known safe placeholder values (checked case-insensitively, exact match). */
    private static final List<String> PLACEHOLDERS = List.of(
            "changeme", "yourpassword", "your_password", "placeholder",
            "todo", "fixme", "insert_here", "replace_me", "secret_here",
            "password_here", "token_here", "xxxx", "****"
    );

    /**
     * Placeholder values where substring containment is checked
     * (short, generic words that are unambiguously not real secrets).
     */
    private static final List<String> PLACEHOLDER_SUBSTRINGS = List.of(
            "<placeholder>", "${", "%s", "%d"
    );

    /** Template / config-file substitution patterns: ${...}, %s, <...>, {env:...} */
    private static final Pattern TEMPLATE_VALUE = Pattern.compile(
            "^\\s*(?:\\$\\{[^}]+}|%[sd]|<[^>]+>|\\{[^}]+})\\s*$"
    );

    @Override
    public String ruleId() { return "CRED-001"; }

    @Override
    public String name() { return "Hardcoded Credential Literal"; }

    @Override
    public List<RuleFinding> analyze(List<DiffLine> addedLines) {
        List<RuleFinding> findings = new ArrayList<>();

        for (DiffLine dl : addedLines) {
            String content = dl.getContent();

            // Skip pure comment lines
            if (isPureComment(content)) continue;

            // System.getenv / System.getProperty calls are not hardcoded
            if (content.contains("System.getenv") || content.contains("System.getProperty")
                    || content.contains("env.get") || content.contains("getenv(")) continue;

            boolean isTestFile = dl.getFile() != null &&
                    (dl.getFile().endsWith("Test.java") || dl.getFile().endsWith("Tests.java"));

            // ── Pattern 1: assignment  (password = "value") ───────────────────
            Matcher m = CREDENTIAL_ASSIGNMENT.matcher(content);
            while (m.find()) {
                String keyword = m.group(1);
                String value   = m.group(2);

                if (isSuppressedValue(value)) continue;

                addFinding(findings, dl, keyword, isTestFile, content);
            }

            // ── Pattern 2: positional args  (getConnection(url, "user", "pwd")) ─
            Matcher ma = CREDENTIAL_METHOD_ARG.matcher(content);
            while (ma.find()) {
                // group(2) is the last (password) argument
                String value = ma.group(2);
                if (isSuppressedValue(value)) continue;

                addFinding(findings, dl, "password", isTestFile, content);
            }

            // ── Pattern 3: JDBC continuation line  ("jdbc:...", "user", "pwd") ─
            Matcher mj = JDBC_CREDENTIAL_LINE.matcher(content);
            while (mj.find()) {
                String value = mj.group(2);  // password is the 3rd quoted arg
                if (isSuppressedValue(value)) continue;

                addFinding(findings, dl, "password", isTestFile, content);
            }
        }

        return findings;
    }

    private void addFinding(List<RuleFinding> findings, DiffLine dl,
                            String keyword, boolean isTestFile, String content) {
        String severity = isTestFile ? "low" : "critical";
        String evidence = truncate(content.trim(), 120);
        findings.add(RuleFinding.builder()
                .ruleId(ruleId())
                .title("Hardcoded " + keyword.toLowerCase() + " literal detected")
                .severity(severity)
                .category("security")
                .location(dl.getFile(), dl.getLineNumber())
                .description(String.format(
                        "[%s] A string literal that appears to be a credential was found " +
                        "assigned to an identifier named '%s'. Hardcoded secrets in source " +
                        "code are exposed to anyone with repository access and are " +
                        "permanently preserved in commit history even after deletion. " +
                        "Evidence: `%s`%s",
                        ruleId(), keyword, evidence,
                        isTestFile ? " (Found in test file — severity downgraded to low.)" : ""))
                .suggestion(
                        "Remove the literal value and load it at runtime from a " +
                        "secure source: an environment variable (System.getenv), " +
                        "a Spring @Value property backed by a secrets manager " +
                        "(e.g. HashiCorp Vault, AWS Secrets Manager, Azure Key Vault), " +
                        "or a configuration service. Rotate the secret if it was " +
                        "already committed.")
                .build());
    }

    private boolean isSuppressedValue(String value) {
        if (value == null || value.isEmpty()) return true;
        if (TEMPLATE_VALUE.matcher(value).matches()) return true;
        String lower = value.toLowerCase();
        // Exact-match placeholders
        for (String ph : PLACEHOLDERS) {
            if (lower.equals(ph)) return true;
        }
        // Substring placeholders (only short, unambiguous markers)
        for (String sub : PLACEHOLDER_SUBSTRINGS) {
            if (lower.contains(sub.toLowerCase())) return true;
        }
        return false;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean isPureComment(String content) {
        String trimmed = content.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("*")
                || trimmed.startsWith("/*") || trimmed.startsWith("#");
    }


    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
