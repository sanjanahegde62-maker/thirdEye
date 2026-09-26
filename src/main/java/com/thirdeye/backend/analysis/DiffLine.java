package com.thirdeye.backend.analysis;

/**
 * A single added line extracted from a unified diff, with location metadata.
 *
 * <p>Instances are immutable and produced by {@link DiffParser}.
 */
public final class DiffLine {

    /** File path as reported by the diff header ({@code +++ b/...}). */
    private final String file;

    /**
     * Line number in the <em>target</em> (new) file, or {@code 0} when the
     * diff hunk header could not be parsed.
     */
    private final int lineNumber;

    /** Raw content of the added line, with the leading {@code +} stripped. */
    private final String content;

    public DiffLine(String file, int lineNumber, String content) {
        this.file       = file;
        this.lineNumber = lineNumber;
        this.content    = content;
    }

    /** @return file path, never {@code null}; {@code "unknown"} when not determinable. */
    public String getFile() { return file; }

    /** @return 1-based target line number, or {@code 0} when unknown. */
    public int getLineNumber() { return lineNumber; }

    /** @return the added line content (leading {@code +} already removed). */
    public String getContent() { return content; }

    @Override
    public String toString() {
        return file + ":" + lineNumber + " | " + content;
    }
}
