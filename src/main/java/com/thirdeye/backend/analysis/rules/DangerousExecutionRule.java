package com.thirdeye.backend.analysis.rules;

import com.thirdeye.backend.analysis.AnalysisRule;
import com.thirdeye.backend.analysis.DiffLine;
import com.thirdeye.backend.analysis.RuleFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule EXEC-001 — Dangerous OS command execution.
 *
 * <h3>What it detects</h3>
 * Added lines that invoke OS-level command execution APIs:
 * <ul>
 *   <li>{@code Runtime.getRuntime().exec(...)}</li>
 *   <li>{@code Runtime.exec(...)}</li>
 *   <li>{@code new ProcessBuilder(...).start()}</li>
 *   <li>{@code ProcessBuilder.start()}</li>
 * </ul>
 *
 * <h3>Why this matters</h3>
 * If the argument to {@code exec} or {@code ProcessBuilder} includes
 * user-controlled data, it enables OS command injection. Even when
 * arguments are static, shell-based invocations can be dangerous.
 *
 * <h3>Confidence levels</h3>
 * <ul>
 *   <li><strong>high</strong>: the exec argument visibly contains string
 *       concatenation ({@code +}) or a variable reference alongside the
 *       exec call, suggesting the command may be constructed from input.</li>
 *   <li><strong>medium</strong>: the exec call is present but the argument
 *       cannot be quickly shown to include dynamic input on the same line.</li>
 * </ul>
 *
 * <h3>Limitations</h3>
 * Multi-line ProcessBuilder construction and method calls split across
 * lines are not detected.
 */
public class DangerousExecutionRule implements AnalysisRule {

    /** Matches Runtime.exec or ProcessBuilder usage. */
    private static final Pattern EXEC_CALL = Pattern.compile(
            "\\bRuntime\\b.*\\.exec\\s*\\(|" +
            "new\\s+ProcessBuilder\\s*\\(|" +
            "\\bProcessBuilder\\b.*\\.start\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** Indicates the command argument may include concatenated/dynamic values. */
    private static final Pattern DYNAMIC_ARG = Pattern.compile(
            "\\+\\s*[a-zA-Z_]|[a-zA-Z_]\\w*\\s*\\+",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String ruleId() { return "EXEC-001"; }

    @Override
    public String name() { return "Dangerous OS Command Execution"; }

    @Override
    public List<RuleFinding> analyze(List<DiffLine> addedLines) {
        List<RuleFinding> findings = new ArrayList<>();

        for (DiffLine dl : addedLines) {
            String content = dl.getContent();
            if (isPureComment(content)) continue;

            if (!EXEC_CALL.matcher(content).find()) continue;

            boolean hasDynamic = DYNAMIC_ARG.matcher(content).find();
            String  severity   = hasDynamic ? "high" : "medium";
            String  evidence   = truncate(content.trim(), 120);

            findings.add(RuleFinding.builder()
                    .ruleId(ruleId())
                    .title("OS command execution call detected")
                    .severity(severity)
                    .category("security")
                    .location(dl.getFile(), dl.getLineNumber())
                    .description(String.format(
                            "[%s] An OS-level command execution API (Runtime.exec or ProcessBuilder) " +
                            "was found in added code. If the command or any of its arguments " +
                            "are derived from user input, this is an OS command injection vulnerability. " +
                            "Evidence: `%s`%s",
                            ruleId(), evidence,
                            hasDynamic
                                ? " String concatenation was detected in the same line as the exec call, " +
                                  "increasing the likelihood of a dynamic (user-controlled) command."
                                : " Could not confirm whether arguments are user-controlled from this " +
                                  "line alone — manual review recommended."))
                    .suggestion(
                            "Avoid Runtime.exec and ProcessBuilder with user-supplied input. " +
                            "If OS execution is necessary: (1) use an allowlist of permitted " +
                            "commands/arguments; (2) pass arguments as an array (not a shell " +
                            "string) to prevent shell interpretation; (3) validate and sanitise " +
                            "all inputs before use.")
                    .build());
        }

        return findings;
    }

    private static boolean isPureComment(String content) {
        String t = content.stripLeading();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("#");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
