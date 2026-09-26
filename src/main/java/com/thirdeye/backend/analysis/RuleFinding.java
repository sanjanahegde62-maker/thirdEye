package com.thirdeye.backend.analysis;

/**
 * A single finding produced by an {@link AnalysisRule}.
 *
 * <p>This is a plain data carrier; it is converted to a
 * {@link com.thirdeye.backend.entity.Finding} entity by
 * {@link StaticAnalysisEngine} before persistence.
 *
 * <p>All fields that map to existing entity columns are present; no new
 * database columns are required.
 */
public final class RuleFinding {

    private final String ruleId;
    private final String title;
    private final String severity;
    private final String category;
    private final String file;
    private final int    line;
    private final String description;
    private final String suggestion;

    private RuleFinding(Builder b) {
        this.ruleId      = b.ruleId;
        this.title       = b.title;
        this.severity    = b.severity;
        this.category    = b.category;
        this.file        = b.file;
        this.line        = b.line;
        this.description = b.description;
        this.suggestion  = b.suggestion;
    }

    public String getRuleId()      { return ruleId; }
    public String getTitle()       { return title; }
    public String getSeverity()    { return severity; }
    public String getCategory()    { return category; }
    public String getFile()        { return file; }
    public int    getLine()        { return line; }
    public String getDescription() { return description; }
    public String getSuggestion()  { return suggestion; }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String ruleId   = "UNKNOWN";
        private String title    = "";
        private String severity = "medium";
        private String category = "security";
        private String file     = "unknown";
        private int    line     = 0;
        private String description = "";
        private String suggestion  = "";

        public Builder ruleId(String v)      { this.ruleId = v;      return this; }
        public Builder title(String v)       { this.title = v;       return this; }
        public Builder severity(String v)    { this.severity = v;    return this; }
        public Builder category(String v)    { this.category = v;    return this; }
        public Builder file(String v)        { this.file = v;        return this; }
        public Builder line(int v)           { this.line = v;        return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder suggestion(String v)  { this.suggestion = v;  return this; }

        /** Location convenience: set file and line together. */
        public Builder location(String file, int line) {
            this.file = file;
            this.line = line;
            return this;
        }

        public RuleFinding build() { return new RuleFinding(this); }
    }
}
