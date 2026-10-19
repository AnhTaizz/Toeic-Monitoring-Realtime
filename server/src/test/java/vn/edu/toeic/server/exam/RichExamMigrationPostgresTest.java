package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.server.ToeicServerApplication;

/** PostgreSQL/Flyway/HTTP thật; mọi nội dung đề là fixture MOCK tự tạo, không dùng seed ETS. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PG_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RichExamMigrationPostgresTest {
    private static final Gson JSON = new Gson();
    private String url, user, password, schema, base;
    private Connection admin, db;
    private ConfigurableApplicationContext context;
    private HttpClient http;

    @BeforeAll void connectTestDatabase() throws Exception {
        url = "jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME");
        user = required("DB_USER"); password = required("DB_PASSWORD");
        admin = DriverManager.getConnection(url, user, password);
    }

    @BeforeEach void createOwnedSchema() throws Exception {
        schema = "ex04_" + UUID.randomUUID().toString().replace("-", "");
        try (var sql = admin.createStatement()) { sql.execute("CREATE SCHEMA " + schema); }
        db = DriverManager.getConnection(url + "?currentSchema=" + schema, user, password);
    }

    @AfterEach void removeOnlyOwnedSchema() throws Exception {
        try {
            if (http != null) { http.close(); http = null; }
            if (context != null) { context.close(); context = null; }
            if (db != null) { if (!db.getAutoCommit()) db.rollback(); db.close(); db = null; }
        } finally {
            try (var sql = admin.createStatement()) { sql.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }

    @AfterAll void closeTestDatabaseConnection() throws Exception { if (admin != null) admin.close(); }

    @Test void freshV1ThroughV11HasValidatedHistoryAndExpectedConstraints() throws Exception {
        var flyway = flyway("11");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(11);
        flyway.validate();
        assertThat(strings("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE NOT success OR checksum IS NULL")).isZero();
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema = '" + schema
                + "' AND table_name IN ('exam_sources','exam_question_groups','exam_media_assets','exam_content_blocks')")).isEqualTo(4);
        assertThat(strings("SELECT condeferrable::text || '/' || condeferred::text FROM pg_constraint WHERE conrelid = 'exam_questions'::regclass AND conname = 'exam_questions_correct_option_fk'"))
                .containsExactly("true/true");
        assertThat(strings("SELECT indexname FROM pg_indexes WHERE schemaname = '" + schema + "'"))
                .contains("exam_questions_exam_order_unique", "exam_options_question_order_unique",
                        "exam_content_blocks_group_order_unique", "exam_content_blocks_question_order_unique");
    }

    @Test void upgradeV10BackfillsGroupsWithoutLosingLegacyDataOrOrder() throws Exception {
        migrate("10"); seedLegacyV10();
        String before = legacySnapshot();
        assertThat(flyway("11").migrate().migrationsExecuted).isEqualTo(1);
        flyway("11").validate();
        assertThat(legacySnapshot()).isEqualTo(before);
        assertThat(strings("SELECT group_id || '/' || section || '/' || part || '/' || order_index || '/' || mapping_status FROM exam_question_groups"))
                .containsExactly("G6/READING/6/1/UNVERIFIED");
        assertThat(number("SELECT count(*) FROM exam_questions WHERE group_id IS NULL")).isEqualTo(2);
        assertThat(strings("SELECT question_id FROM exam_questions ORDER BY order_index")).containsExactly("Q1", "Q2", "Q3", "Q4");
        assertThat(number("SELECT count(*) FROM exam_questions WHERE source_id IS NULL AND source_question_number IS NULL AND answer_key_status = 'UNVERIFIED'"))
                .isEqualTo(4);
    }

    @ParameterizedTest(name = "V10 invalid {0}")
    @ValueSource(strings = {"QUESTION_ORDER", "OPTION_ORDER", "CORRECT_OPTION", "SECTION_PART", "GROUP_PART"})
    void invalidV10DataRollsBackWholeV11WithoutSuccessHistory(String fault) throws Exception {
        migrate("10"); seedLegacyV10();
        execute(switch (fault) {
            case "QUESTION_ORDER" -> "UPDATE exam_questions SET order_index = 1 WHERE question_id = 'Q2'";
            case "OPTION_ORDER" -> "UPDATE exam_options SET order_index = 1 WHERE question_id = 'Q1' AND option_id = 'B'";
            case "CORRECT_OPTION" -> "UPDATE exam_questions SET correct_option = 'MISSING' WHERE question_id = 'Q1'";
            case "SECTION_PART" -> "UPDATE exam_questions SET part = 1 WHERE question_id = 'Q1'";
            case "GROUP_PART" -> "UPDATE exam_questions SET part = 7 WHERE question_id = 'Q2'";
            default -> throw new IllegalArgumentException(fault);
        });
        String before = legacySnapshot();
        assertThrows(FlywayException.class, () -> migrate("11"));
        assertThat(legacySnapshot()).isEqualTo(before);
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version = '11'")).isZero();
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema = '" + schema
                + "' AND table_name IN ('exam_sources','exam_question_groups','exam_media_assets','exam_content_blocks')")).isZero();
        assertThat(strings("SELECT group_id FROM exam_questions WHERE question_id = 'Q3'")).containsExactly("   ");
        flyway("10").validate();
    }

    record Rejection(String name, String sql, String state, String constraint) {
        @Override public String toString() { return name; }
    }

    static Stream<Rejection> invalidRows() {
        var rows = new ArrayList<Rejection>();
        rows.add(new Rejection("duplicate question order", "UPDATE exam_questions SET order_index=1 WHERE exam_id='E1' AND question_id='Q2'", "23505", "exam_questions_exam_order_unique"));
        rows.add(new Rejection("duplicate option order", "UPDATE exam_options SET order_index=1 WHERE exam_id='E1' AND question_id='Q1' AND option_id='B'", "23505", "exam_options_question_order_unique"));
        rows.add(new Rejection("orphan correct option at COMMIT", "UPDATE exam_questions SET correct_option='C' WHERE exam_id='E1' AND question_id='Q1'", "23503", "exam_questions_correct_option_fk"));
        rows.add(new Rejection("section/part mismatch", "UPDATE exam_questions SET part=4 WHERE exam_id='E1' AND question_id='Q1'", "23514", "exam_questions_section_part_consistent"));
        rows.add(new Rejection("foreign exam group", "UPDATE exam_questions SET group_id='G2' WHERE exam_id='E1' AND question_id='Q1'", "23503", "exam_questions_group_fk"));
        rows.add(new Rejection("two block owners", block("'G1'", "'Q1'", "TEXT", "'MOCK'", "NULL", "NULL"), "23514", "exam_content_blocks_one_owner"));
        rows.add(new Rejection("no block owner", block("NULL", "NULL", "TEXT", "'MOCK'", "NULL", "NULL"), "23514", "exam_content_blocks_one_owner"));
        rows.add(new Rejection("TEXT without text", block("NULL", "'Q1'", "TEXT", "NULL", "NULL", "NULL"), "23514", "exam_content_blocks_payload"));
        rows.add(new Rejection("TEXT with media", block("NULL", "'Q1'", "TEXT", "'MOCK'", "'PHOTO'", "'IMAGE'"), "23514", "exam_content_blocks_payload"));
        rows.add(new Rejection("IMAGE points to audio", block("NULL", "'Q1'", "IMAGE", "NULL", "'AUDIO'", "'IMAGE'"), "23503", "exam_content_blocks_media_fk"));
        rows.add(new Rejection("AUDIO points to image", block("NULL", "'Q1'", "AUDIO", "NULL", "'PHOTO'", "'AUDIO'"), "23503", "exam_content_blocks_media_fk"));
        rows.add(new Rejection("declared media type differs from block", block("NULL", "'Q1'", "IMAGE", "NULL", "'AUDIO'", "'AUDIO'"), "23514", "exam_content_blocks_payload"));
        rows.add(new Rejection("foreign exam media", block("NULL", "'Q1'", "IMAGE", "NULL", "'FOREIGN'", "'IMAGE'"), "23503", "exam_content_blocks_media_fk"));
        rows.add(new Rejection("foreign exam source", "UPDATE exam_questions SET source_id=2001,source_question_number=1 WHERE exam_id='E1' AND question_id='Q1'", "23503", "exam_questions_source_fk"));
        rows.add(new Rejection("incomplete provenance pair", "UPDATE exam_questions SET source_id=1001 WHERE exam_id='E1' AND question_id='Q1'", "23514", "exam_questions_source_pair"));
        rows.add(new Rejection("duplicate source number", "UPDATE exam_questions SET source_id=1001,source_question_number=1 WHERE exam_id='E1'", "23505", "exam_questions_source_number_unique"));
        for (String field : List.of("local_file", "mime_type", "size_bytes", "sha256")) {
            rows.add(new Rejection("LOCAL_VERIFIED missing " + field, "UPDATE exam_media_assets SET " + field + "=NULL WHERE exam_id='E1' AND media_id='PHOTO'", "23514", "exam_media_assets_verified_complete"));
        }
        rows.add(new Rejection("invalid storage status", "UPDATE exam_media_assets SET storage_status='READY' WHERE exam_id='E1' AND media_id='PHOTO'", "23514", "exam_media_assets_storage_status_check"));
        rows.add(new Rejection("negative media size", "UPDATE exam_media_assets SET size_bytes=-1 WHERE exam_id='E1' AND media_id='PHOTO'", "23514", "exam_media_assets_size_bytes_check"));
        rows.add(new Rejection("invalid checksum", "UPDATE exam_media_assets SET sha256='bad' WHERE exam_id='E1' AND media_id='PHOTO'", "23514", "exam_media_assets_sha256_check"));
        rows.add(new Rejection("audio has image dimensions", "UPDATE exam_media_assets SET width_px=1 WHERE exam_id='E1' AND media_id='AUDIO'", "23514", "exam_media_assets_dimensions_by_type"));
        rows.add(new Rejection("image has audio duration", "UPDATE exam_media_assets SET duration_ms=1 WHERE exam_id='E1' AND media_id='PHOTO'", "23514", "exam_media_assets_duration_by_type"));
        rows.add(new Rejection("invalid permission enum", "UPDATE exam_sources SET permission_status='APPROVED' WHERE exam_id='E1'", "23514", "exam_sources_permission_status_check"));
        String first = block("NULL", "'Q1'", "TEXT", "'MOCK first'", "NULL", "NULL");
        rows.add(new Rejection("duplicate question block order", first + ";" + first.replace("'B1'", "'B2'"), "23505", "exam_content_blocks_question_order_unique"));
        String group = block("'G1'", "NULL", "TEXT", "'MOCK first'", "NULL", "NULL");
        rows.add(new Rejection("duplicate group block order", group + ";" + group.replace("'B1'", "'B2'"), "23505", "exam_content_blocks_group_order_unique"));
        return rows.stream();
    }

    @ParameterizedTest(name = "V11 rejects {0}") @MethodSource("invalidRows")
    void v11RejectsInvalidRowsIncludingDeferredCommit(Rejection row) throws Exception {
        migrate("11"); seedNormalized();
        rejected(row.sql(), row.state(), row.constraint());
    }

    @ParameterizedTest @ValueSource(strings = {"IMAGE", "AUDIO"})
    void latestSchemaRejectsNullMediaTypeInsteadOfSkippingTypedForeignKey(String type) throws Exception {
        migrate(null); seedNormalized();
        rejected(block("NULL", "'Q1'", type, "NULL", "'MISSING'", "NULL"), "23514", "exam_content_blocks_media_type_required");
    }

    @Test void v11NullableTypeReproducerMakesV12FailClosedWithoutEditingBadData() throws Exception {
        migrate("11"); seedNormalized();
        execute(block("NULL", "'Q1'", "IMAGE", "NULL", "'MISSING'", "NULL"));
        assertThat(number("SELECT count(*) FROM exam_content_blocks WHERE media_id='MISSING' AND media_type IS NULL")).isEqualTo(1);
        assertThrows(FlywayException.class, () -> migrate("12"));
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version='12'")).isZero();
        assertThat(number("SELECT count(*) FROM exam_content_blocks WHERE media_id='MISSING' AND media_type IS NULL")).isEqualTo(1);
        flyway("11").validate();
    }

    @Test void v12KeepsValidTextImageAudioBlocksAndValidatedV11Checksums() throws Exception {
        migrate("11"); seedNormalized();
        execute(block("NULL", "'Q1'", "TEXT", "'MOCK text'", "NULL", "NULL"));
        execute(block("'G1'", "NULL", "IMAGE", "NULL", "'PHOTO'", "'IMAGE'").replace("'B1'", "'B2'"));
        execute(block("NULL", "'Q2'", "AUDIO", "NULL", "'AUDIO'", "'AUDIO'").replace("'B1'", "'B3'"));
        assertThat(flyway("12").migrate().migrationsExecuted).isEqualTo(1);
        flyway("12").validate();
        assertThat(number("SELECT count(*) FROM exam_content_blocks")).isEqualTo(3);
        assertThat(strings("SELECT block_type FROM exam_content_blocks ORDER BY block_id")).containsExactly("TEXT", "IMAGE", "AUDIO");
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE success")).isEqualTo(12);
    }

    @Test void sevenPartsAndSharedOrderedBlocksRoundTripOnV11() throws Exception {
        migrate("11"); seedNormalized();
        db.setAutoCommit(false);
        for (int part = 1; part <= 7; part++) {
            String section = part <= 4 ? "LISTENING" : "READING";
            String group = List.of(3, 4, 6, 7).contains(part) ? "'P" + part + "'" : "NULL";
            if (!"NULL".equals(group)) execute("INSERT INTO exam_question_groups(exam_id,group_id,section,part,order_index) VALUES('E2'," + group + ",'" + section + "'," + part + "," + (part + 10) + ")");
            execute("INSERT INTO exam_questions(exam_id,question_id,section,part,group_id,prompt,correct_option,order_index) VALUES('E2','P" + part + "','" + section + "'," + part + "," + group + ",'MOCK prompt','A'," + (part + 10) + ")");
            for (int option = 0; option < (part == 2 ? 3 : 4); option++) {
                String id = String.valueOf((char) ('A' + option));
                execute("INSERT INTO exam_options(exam_id,question_id,option_id,option_text,order_index) VALUES('E2','P" + part + "','" + id + "','" + id + "'," + (option + 1) + ")");
            }
        }
        execute("INSERT INTO exam_media_assets(exam_id,media_id,media_type,storage_status) VALUES('E2','SHARED','AUDIO','REMOTE_REFERENCE')");
        execute("INSERT INTO exam_content_blocks(exam_id,block_id,group_id,question_id,block_type,order_index,text_content,media_id,media_type) VALUES"
                + "('E2','P1-photo',NULL,'P1','IMAGE',1,NULL,'FOREIGN','IMAGE'),"
                + "('E2','P1-audio',NULL,'P1','AUDIO',2,NULL,'SHARED','AUDIO'),"
                + "('E2','P2-audio',NULL,'P2','AUDIO',1,NULL,'SHARED','AUDIO'),"
                + "('E2','P3-audio','P3',NULL,'AUDIO',1,NULL,'SHARED','AUDIO'),"
                + "('E2','P4-audio','P4',NULL,'AUDIO',1,NULL,'SHARED','AUDIO'),"
                + "('E2','P6-text','P6',NULL,'TEXT',1,'MOCK passage',NULL,NULL),"
                + "('E2','P7-doc1','P7',NULL,'TEXT',1,'MOCK document 1',NULL,NULL),"
                + "('E2','P7-image','P7',NULL,'IMAGE',2,NULL,'FOREIGN','IMAGE'),"
                + "('E2','P7-doc2','P7',NULL,'TEXT',3,'MOCK document 2',NULL,NULL)");
        db.commit(); db.setAutoCommit(true);
        assertThat(strings("SELECT part::text FROM exam_questions WHERE exam_id='E2' AND question_id LIKE 'P%' ORDER BY order_index"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(number("SELECT count(*) FROM exam_options WHERE exam_id='E2' AND question_id='P2'")).isEqualTo(3);
        assertThat(strings("SELECT block_id FROM exam_content_blocks WHERE exam_id='E2' AND group_id='P7' ORDER BY order_index"))
                .containsExactly("P7-doc1", "P7-image", "P7-doc2");
        assertThat(number("SELECT count(*) FROM exam_content_blocks WHERE exam_id='E2' AND group_id IN('P3','P4') AND media_id='SHARED'")).isEqualTo(2);
    }

    @Test void v11MetadataDoesNotClaimFilesystemValidationOrGroupPartMatching() throws Exception {
        migrate("11"); seedNormalized();
        execute("UPDATE exam_media_assets SET local_file='../MOCK.png',size_bytes=0 WHERE exam_id='E1' AND media_id='PHOTO'");
        execute("UPDATE exam_questions SET part=7 WHERE exam_id='E1' AND question_id='Q1'");
        assertThat(strings("SELECT local_file FROM exam_media_assets WHERE exam_id='E1' AND media_id='PHOTO'")).containsExactly("../MOCK.png");
        assertThat(number("SELECT count(*) FROM exam_questions q JOIN exam_question_groups g USING(exam_id,group_id) WHERE q.part<>g.part")).isEqualTo(1);
        // Chỉ ghi nhận giới hạn schema: không có file thật hoặc quyền runtime nào được cấp bởi test này.
    }

    @Test void upgradedV11BootsAndLegacyHttpImportReadRemainCompatible() throws Exception {
        migrate("10"); seedLegacyV10(); migrate("11"); startBackend("11");
        String candidate = login("candidate1"), proctor = login("proctor1"), other = login("candidate2");
        var legacy = expect(get("/api/v1/attempts/LEGACY-A/exam", candidate), 200);
        assertThat(JSON.fromJson(legacy.body(), JsonObject.class).getAsJsonArray("questions")).hasSize(4);
        expect(get("/api/v1/attempts/LEGACY-A/exam", other), 403);
        var request = new ExamImportRequest("HTTP-MOCK", "MOCK rich baseline", "MOCK self-authored",
                List.of(q("P1", 1, null, 1), q("P3a", 3, "G3", 2), q("P3b", 3, "G3", 3), q("P6", 6, "G6", 4), q("P7", 7, "G7", 5)));
        expect(post("/api/v1/exams/import", request, candidate), 403);
        expect(post("/api/v1/exams/import", request, proctor), 200);
        // Legacy endpoint reports duplicate IDs as INVALID_INPUT (400), not overwrite or a new 409 contract.
        expect(post("/api/v1/exams/import", request, proctor), 400);
        assertThat(number("SELECT count(*) FROM exam_questions WHERE exam_id='HTTP-MOCK'")).isEqualTo(5);
        assertThat(number("SELECT count(*) FROM exam_question_groups WHERE exam_id='HTTP-MOCK'")).isEqualTo(3);
        createSession("HTTP-S1", "HTTP-MOCK", proctor); createSession("HTTP-S2", "HTTP-MOCK", proctor);
        assertThat(number("SELECT count(*) FROM exam_sessions WHERE exam_id='HTTP-MOCK'")).isEqualTo(2);
        var response = expect(get("/api/v1/attempts/HTTP-S1-candidate1/exam", candidate), 200);
        assertThat(response.body()).doesNotContain("correctOption", "correct_option", "answerKey", "explanation", "permissionNote", "licenseDocument");
        assertThat(JSON.fromJson(response.body(), JsonObject.class).getAsJsonArray("questions")).hasSize(5);
        assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='LEGACY-A'")).isEqualTo(7);
        assertThat(number("SELECT saved_revision FROM monitoring_attempts WHERE attempt_id='LEGACY-A'")).isEqualTo(3);
    }

    @Test void scheduledSessionCurrentlyAllowsExamTakeoverSaveSubmitUnderQd21() throws Exception {
        migrate(null); startBackend(null);
        String candidate = login("candidate1"), proctor = login("proctor1");
        var request = new ExamImportRequest("START-MOCK", "MOCK scheduled", "MOCK", List.of(q("R1", 5, null, 1)));
        expect(post("/api/v1/exams/import", request, proctor), 200); createSession("START-S", "START-MOCK", proctor);
        String attempt = "START-S-candidate1", path = "/api/v1/attempts/" + attempt;
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE attempt_id='" + attempt + "' AND deadline_at IS NULL")).isEqualTo(1);
        expect(get(path + "/exam", candidate), 200);
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE attempt_id='" + attempt + "' AND deadline_at IS NULL")).isEqualTo(1);
        var writer = JSON.fromJson(expect(post(path + "/takeover", new TakeoverWriterRequest(id(), attempt, null), candidate), 200).body(), JsonObject.class);
        long epoch = writer.get("writerEpoch").getAsLong();
        assertThat(writer.get("deadlineAt").isJsonNull()).isFalse();
        expect(post(path + "/answers", new AutosaveAnswersRequest(id(), attempt, epoch, 1, Map.of("R1", "A")), candidate), 200);
        expect(post(path + "/submit", new SubmitExamRequest(id(), attempt, epoch, 2, Map.of("R1", "A")), candidate), 200);
        assertThat(strings("SELECT state FROM exam_sessions WHERE session_id='START-S'")).containsExactly("SCHEDULED");
        assertThat(strings("SELECT state FROM monitoring_attempts WHERE attempt_id='" + attempt + "'")).containsExactly("SUBMITTED");
        // Reproducer hiện trạng, không sửa QD21. EX-07 phải duyệt gate/deadline trước khi thay behavior này.
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }
    private void migrate(String target) { flyway(target).migrate(); }
    private void execute(String sql) throws SQLException { try (var statement = db.createStatement()) { statement.execute(sql); } }
    private long number(String sql) throws SQLException { try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); } }
    private List<String> strings(String sql) throws SQLException {
        var rows = new ArrayList<String>();
        try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) { while (result.next()) rows.add(result.getString(1)); }
        return rows;
    }
    private void rejected(String sql, String state, String constraint) throws Exception {
        db.setAutoCommit(false);
        try {
            var failure = assertThrows(SQLException.class, () -> { execute(sql); db.commit(); });
            assertThat(failure.getSQLState()).isEqualTo(state);
            assertThat(failure.getMessage()).contains(constraint);
        } finally { db.rollback(); db.setAutoCommit(true); }
    }
    private static String block(String group, String question, String type, String text, String media, String mediaType) {
        return "INSERT INTO exam_content_blocks(exam_id,block_id,group_id,question_id,block_type,order_index,text_content,media_id,media_type) VALUES('E1','B1',"
                + group + "," + question + ",'" + type + "',1," + text + "," + media + "," + mediaType + ")";
    }
    private void seedNormalized() throws Exception {
        db.setAutoCommit(false);
        execute("INSERT INTO user_accounts(id,username,display_name,role,password_hash) VALUES(100,'schema-owner','MOCK schema owner','PROCTOR','MOCK unusable hash')");
        execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES('E1','MOCK',2,100),('E2','MOCK',1,100)");
        execute("INSERT INTO exam_sources(id,exam_id,source_name,source_path,permission_status,source_sha256) VALUES(1001,'E1','MOCK','MOCK/E1.json','UNKNOWN',repeat('a',64)),(2001,'E2','MOCK','MOCK/E2.json','UNKNOWN',repeat('b',64))");
        execute("INSERT INTO exam_question_groups(exam_id,group_id,section,part,order_index) VALUES('E1','G1','READING',6,1),('E2','G2','READING',7,1)");
        execute("INSERT INTO exam_questions(exam_id,question_id,section,part,group_id,prompt,correct_option,order_index) VALUES('E1','Q1','READING',6,'G1','MOCK','A',1),('E1','Q2','READING',5,NULL,'MOCK','B',2),('E2','Q1','READING',7,'G2','MOCK','A',1)");
        execute("INSERT INTO exam_options(exam_id,question_id,option_id,option_text,order_index) VALUES('E1','Q1','A','MOCK A',1),('E1','Q1','B','MOCK B',2),('E1','Q2','A','MOCK A',1),('E1','Q2','B','MOCK B',2),('E2','Q1','A','MOCK A',1),('E2','Q1','B','MOCK B',2)");
        execute("INSERT INTO exam_media_assets(exam_id,media_id,media_type,storage_status,local_file,mime_type,size_bytes,sha256) VALUES('E1','PHOTO','IMAGE','LOCAL_VERIFIED','MOCK.png','image/png',8,repeat('a',64)),('E1','AUDIO','AUDIO','REMOTE_REFERENCE',NULL,NULL,NULL,NULL),('E2','FOREIGN','IMAGE','REMOTE_REFERENCE',NULL,NULL,NULL,NULL)");
        db.commit(); db.setAutoCommit(true);
    }
    private void seedLegacyV10() throws Exception {
        String hash = new BCryptPasswordEncoder().encode(required("TOEIC_SEED_PASSWORD"));
        try (var insert = db.prepareStatement("INSERT INTO user_accounts(id,username,display_name,role,password_hash) VALUES(100,'proctor1','MOCK proctor','PROCTOR',?),(101,'candidate1','MOCK candidate','CANDIDATE',?)")) {
            insert.setString(1, hash); insert.setString(2, hash); insert.executeUpdate();
        }
        execute("INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at) VALUES(101,repeat('c',64),'2026-01-01T00:00:00Z','2030-01-01T00:00:00Z')");
        execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES('LEGACY','MOCK V10',4,100)");
        execute("INSERT INTO exam_questions(exam_id,question_id,section,part,group_id,passage_text,prompt,correct_option,order_index) VALUES('LEGACY','Q1','READING',6,'G6','MOCK passage','MOCK','A',1),('LEGACY','Q2','READING',6,'G6','MOCK passage','MOCK','A',2),('LEGACY','Q3','READING',5,'   ',NULL,'MOCK','A',3),('LEGACY','Q4','READING',5,NULL,NULL,'MOCK','A',4)");
        execute("INSERT INTO exam_options(exam_id,question_id,option_id,option_text,order_index) SELECT 'LEGACY',q.question_id,o.id,'MOCK '||o.id,o.n FROM exam_questions q CROSS JOIN (VALUES('A',1),('B',2)) o(id,n)");
        execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) VALUES('LEGACY-S','LEGACY','MOCK session',3600,100)");
        execute("INSERT INTO exam_session_proctors(session_id,proctor_user_id) VALUES('LEGACY-S',100)");
        execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id,writer_epoch,saved_revision,answers_json,deadline_at) VALUES('LEGACY-A',101,'ACTIVE','LEGACY-S','LEGACY',7,3,'{\"Q1\":\"A\"}','2030-01-01T00:00:00Z')");
        execute("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) VALUES('LEGACY-A',100)");
    }
    private String legacySnapshot() throws Exception {
        var result = new ArrayList<String>();
        for (String table : List.of("user_accounts", "login_sessions", "exams", "exam_options", "exam_sessions", "exam_session_proctors", "monitoring_attempts", "monitoring_proctor_assignments")) {
            result.addAll(strings("SELECT row_to_json(t)::text FROM (SELECT * FROM " + table + ") t ORDER BY row_to_json(t)::text"));
        }
        result.addAll(strings("SELECT row_to_json(t)::text FROM (SELECT id,exam_id,question_id,section,part,NULLIF(btrim(group_id),'') AS normalized_group,passage_text,audio_file,prompt,correct_option,order_index FROM exam_questions ORDER BY id) t"));
        return String.join("\n", result);
    }
    private void startBackend(String target) {
        var properties = new HashMap<String, Object>();
        properties.put("server.port", "0"); properties.put("server.address", "127.0.0.1");
        properties.put("spring.datasource.url", url + "?currentSchema=" + schema);
        properties.put("spring.datasource.username", user); properties.put("spring.datasource.password", password);
        properties.put("spring.flyway.default-schema", schema); properties.put("spring.flyway.schemas", schema);
        if (target != null) properties.put("spring.flyway.target", target);
        properties.put("toeic.seed.password", required("TOEIC_SEED_PASSWORD"));
        properties.put("exam.timeout.scheduler.enabled", "false"); properties.put("toeic.listening.maintenance-enabled", "false");
        properties.put("logging.level.root", "ERROR");
        var app = new SpringApplication(ToeicServerApplication.class); app.setBannerMode(Banner.Mode.OFF);
        app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-ex04-test", properties)));
        context = app.run();
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    private String login(String name) throws Exception {
        return JSON.fromJson(expect(post("/api/v1/auth/login", Map.of("username", name, "password", required("TOEIC_SEED_PASSWORD"), "requestId", id()), null), 200).body(), JsonObject.class).get("token").getAsString();
    }
    private void createSession(String id, String exam, String token) throws Exception {
        expect(post("/api/v1/sessions", new CreateSessionRequest(id, exam, "MOCK session", 3600, List.of("proctor1"), List.of("candidate1")), token), 200);
    }
    private HttpResponse<String> post(String path, Object payload, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(payload)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as("HTTP status; response body intentionally omitted").isEqualTo(status); return response;
    }
    private static ExamQuestionImportDto q(String id, int part, String group, int order) {
        return new ExamQuestionImportDto(id, part <= 4 ? "LISTENING" : "READING", part, group, part >= 6 ? "MOCK passage" : null,
                part <= 4 ? "MOCK.wav" : null, "MOCK prompt", "A", order, List.of(new ExamOptionImportDto("A", "A", 1), new ExamOptionImportDto("B", "B", 2)));
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String required(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException("Missing test environment " + name); return value;
    }
}
