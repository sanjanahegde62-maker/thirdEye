package com.thirdeye.backend.migration;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * ONE-TIME data-recovery utility.
 *
 * Restores the single historical review (ID 1) and its related findings and
 * generated tests into the current production H2 database.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  DO NOT run this while the Spring Boot application is running.          │
 * │  Stop the app first — H2 file-mode databases allow only one writer.    │
 * │                                                                         │
 * │  Run exactly once with:                                                 │
 * │    .\mvnw.cmd exec:java \                                               │
 * │      -Dexec.mainClass=com.thirdeye.backend.migration.RecoverHistoricalData │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Safety guarantees:
 *   • Checks PROJECTS ID 1 and ID 2 exist before starting.
 *   • Checks REVIEWS/FINDINGS/GENERATED_TESTS are empty (or the target IDs
 *     are absent) and aborts if any target record already exists.
 *   • Uses PreparedStatements for all multi-line string values — no string
 *     concatenation for data.
 *   • Wraps all inserts in a single transaction; rolls back on any error.
 *   • Advances IDENTITY sequences to the next safe value after the inserts.
 *   • Prints a verification summary at the end.
 */
public class RecoverHistoricalData {

    // ── JDBC coordinates ───────────────────────────────────────────────────
    // Must match spring.datasource.* in application.properties exactly.
    private static final String DB_URL =
            "jdbc:h2:file:C:/Users/sanja/Downloads/thirdeye-backend/data/thirdeye";
    private static final String DB_USER     = "sa";
    private static final String DB_PASSWORD = "";

    // ── Historical data constants ──────────────────────────────────────────

    // REVIEW
    private static final long   R_ID            = 1L;
    private static final long   R_PROJECT_ID    = 2L;  // Lost & Found
    private static final String R_TITLE         = "Lost & Found \u2013 Security and Code Quality Review";
    private static final String R_STATUS        = "COMPLETED";
    private static final int    R_FILES_CHANGED = 1;
    private static final int    R_LINES_ADDED   = 0;
    private static final int    R_LINES_REMOVED = 0;
    private static final int    R_COMMITS       = 1;
    private static final int    R_PROGRESS      = 100;
    private static final String R_CREATED_AT    = "2026-09-25 23:15:26.643749";
    private static final String R_UPDATED_AT    = "2026-09-25 23:15:26.746485";

    // The original CODE_DIFF — a Java snippet illustrating security issues.
    // Stored verbatim via PreparedStatement to preserve all whitespace and
    // special characters safely.
    private static final String R_CODE_DIFF =
            "import java.sql.Connection;\n" +
            "import java.sql.DriverManager;\n" +
            "import java.sql.ResultSet;\n" +
            "import java.sql.Statement;\n" +
            "\n" +
            "public class UserService {\n" +
            "\n" +
            "    public boolean login(String username, String password) throws Exception {\n" +
            "        // Hardcoded credentials — critical security finding\n" +
            "        Connection conn = DriverManager.getConnection(\n" +
            "            \"jdbc:mysql://localhost:3306/appdb\", \"root\", \"password\");\n" +
            "\n" +
            "        // Plain Statement with string concatenation — SQL injection risk\n" +
            "        Statement stmt = conn.createStatement();\n" +
            "        String query = \"SELECT * FROM users WHERE username='\" + username +\n" +
            "                       \"' AND password='\" + password + \"'\";\n" +
            "        ResultSet rs = stmt.executeQuery(query);\n" +
            "        return rs.next();\n" +
            "    }\n" +
            "}\n";

    // FINDING
    private static final long   F_ID          = 1L;
    private static final long   F_REVIEW_ID   = 1L;
    private static final String F_TITLE       = "No test changes detected in diff";
    private static final String F_SEVERITY    = "medium";
    private static final String F_CATEGORY    = "coverage";
    private static final String F_FILE        = "unknown";
    private static final int    F_LINE        = 0;
    private static final String F_DESCRIPTION =
            "[MOCK] The submitted diff does not appear to include any test file changes. " +
            "Adding or updating tests alongside feature code maintains coverage.";
    private static final String F_SUGGESTION  =
            "Add unit or integration tests that exercise the changed code paths. " +
            "Aim for at least one happy-path and one error-path test per new method.";

    // GENERATED TEST 1
    private static final long   GT1_ID                = 1L;
    private static final long   GT1_REVIEW_ID         = 1L;
    private static final String GT1_NAME              = "should handle edge case: no test changes detected in diff";
    private static final String GT1_FILE              = "tests/unknown_finding_1.test.js";
    private static final String GT1_STATUS            = "PENDING";
    private static final String GT1_DURATION          = null;
    private static final String GT1_LINKED_FINDING_ID = "1";
    private static final String GT1_TEST_CODE         =
            "// [MOCK GENERATED TEST] \u2014 not executed\n" +
            "// Finding: No test changes detected in diff\n" +
            "// Severity: medium | Category: coverage\n" +
            "// File: unknown:0\n" +
            "\n" +
            "describe('No test changes detected in diff', () => {\n" +
            "  it('should handle edge case: no test changes detected in diff', () => {\n" +
            "    // TODO: implement test for finding #1\n" +
            "    // Suggestion: Add unit or integration tests that exercise the changed code paths." +
            " Aim for at least one happy-path and one error-path test per new method.\n" +
            "    expect(true).toBe(true); // placeholder\n" +
            "  });\n" +
            "});\n";

    // GENERATED TEST 2
    private static final long   GT2_ID                = 2L;
    private static final long   GT2_REVIEW_ID         = 1L;
    private static final String GT2_NAME              = "diff does not introduce new critical security findings";
    private static final String GT2_FILE              = "tests/security/review_1_security.test.js";
    private static final String GT2_STATUS            = "PENDING";
    private static final String GT2_DURATION          = null;
    private static final String GT2_LINKED_FINDING_ID = null;
    private static final String GT2_TEST_CODE         =
            "// [MOCK GENERATED TEST] \u2014 not executed\n" +
            "// Review #1: Lost & Found \u2013 Security and Code Quality Review\n" +
            "\n" +
            "describe('Review #1 security smoke test', () => {\n" +
            "  it('diff does not introduce new critical security findings', () => {\n" +
            "    // This test would assert that the code changes do not\n" +
            "    // introduce hardcoded credentials, injection vectors, etc.\n" +
            "    expect(true).toBe(true); // placeholder \u2014 replace with real assertions\n" +
            "  });\n" +
            "});\n";

    // ── Entry point ────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println(" ThirdEye AI — Historical Data Recovery Utility");
        System.out.println("==========================================================");
        System.out.println("Target DB : " + DB_URL);
        System.out.println();

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            abort("H2 driver not found on classpath: " + e.getMessage());
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);

            // ── Step 1: Pre-flight checks ──────────────────────────────────
            System.out.println("[1/5] Running pre-flight checks...");
            checkProjectExists(conn, 1L, "DayFlow");
            checkProjectExists(conn, 2L, "Lost & Found");
            checkTargetRowAbsent(conn, "REVIEWS",         R_ID);
            checkTargetRowAbsent(conn, "FINDINGS",        F_ID);
            checkTargetRowAbsent(conn, "GENERATED_TESTS", GT1_ID);
            checkTargetRowAbsent(conn, "GENERATED_TESTS", GT2_ID);
            System.out.println("    All pre-flight checks passed.");
            System.out.println();

            // ── Step 2: Insert REVIEW ──────────────────────────────────────
            System.out.println("[2/5] Inserting REVIEWS row (ID=" + R_ID + ")...");
            insertReview(conn);
            System.out.println("    Review inserted.");
            System.out.println();

            // ── Step 3: Insert FINDING ─────────────────────────────────────
            System.out.println("[3/5] Inserting FINDINGS row (ID=" + F_ID + ")...");
            insertFinding(conn);
            System.out.println("    Finding inserted.");
            System.out.println();

            // ── Step 4: Insert GENERATED_TESTS ────────────────────────────
            System.out.println("[4/5] Inserting GENERATED_TESTS rows (IDs=" + GT1_ID + "," + GT2_ID + ")...");
            insertGeneratedTest(conn,
                    GT1_ID, GT1_REVIEW_ID, GT1_NAME, GT1_FILE,
                    GT1_STATUS, GT1_DURATION, GT1_LINKED_FINDING_ID, GT1_TEST_CODE);
            insertGeneratedTest(conn,
                    GT2_ID, GT2_REVIEW_ID, GT2_NAME, GT2_FILE,
                    GT2_STATUS, GT2_DURATION, GT2_LINKED_FINDING_ID, GT2_TEST_CODE);
            System.out.println("    Both generated tests inserted.");
            System.out.println();

            // ── Step 5: Commit + advance sequences ────────────────────────
            System.out.println("[5/5] Committing transaction and advancing identity sequences...");
            conn.commit();

            // Advance sequences in a separate auto-commit statement so they
            // take effect even after the transaction boundary.
            advanceSequences(conn);
            System.out.println("    Sequences advanced.");
            System.out.println();

            // ── Verification summary ───────────────────────────────────────
            printVerificationSummary(conn);

        } catch (AbortException e) {
            System.err.println();
            System.err.println("ABORTED: " + e.getMessage());
            System.err.println("No changes were committed to the database.");
            System.exit(1);
        } catch (SQLException e) {
            System.err.println();
            System.err.println("SQL ERROR: " + e.getMessage());
            System.err.println("State: " + e.getSQLState() + "  Code: " + e.getErrorCode());
            System.err.println("All changes have been rolled back.");
            System.exit(2);
        }
    }

    // ── Pre-flight helpers ────────────────────────────────────────────────

    private static void checkProjectExists(Connection conn, long id, String expectedName)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT NAME FROM PROJECTS WHERE ID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AbortException(
                            "PROJECTS row with ID=" + id + " not found. " +
                            "Expected project '" + expectedName + "'. " +
                            "Run the application first to seed projects.");
                }
                String name = rs.getString(1);
                System.out.println("    OK: PROJECTS ID=" + id + " exists ('" + name + "')");
            }
        }
    }

    private static void checkTargetRowAbsent(Connection conn, String table, long id)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE ID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                if (count > 0) {
                    throw new AbortException(
                            table + " row with ID=" + id + " already exists. " +
                            "Recovery has already been performed, or a conflicting record is present. " +
                            "Aborting to avoid duplication.");
                }
                System.out.println("    OK: " + table + " ID=" + id + " is absent (safe to insert)");
            }
        }
    }

    // ── Insert helpers ────────────────────────────────────────────────────

    private static void insertReview(Connection conn) throws SQLException {
        final String sql =
                "INSERT INTO REVIEWS " +
                "(ID, PROJECT_ID, TITLE, STATUS, CODE_DIFF, " +
                " FILES_CHANGED, LINES_ADDED, LINES_REMOVED, COMMITS, PROGRESS, " +
                " CREATED_AT, UPDATED_AT) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, R_ID);
            ps.setLong(2, R_PROJECT_ID);
            ps.setString(3, R_TITLE);
            ps.setString(4, R_STATUS);
            ps.setString(5, R_CODE_DIFF);
            ps.setInt(6, R_FILES_CHANGED);
            ps.setInt(7, R_LINES_ADDED);
            ps.setInt(8, R_LINES_REMOVED);
            ps.setInt(9, R_COMMITS);
            ps.setInt(10, R_PROGRESS);
            ps.setTimestamp(11, Timestamp.valueOf(R_CREATED_AT));
            ps.setTimestamp(12, Timestamp.valueOf(R_UPDATED_AT));
            int rows = ps.executeUpdate();
            if (rows != 1) throw new SQLException("Expected 1 row inserted for REVIEWS, got: " + rows);
        }
    }

    private static void insertFinding(Connection conn) throws SQLException {
        final String sql =
                "INSERT INTO FINDINGS " +
                "(ID, REVIEW_ID, TITLE, SEVERITY, CATEGORY, FILE, LINE, DESCRIPTION, SUGGESTION) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, F_ID);
            ps.setLong(2, F_REVIEW_ID);
            ps.setString(3, F_TITLE);
            ps.setString(4, F_SEVERITY);
            ps.setString(5, F_CATEGORY);
            ps.setString(6, F_FILE);
            ps.setInt(7, F_LINE);
            ps.setString(8, F_DESCRIPTION);
            ps.setString(9, F_SUGGESTION);
            int rows = ps.executeUpdate();
            if (rows != 1) throw new SQLException("Expected 1 row inserted for FINDINGS, got: " + rows);
        }
    }

    private static void insertGeneratedTest(
            Connection conn,
            long id, long reviewId, String name, String file,
            String status, String duration, String linkedFindingId, String testCode)
            throws SQLException {
        final String sql =
                "INSERT INTO GENERATED_TESTS " +
                "(ID, REVIEW_ID, NAME, FILE, STATUS, DURATION, LINKED_FINDING_ID, TEST_CODE) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, reviewId);
            ps.setString(3, name);
            ps.setString(4, file);
            ps.setString(5, status);
            if (duration == null) ps.setNull(6, Types.VARCHAR); else ps.setString(6, duration);
            if (linkedFindingId == null) ps.setNull(7, Types.VARCHAR); else ps.setString(7, linkedFindingId);
            ps.setString(8, testCode);
            int rows = ps.executeUpdate();
            if (rows != 1) throw new SQLException(
                    "Expected 1 row inserted for GENERATED_TESTS ID=" + id + ", got: " + rows);
        }
    }

    // ── Sequence advancement ───────────────────────────────────────────────

    /**
     * After explicit-ID inserts into GENERATED BY DEFAULT AS IDENTITY columns,
     * H2's internal counter is not automatically advanced. Without this step,
     * the next auto-generated insert by the application would collide with the
     * IDs we just inserted.
     *
     * RESTART WITH N means the next auto-generated value will be N.
     */
    private static void advanceSequences(Connection conn) throws SQLException {
        // Run outside the transaction (auto-commit DDL)
        conn.setAutoCommit(true);
        String[] stmts = {
                "ALTER TABLE REVIEWS         ALTER COLUMN ID RESTART WITH " + (R_ID + 1),
                "ALTER TABLE FINDINGS        ALTER COLUMN ID RESTART WITH " + (F_ID + 1),
                "ALTER TABLE GENERATED_TESTS ALTER COLUMN ID RESTART WITH " + (GT2_ID + 1),
        };
        for (String ddl : stmts) {
            try (Statement st = conn.createStatement()) {
                st.execute(ddl);
                System.out.println("    Executed: " + ddl);
            }
        }
    }

    // ── Verification ───────────────────────────────────────────────────────

    private static void printVerificationSummary(Connection conn) throws SQLException {
        System.out.println("==========================================================");
        System.out.println(" Verification Summary");
        System.out.println("==========================================================");

        // Table counts
        for (String table : new String[]{"PROJECTS", "REVIEWS", "FINDINGS", "GENERATED_TESTS"}) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rs.next();
                System.out.printf("  %-20s rows: %d%n", table, rs.getInt(1));
            }
        }
        System.out.println();

        // Review row
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.ID, p.NAME AS PROJECT, r.TITLE, r.STATUS, r.PROGRESS, " +
                "       r.FILES_CHANGED, r.LINES_ADDED, r.LINES_REMOVED, r.COMMITS, " +
                "       r.CREATED_AT, r.UPDATED_AT " +
                "FROM REVIEWS r JOIN PROJECTS p ON r.PROJECT_ID = p.ID " +
                "WHERE r.ID = ?")) {
            ps.setLong(1, R_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("  REVIEW ID=" + rs.getLong("ID") +
                            "  project='" + rs.getString("PROJECT") + "'" +
                            "  title='"   + rs.getString("TITLE")   + "'" +
                            "  status="   + rs.getString("STATUS")  +
                            "  progress=" + rs.getInt("PROGRESS"));
                    System.out.println("         created_at=" + rs.getTimestamp("CREATED_AT") +
                            "  updated_at=" + rs.getTimestamp("UPDATED_AT"));
                } else {
                    System.out.println("  WARNING: REVIEWS ID=1 not found after insert!");
                }
            }
        }

        // Finding row
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID, TITLE, SEVERITY, CATEGORY, FILE, LINE FROM FINDINGS WHERE ID = ?")) {
            ps.setLong(1, F_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("  FINDING ID=" + rs.getLong("ID") +
                            "  title='"    + rs.getString("TITLE")    + "'" +
                            "  severity="  + rs.getString("SEVERITY") +
                            "  category="  + rs.getString("CATEGORY") +
                            "  file="      + rs.getString("FILE") +
                            "  line="      + rs.getInt("LINE"));
                } else {
                    System.out.println("  WARNING: FINDINGS ID=1 not found after insert!");
                }
            }
        }

        // Generated test rows
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID, NAME, FILE, STATUS, LINKED_FINDING_ID " +
                "FROM GENERATED_TESTS WHERE REVIEW_ID = ? ORDER BY ID")) {
            ps.setLong(1, R_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("  GENERATED_TEST ID=" + rs.getLong("ID") +
                            "  name='"           + rs.getString("NAME")             + "'" +
                            "  status="          + rs.getString("STATUS")           +
                            "  linkedFindingId=" + rs.getString("LINKED_FINDING_ID"));
                }
            }
        }

        System.out.println();
        System.out.println("==========================================================");
        System.out.println(" Recovery complete. Restart the Spring Boot application.");
        System.out.println("==========================================================");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void abort(String msg) {
        throw new AbortException(msg);
    }

    /** Unchecked wrapper so abort() can be called without throws declarations. */
    private static class AbortException extends RuntimeException {
        AbortException(String msg) { super(msg); }
    }
}
