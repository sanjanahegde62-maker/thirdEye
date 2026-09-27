package com.thirdeye.backend.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a unified diff ({@code git diff} output) and extracts every
 * <em>added</em> line together with its file path and target line number.
 *
 * <h3>Parsing algorithm</h3>
 * <ol>
 *   <li>Track the current file by scanning {@code +++ b/...} headers.</li>
 *   <li>Parse hunk headers ({@code @@ -a,b +c,d @@}) to determine the
 *       starting target line number for the hunk.</li>
 *   <li>For each line in the hunk:
 *       <ul>
 *         <li>{@code +} prefix → added line; record file + current target
 *             line counter, advance counter.</li>
 *         <li>{@code -} prefix → removed line; do NOT advance target
 *             counter.</li>
 *         <li>space prefix → context line; advance target counter.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><strong>Limitations</strong>: binary diffs, {@code diff --stat} output,
 * and non-standard diff formats are not handled; the parser silently skips
 * unrecognised lines.  If the input is not a proper unified diff (e.g. the
 * user pastes raw code), lines that start with {@code +} and are not preceded
 * by a hunk header are collected with {@code lineNumber = 0} and
 * {@code file = "unknown"}.
 */
public final class DiffParser {

    /**
     * {@code @@ -oldStart[,oldLen] +newStart[,newLen] @@} — only the
     * {@code +newStart} component is captured (group 1).
     */
    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

    // private constructor — utility class
    private DiffParser() {}

    /**
     * Returns {@code true} when the text looks like a proper unified diff
     * (contains at least one {@code +++} file header or {@code @@} hunk header).
     *
     * <p>Used to select between unified-diff parsing and raw-code fallback.
     */
    static boolean isUnifiedDiff(String text) {
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("+++ ") || line.startsWith("@@")) return true;
        }
        return false;
    }

    /**
     * Parse the supplied diff text and return all added lines with metadata.
     *
     * <p>Two modes:
     * <ol>
     *   <li><strong>Unified diff</strong>: standard git-diff format — only
     *       lines with a leading {@code +} (added lines) are collected.</li>
     *   <li><strong>Raw source fallback</strong>: when the input contains no
     *       diff markers ({@code +++ } or {@code @@}), every non-blank line is
     *       treated as an added line so that pasted raw Java code is still
     *       analysed.  File will be {@code "unknown"} and line numbers are
     *       1-based positions within the pasted text.</li>
     * </ol>
     *
     * @param diff unified diff text or raw source code; may be {@code null} or empty.
     * @return ordered list of added {@link DiffLine}s; empty when nothing
     *         could be parsed.
     */
    public static List<DiffLine> parse(String diff) {
        List<DiffLine> result = new ArrayList<>();
        if (diff == null || diff.isBlank()) return result;

        // ── Raw-source fallback ───────────────────────────────────────────
        // When the submitted text is not a unified diff (no +++ or @@ markers),
        // treat every non-blank line as an added line so that users who paste
        // raw Java source still receive findings.
        if (!isUnifiedDiff(diff)) {
            String[] lines = diff.split("\n", -1);
            int lineNum = 1;
            for (String raw : lines) {
                if (!raw.isBlank()) {
                    result.add(new DiffLine("unknown", lineNum, raw));
                }
                lineNum++;
            }
            return result;
        }

        String[] lines  = diff.split("\n", -1);
        String   file   = "unknown";
        int      target = 0;      // current target (new file) line counter

        for (String raw : lines) {

            // ── File header ─────────────────────────────────────────────
            if (raw.startsWith("+++ ")) {
                file   = extractFile(raw);
                target = 0;
                continue;
            }
            // Skip "--- " header lines
            if (raw.startsWith("--- ")) {
                continue;
            }

            // ── Hunk header ─────────────────────────────────────────────
            if (raw.startsWith("@@")) {
                Matcher m = HUNK_HEADER.matcher(raw);
                if (m.find()) {
                    target = Integer.parseInt(m.group(1));
                } else {
                    target = 0; // can't determine position
                }
                continue;
            }

            // ── Added line ──────────────────────────────────────────────
            if (raw.startsWith("+")) {
                String content = raw.substring(1); // strip leading '+'
                result.add(new DiffLine(file, target, content));
                if (target > 0) target++;
                continue;
            }

            // ── Removed line ─────────────────────────────────────────────
            if (raw.startsWith("-")) {
                // removed line: target counter does NOT advance
                continue;
            }

            // ── Context line (space prefix or bare line) ─────────────────
            // Both space-prefixed context lines and diff meta-headers land here.
            // Only advance the counter for actual content lines (space prefix).
            if (raw.startsWith(" ") && target > 0) {
                target++;
            }
        }

        return result;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Extract the file path from a {@code +++ b/path} or {@code +++ path} line.
     * Returns {@code "unknown"} if the line cannot be parsed.
     */
    static String extractFile(String headerLine) {
        // Strip the '+++ ' prefix
        String path = headerLine.startsWith("+++ ") ? headerLine.substring(4) : headerLine;
        // Remove 'b/' git prefix if present
        if (path.startsWith("b/")) path = path.substring(2);
        path = path.trim();
        return path.isEmpty() ? "unknown" : path;
    }
}
