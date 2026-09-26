
package com.thirdeye.backend.analysis.rules;

import com.thirdeye.backend.analysis.AnalysisRule;
import com.thirdeye.backend.analysis.DiffLine;
import com.thirdeye.backend.analysis.RuleFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule CRED-001 — Detect hardcoded passwords, API keys, tokens,
 * and secret literals in added code lines.
 */
public class HardcodedCredentialRule implements AnalysisRule {

    /*
     * Matches credential-like identifiers, including:
     *
     * password = "secret123"
     * adminPassword = "secret123"
     * dbPassword = "secret123"
     * apiKey = "key123"
     * accessToken = "token123"
     * private_key = "key123"
     *
     * Group 1: credential keyword
     * Group 2: literal value
     */
    private static final Pattern CREDENTIAL_ASSIGNMENT =
            Pattern.compile(
                    "(?i)\\b[A-Za-z_$]*?(password|passwd|secret|api[_-]?key|token|auth[_-]?token"
                            + "|access[_-]?key|private[_-]?key)\\b"
                            + "\\s*(?:=|:)\\s*"
                            + "[\"']([^\"']{1,200})[\"']"
            );

    /*
     * Detect credentials passed as arguments to common
     * authentication or database connection methods.
     */
    private static final Pattern CREDENTIAL_METHOD_ARG =
            Pattern.compile(
                    "(?i)(?:getConnection|authenticate|login|connect|"
                            + "createConnection|openConnection)"
                            + "\\s*\\([^)]*[\"']([^\"']{1,200})[\"']"
                            + "\\s*,\\s*[\"']([^\"']{1,200})[\"']"
            );

    /*
     * Detect JDBC connection strings containing username
     * and password literals.
     */
    private static final Pattern JDBC_CREDENTIAL_LINE =
            Pattern.compile(
                    "(?i)[\"']jdbc:[^\"']+[\"']"
                            + "\\s*,\\s*[\"']([^\"']{1,200})[\"']"
                            + "\\s*,\\s*[\"']([^\"']{1,200})[\"']"
            );

    private static final List<String> PLACEHOLDERS = List.of(
            "changeme",
            "yourpassword",
            "your_password",
            "placeholder",
            "todo",
            "fixme",
            "insert_here",
            "replace_me",
            "secret_here",
            "password_here",
            "token_here",
            "xxxx",
            "****",
            "example",
            "yourpasswordhere"
    );

    private static final List<String> PLACEHOLDER_SUBSTRINGS =
            List.of(
                    "<placeholder>",
                    "${",
                    "%s",
                    "%d"
            );

    private static final Pattern TEMPLATE_VALUE =
            Pattern.compile(
                    "^\\s*(?:\\$\\{[^}]+}|%[sd]|<[^>]+>|\\{[^}]+})\\s*$"
            );

    @Override
    public String ruleId() {
        return "CRED-001";
    }

    @Override
    public String name() {
        return "Hardcoded Credential Literal";
    }

    @Override
    public List<RuleFinding> analyze(List<DiffLine> addedLines) {

        List<RuleFinding> findings = new ArrayList<>();

        if (addedLines == null) {
            return findings;
        }

        for (DiffLine dl : addedLines) {

            if (dl == null || dl.getContent() == null) {
                continue;
            }

            String content = dl.getContent();

            // Ignore pure comments.
            if (isPureComment(content)) {
                continue;
            }

            // Ignore environment-variable lookups.
            if (content.contains("System.getenv")
                    || content.contains("System.getProperty")
                    || content.contains("env.get")
                    || content.contains("getenv(")) {
                continue;
            }

            boolean isTestFile =
                    dl.getFile() != null
                            && (dl.getFile().endsWith("Test.java")
                            || dl.getFile().endsWith("Tests.java"));

            // Pattern 1: Credential assignments.
            Matcher matcher =
                    CREDENTIAL_ASSIGNMENT.matcher(content);

            while (matcher.find()) {

                String keyword = matcher.group(1);
                String value = matcher.group(2);

                if (isSuppressedValue(value)) {
                    continue;
                }

                addFinding(
                        findings,
                        dl,
                        keyword,
                        isTestFile,
                        content
                );
            }

            // Pattern 2: Credentials passed to authentication methods.
            Matcher methodMatcher =
                    CREDENTIAL_METHOD_ARG.matcher(content);

            while (methodMatcher.find()) {

                String value = methodMatcher.group(2);

                if (isSuppressedValue(value)) {
                    continue;
                }

                addFinding(
                        findings,
                        dl,
                        "password",
                        isTestFile,
                        content
                );
            }

            // Pattern 3: JDBC connection credentials.
            Matcher jdbcMatcher =
                    JDBC_CREDENTIAL_LINE.matcher(content);

            while (jdbcMatcher.find()) {

                String value = jdbcMatcher.group(2);

                if (isSuppressedValue(value)) {
                    continue;
                }

                addFinding(
                        findings,
                        dl,
                        "password",
                        isTestFile,
                        content
                );
            }
        }

        return findings;
    }

    private void addFinding(
            List<RuleFinding> findings,
            DiffLine dl,
            String keyword,
            boolean isTestFile,
            String content) {

        String severity =
                isTestFile ? "low" : "critical";

        String evidence =
                truncate(content.trim(), 120);

        RuleFinding finding =
                RuleFinding.builder()
                        .ruleId(ruleId())
                        .title(
                                "Hardcoded "
                                        + keyword.toLowerCase()
                                        + " literal detected"
                        )
                        .severity(severity)
                        .category("security")
                        .location(
                                dl.getFile(),
                                dl.getLineNumber()
                        )
                        .description(
                                String.format(
                                        "[%s] A string literal that appears "
                                                + "to be a credential was found "
                                                + "assigned to an identifier named "
                                                + "'%s'. Hardcoded secrets in source "
                                                + "code can be exposed to anyone with "
                                                + "repository access and may remain "
                                                + "in commit history after deletion. "
                                                + "Evidence: `%s`%s",
                                        ruleId(),
                                        keyword,
                                        evidence,
                                        isTestFile
                                                ? " (Found in test file; "
                                                + "severity downgraded to low.)"
                                                : ""
                                )
                        )
                        .suggestion(
                                "Remove the hardcoded value and load it "
                                        + "at runtime from a secure source, such "
                                        + "as an environment variable using "
                                        + "System.getenv, a Spring @Value property "
                                        + "backed by a secrets manager, or a "
                                        + "configuration service. Rotate the "
                                        + "secret if it was already committed."
                        )
                        .build();

        findings.add(finding);
    }

    private boolean isSuppressedValue(String value) {

        if (value == null || value.isEmpty()) {
            return true;
        }

        if (TEMPLATE_VALUE.matcher(value).matches()) {
            return true;
        }

        String lower = value.toLowerCase();

        for (String placeholder : PLACEHOLDERS) {

            if (lower.equals(placeholder)) {
                return true;
            }
        }

        for (String placeholder : PLACEHOLDER_SUBSTRINGS) {

            if (lower.contains(placeholder.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPureComment(String content) {

        String trimmed = content.stripLeading();

        return trimmed.startsWith("//")
                || trimmed.startsWith("*")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("#");
    }

    private static String truncate(
            String value,
            int maxLength) {

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength) + "…";
    }
}