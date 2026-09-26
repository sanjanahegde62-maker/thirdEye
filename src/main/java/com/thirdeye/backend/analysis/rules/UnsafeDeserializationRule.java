package com.thirdeye.backend.analysis.rules;

import com.thirdeye.backend.analysis.AnalysisRule;
import com.thirdeye.backend.analysis.DiffLine;
import com.thirdeye.backend.analysis.RuleFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule DESER-001 — Unsafe Java deserialization.
 *
 * <h3>What it detects</h3>
 * Added lines that use Java's native serialization deserialization APIs or
 * known unsafe deserialization sinks:
 * <ul>
 *   <li>{@code ObjectInputStream} construction or {@code readObject()} call.</li>
 *   <li>{@code ObjectInputStream.readUnshared()}</li>
 *   <li>XStream's {@code fromXML()} — known to have been exploitable without
 *       allowlisting.</li>
 *   <li>{@code SerializationUtils.deserialize()} from Apache Commons Lang.</li>
 * </ul>
 *
 * <h3>Confidence signal</h3>
 * Severity is elevated to {@code high} if the input to the deserialization
 * call appears to come from a network or user-supplied stream
 * ({@code request}, {@code socket}, {@code inputStream} variable name,
 * or {@code getInputStream()}).
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>JSON/XML frameworks (Jackson, Gson) use a different mechanism
 *       and are not flagged by this rule.</li>
 *   <li>Multi-line construction split across lines is not captured.</li>
 * </ul>
 */
public class UnsafeDeserializationRule implements AnalysisRule {

    /** Deserialization API calls. */
    private static final Pattern DESER_API = Pattern.compile(
            "new\\s+ObjectInputStream\\s*\\(|" +
            "\\.readObject\\s*\\(|" +
            "\\.readUnshared\\s*\\(|" +
            "\\bfromXML\\s*\\(|" +
            "SerializationUtils\\.deserialize\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** Signals that the source stream may be network / user-supplied. */
    private static final Pattern NETWORK_SOURCE = Pattern.compile(
            "\\brequest\\b|getInputStream\\s*\\(|\\bsocket\\b|\\binputStream\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String ruleId() { return "DESER-001"; }

    @Override
    public String name() { return "Unsafe Deserialization"; }

    @Override
    public List<RuleFinding> analyze(List<DiffLine> addedLines) {
        List<RuleFinding> findings = new ArrayList<>();

        for (DiffLine dl : addedLines) {
            String content = dl.getContent();
            if (isPureComment(content)) continue;

            if (!DESER_API.matcher(content).find()) continue;

            boolean networkSource = NETWORK_SOURCE.matcher(content).find();
            String  severity      = networkSource ? "high" : "medium";
            String  evidence      = truncate(content.trim(), 120);

            findings.add(RuleFinding.builder()
                    .ruleId(ruleId())
                    .title("Unsafe deserialization API usage detected")
                    .severity(severity)
                    .category("security")
                    .location(dl.getFile(), dl.getLineNumber())
                    .description(String.format(
                            "[%s] A Java native serialization deserialization API was found " +
                            "in added code. Deserializing untrusted data using " +
                            "ObjectInputStream or similar APIs can lead to remote code " +
                            "execution if the classpath contains gadget chains (cf. " +
                            "Apache Commons Collections, Spring Framework). " +
                            "Evidence: `%s`%s",
                            ruleId(), evidence,
                            networkSource
                                ? " A network/request stream reference was detected on the same " +
                                  "line, increasing the likelihood that the input is user-controlled."
                                : " Could not confirm the stream source from this line alone."))
                    .suggestion(
                            "Avoid Java native serialization for untrusted data. " +
                            "Prefer JSON (Jackson with type validation) or Protocol Buffers. " +
                            "If ObjectInputStream must be used: implement a " +
                            "resolveClass() override that enforces an allowlist of " +
                            "permitted classes, and never deserialise data from " +
                            "untrusted network sources without integrity verification.")
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
