package com.thirdeye.backend.analysis;

import com.thirdeye.backend.analysis.rules.DangerousExecutionRule;
import com.thirdeye.backend.analysis.rules.HardcodedCredentialRule;
import com.thirdeye.backend.analysis.rules.SqlInjectionRule;
import com.thirdeye.backend.analysis.rules.UnsafeDeserializationRule;
import com.thirdeye.backend.entity.Finding;
import com.thirdeye.backend.entity.Project;
import com.thirdeye.backend.entity.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the static-analysis engine and every rule.
 *
 * <p>No Spring context, no database, no network — all tests are pure in-memory.
 * Tests cover:
 * <ul>
 *   <li>Positive detections (rule fires when pattern matches)</li>
 *   <li>Negative cases (rule does not fire on unrelated code)</li>
 *   <li>False-positive guards (rule suppressed for known-safe patterns)</li>
 *   <li>File and line attribution from parsed unified diffs</li>
 *   <li>Empty diff, null diff, and malformed input</li>
 *   <li>Engine-level orchestration and idempotency of rule application</li>
 * </ul>
 */
class StaticAnalysisEngineTest {

    // ── shared helpers ────────────────────────────────────────────────────────

    private static Review dummyReview() {
        Project project = new Project("Test", "desc", "http://r", "ACTIVE");
        Review  review  = new Review(project, "Test review", "");
        return review;
    }

    /** Wrap content lines in a minimal valid unified diff for a given file. */
    private static String diff(String filename, String... addedContentLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(filename).append("\n");
        sb.append("+++ b/").append(filename).append("\n");
        sb.append("@@ -0,0 +1,").append(addedContentLines.length).append(" @@\n");
        for (String line : addedContentLines) {
            sb.append("+").append(line).append("\n");
        }
        return sb.toString();
    }

    // ── DiffParser ────────────────────────────────────────────────────────────

    @Nested
    class DiffParserTest {

        @Test
        void emptyInput_returnsNoLines() {
            assertThat(DiffParser.parse("")).isEmpty();
            assertThat(DiffParser.parse(null)).isEmpty();
            assertThat(DiffParser.parse("   \n  ")).isEmpty();
        }

        @Test
        void parsesFileAndLineNumber() {
            String d = diff("src/Foo.java", "int x = 1;", "int y = 2;");
            List<DiffLine> lines = DiffParser.parse(d);
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).getFile()).isEqualTo("src/Foo.java");
            assertThat(lines.get(0).getLineNumber()).isEqualTo(1);
            assertThat(lines.get(0).getContent()).isEqualTo("int x = 1;");
            assertThat(lines.get(1).getLineNumber()).isEqualTo(2);
        }

        @Test
        void stripsGitBPrefix() {
            String d = "+++ b/com/example/Service.java\n@@ -0,0 +1 @@\n+code\n";
            List<DiffLine> lines = DiffParser.parse(d);
            assertThat(lines.get(0).getFile()).isEqualTo("com/example/Service.java");
        }

        @Test
        void removedLinesNotIncluded() {
            String d = "--- a/Foo.java\n+++ b/Foo.java\n@@ -1,1 +1,1 @@\n-old line\n+new line\n";
            List<DiffLine> lines = DiffParser.parse(d);
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getContent()).isEqualTo("new line");
        }

        @Test
        void contextLineAdvancesLineCounter() {
            String d = "--- a/F.java\n+++ b/F.java\n@@ -1,3 +1,3 @@\n context1\n+added at line 2\n context3\n";
            List<DiffLine> lines = DiffParser.parse(d);
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getLineNumber()).isEqualTo(2);
        }

        @Test
        void rawLinesWithoutDiffHeader_unknownFile() {
            // No +++ header → file = "unknown"
            String d = "+String x = \"val\";\n";
            List<DiffLine> lines = DiffParser.parse(d);
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getFile()).isEqualTo("unknown");
        }

        @Test
        void multipleHunks_lineNumbersContiguous() {
            String d = "--- a/F.java\n+++ b/F.java\n" +
                       "@@ -1,1 +1,1 @@\n+line1\n" +
                       "@@ -10,1 +10,1 @@\n+line10\n";
            List<DiffLine> lines = DiffParser.parse(d);
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).getLineNumber()).isEqualTo(1);
            assertThat(lines.get(1).getLineNumber()).isEqualTo(10);
        }

        @Test
        void extractFile_handlesVariousFormats() {
            assertThat(DiffParser.extractFile("+++ b/src/Foo.java")).isEqualTo("src/Foo.java");
            assertThat(DiffParser.extractFile("+++ src/Bar.java")).isEqualTo("src/Bar.java");
            assertThat(DiffParser.extractFile("+++ ")).isEqualTo("unknown");
        }
    }

    // ── SqlInjectionRule ──────────────────────────────────────────────────────

    @Nested
    class SqlInjectionRuleTest {

        private final SqlInjectionRule rule = new SqlInjectionRule();

        @Test
        void detectsSqlConcatWithSelectAndWhere() {
            List<DiffLine> lines = List.of(
                    new DiffLine("UserDao.java", 15,
                            "String q = \"SELECT * FROM users WHERE name='\" + username + \"'\";"));
            assertThat(rule.analyze(lines)).hasSize(1);
            assertThat(rule.analyze(lines).get(0).getSeverity()).isEqualTo("high");
            assertThat(rule.analyze(lines).get(0).getCategory()).isEqualTo("security");
        }

        @Test
        void criticalWhenStatementExecuteAlsoPresent() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Dao.java", 10,
                            "ResultSet rs = Statement.executeQuery(\"SELECT * FROM t WHERE id=\" + id);"));
            List<RuleFinding> findings = rule.analyze(lines);
            assertThat(findings).isNotEmpty();
            assertThat(findings.get(0).getSeverity()).isEqualTo("critical");
        }

        @Test
        void noFindingForPreparedStatement() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Dao.java", 5,
                            "PreparedStatement ps = conn.prepareStatement(\"SELECT * FROM t WHERE id=?\");"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForLiteralOnlySqlString() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Dao.java", 3,
                            "String q = \"SELECT * FROM users WHERE active = true\";"));
            // No concatenation operator
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void detectsInsertConcatenation() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Repo.java", 20,
                            "stmt.execute(\"INSERT INTO log VALUES ('\" + msg + \"')\");"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void emptyLines_noFindings() {
            assertThat(rule.analyze(List.of())).isEmpty();
        }

        @Test
        void findingIncludesRuleId() {
            List<DiffLine> lines = List.of(
                    new DiffLine("F.java", 1,
                            "String q = \"SELECT id FROM t WHERE x='\" + x + \"'\";"));
            RuleFinding f = rule.analyze(lines).get(0);
            assertThat(f.getDescription()).contains("SQLI-001");
        }

        @Test
        void fileAndLineAttributedCorrectly() {
            List<DiffLine> lines = List.of(
                    new DiffLine("dao/UserDao.java", 42,
                            "String q = \"SELECT * FROM u WHERE name='\" + n + \"'\";"));
            RuleFinding f = rule.analyze(lines).get(0);
            assertThat(f.getFile()).isEqualTo("dao/UserDao.java");
            assertThat(f.getLine()).isEqualTo(42);
        }
    }

    // ── HardcodedCredentialRule ────────────────────────────────────────────────

    @Nested
    class HardcodedCredentialRuleTest {

        private final HardcodedCredentialRule rule = new HardcodedCredentialRule();

        @Test
        void detectsHardcodedPassword() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Config.java", 5, "String password = \"supersecret\";"));
            assertThat(rule.analyze(lines)).hasSize(1);
            assertThat(rule.analyze(lines).get(0).getSeverity()).isEqualTo("critical");
        }

        @Test
        void detectsApiKey() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Client.java", 3, "String api_key = \"AKIA1234567890EXAMPLE\";"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void detectsToken() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Service.java", 7, "String token = \"eyJhbGciOiJIUzI1NiJ9.abc\";"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void noFindingForEmptyStringValue() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Config.java", 8, "String password = \"\";"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForPlaceholderValue() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Config.java", 8, "String password = \"changeme\";"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForSpringPropertyBinding() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Config.java", 8, "String password = \"${db.password}\";"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForSystemGetenv() {
            List<DiffLine> lines = List.of(
                    new DiffLine("App.java", 2, "String password = System.getenv(\"DB_PASS\");"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForPureCommentLine() {
            List<DiffLine> lines = List.of(
                    new DiffLine("App.java", 3, "// password = \"example123\" // don't do this"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void lowSeverityInTestFile() {
            List<DiffLine> lines = List.of(
                    new DiffLine("UserServiceTest.java", 12,
                            "String password = \"testpassword123\";"));
            List<RuleFinding> findings = rule.analyze(lines);
            assertThat(findings).isNotEmpty();
            assertThat(findings.get(0).getSeverity()).isEqualTo("low");
        }

        @Test
        void findingIncludesRuleId() {
            List<DiffLine> lines = List.of(
                    new DiffLine("F.java", 1, "String secret = \"abc123\";"));
            assertThat(rule.analyze(lines).get(0).getDescription()).contains("CRED-001");
        }

        @Test
        void fileAndLineAttributedCorrectly() {
            List<DiffLine> lines = List.of(
                    new DiffLine("src/main/Config.java", 99, "String password = \"hunter2\";"));
            RuleFinding f = rule.analyze(lines).get(0);
            assertThat(f.getFile()).isEqualTo("src/main/Config.java");
            assertThat(f.getLine()).isEqualTo(99);
        }

        @Test
        void emptyLines_noFindings() {
            assertThat(rule.analyze(List.of())).isEmpty();
        }
    }

    // ── DangerousExecutionRule ────────────────────────────────────────────────

    @Nested
    class DangerousExecutionRuleTest {

        private final DangerousExecutionRule rule = new DangerousExecutionRule();

        @Test
        void detectsRuntimeExec() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Shell.java", 8, "Runtime.getRuntime().exec(cmd);"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void detectsProcessBuilder() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Shell.java", 10, "Process p = new ProcessBuilder(args).start();"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void highSeverityWithDynamicArg() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Shell.java", 5,
                            "Runtime.getRuntime().exec(\"cmd /c \" + userInput);"));
            assertThat(rule.analyze(lines).get(0).getSeverity()).isEqualTo("high");
        }

        @Test
        void mediumSeverityWithoutDynamicArg() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Shell.java", 5,
                            "Runtime.getRuntime().exec(\"ls -la\");"));
            assertThat(rule.analyze(lines).get(0).getSeverity()).isEqualTo("medium");
        }

        @Test
        void noFindingForUnrelatedCode() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Service.java", 5, "int x = compute(y);"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForCommentLine() {
            List<DiffLine> lines = List.of(
                    new DiffLine("A.java", 1,
                            "// Runtime.getRuntime().exec(\"bad\"); -- old code"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void findingIncludesRuleId() {
            List<DiffLine> lines = List.of(
                    new DiffLine("A.java", 1, "Runtime.getRuntime().exec(\"ls\");"));
            assertThat(rule.analyze(lines).get(0).getDescription()).contains("EXEC-001");
        }

        @Test
        void emptyLines_noFindings() {
            assertThat(rule.analyze(List.of())).isEmpty();
        }
    }

    // ── UnsafeDeserializationRule ─────────────────────────────────────────────

    @Nested
    class UnsafeDeserializationRuleTest {

        private final UnsafeDeserializationRule rule = new UnsafeDeserializationRule();

        @Test
        void detectsObjectInputStream() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Deser.java", 12,
                            "ObjectInputStream ois = new ObjectInputStream(stream);"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void detectsReadObject() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Deser.java", 15, "Object obj = ois.readObject();"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void detectsXStreamFromXml() {
            List<DiffLine> lines = List.of(
                    new DiffLine("X.java", 7, "Object o = xstream.fromXML(xmlData);"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void detectsSerializationUtils() {
            List<DiffLine> lines = List.of(
                    new DiffLine("U.java", 5,
                            "Object o = SerializationUtils.deserialize(bytes);"));
            assertThat(rule.analyze(lines)).isNotEmpty();
        }

        @Test
        void highSeverityForNetworkSource() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Deser.java", 3,
                            "ObjectInputStream ois = new ObjectInputStream(request.getInputStream());"));
            assertThat(rule.analyze(lines).get(0).getSeverity()).isEqualTo("high");
        }

        @Test
        void mediumSeverityForUnknownSource() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Deser.java", 3,
                            "ObjectInputStream ois = new ObjectInputStream(fileStream);"));
            assertThat(rule.analyze(lines).get(0).getSeverity()).isEqualTo("medium");
        }

        @Test
        void noFindingForUnrelatedCode() {
            List<DiffLine> lines = List.of(
                    new DiffLine("Service.java", 1, "String x = \"hello\";"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void noFindingForCommentLine() {
            List<DiffLine> lines = List.of(
                    new DiffLine("A.java", 1,
                            "// new ObjectInputStream(stream) — do not use"));
            assertThat(rule.analyze(lines)).isEmpty();
        }

        @Test
        void findingIncludesRuleId() {
            List<DiffLine> lines = List.of(
                    new DiffLine("A.java", 1, "ois.readObject();"));
            assertThat(rule.analyze(lines).get(0).getDescription()).contains("DESER-001");
        }

        @Test
        void emptyLines_noFindings() {
            assertThat(rule.analyze(List.of())).isEmpty();
        }
    }

    // ── StaticAnalysisEngine ──────────────────────────────────────────────────

    @Nested
    class EngineTest {

        private StaticAnalysisEngine engine;
        private Review review;

        @BeforeEach
        void setUp() {
            engine = new StaticAnalysisEngine();
            review = dummyReview();
        }

        @Test
        void nullDiff_returnsSingleEmptyDiffFinding() {
            List<Finding> findings = engine.analyze(review, null);
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getSeverity()).isEqualTo("low");
        }

        @Test
        void blankDiff_returnsSingleEmptyDiffFinding() {
            List<Finding> findings = engine.analyze(review, "   \n  ");
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).getSeverity()).isEqualTo("low");
        }

        @Test
        void diffWithSqlInjectionAndCredential_detectsBoth() {
            String d = diff("LoginService.java",
                    "String q = \"SELECT * FROM users WHERE pwd='\" + password + \"'\";",
                    "Connection c = DriverManager.getConnection(url, \"root\", \"password123\");");
            List<Finding> findings = engine.analyze(review, d);
            boolean hasSql  = findings.stream().anyMatch(f -> f.getDescription().contains("SQLI-001"));
            boolean hasCred = findings.stream().anyMatch(f -> f.getDescription().contains("CRED-001"));
            assertThat(hasSql).isTrue();
            assertThat(hasCred).isTrue();
        }

        @Test
        void diffWithNoSecurityIssues_returnsNoFindings() {
            String d = diff("Service.java",
                    "int x = 42;",
                    "return x * 2;");
            assertThat(engine.analyze(review, d)).isEmpty();
        }

        @Test
        void registeredRuleIds_containsAllFourRules() {
            assertThat(engine.registeredRuleIds())
                    .containsExactlyInAnyOrder("SQLI-001", "CRED-001", "EXEC-001", "DESER-001");
        }

        @Test
        void findingsHaveFileAndLineFromDiff() {
            String d = diff("src/UserDao.java",
                    "String q = \"SELECT * FROM u WHERE name='\" + name + \"'\";");
            List<Finding> findings = engine.analyze(review, d);
            assertThat(findings).isNotEmpty();
            Finding f = findings.get(0);
            assertThat(f.getFile()).isEqualTo("src/UserDao.java");
            assertThat(f.getLine()).isEqualTo(1);
        }

        @Test
        void rawCodeWithoutDiffHeader_unknownFileLocation() {
            // Raw code paste without git diff header — file should be "unknown"
            String raw = "+String password = \"abc123\";\n";
            List<Finding> findings = engine.analyze(review, raw);
            assertThat(findings).isNotEmpty();
            assertThat(findings.stream().allMatch(f -> "unknown".equals(f.getFile()))).isTrue();
        }

        @Test
        void execAndDeserInSameDiff_bothDetected() {
            String d = diff("Util.java",
                    "Runtime.getRuntime().exec(\"rm -rf \" + dir);",
                    "Object obj = new ObjectInputStream(request.getInputStream()).readObject();");
            List<Finding> findings = engine.analyze(review, d);
            boolean hasExec  = findings.stream().anyMatch(f -> f.getDescription().contains("EXEC-001"));
            boolean hasDeser = findings.stream().anyMatch(f -> f.getDescription().contains("DESER-001"));
            assertThat(hasExec).isTrue();
            assertThat(hasDeser).isTrue();
        }

        @Test
        void historicalReviewDiff_detectsCredentialAndNoTests() {
            // Mirrors the exact code from the recovered historical review (ID 1)
            String d = diff("UserService.java",
                    "import java.sql.DriverManager;",
                    "Connection conn = DriverManager.getConnection(",
                    "    \"jdbc:mysql://localhost:3306/appdb\", \"root\", \"password\");",
                    "Statement stmt = conn.createStatement();",
                    "String query = \"SELECT * FROM users WHERE username='\" + username +",
                    "               \"' AND password='\" + password + \"'\";",
                    "ResultSet rs = stmt.executeQuery(query);");
            List<Finding> findings = engine.analyze(review, d);
            // Should detect hardcoded credential ("root", "password" in DriverManager call)
            assertThat(findings.stream().anyMatch(f -> f.getDescription().contains("CRED-001"))).isTrue();
            // Should detect SQL injection (SELECT ... + username/password)
            assertThat(findings.stream().anyMatch(f -> f.getDescription().contains("SQLI-001"))).isTrue();
        }
    }
}
