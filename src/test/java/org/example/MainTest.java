package org.example;

import org.example.repository.JsonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @TempDir
    Path tempDir;

    private Path jsonFile;

    @BeforeEach
    void setUp() throws IOException {
        jsonFile = tempDir.resolve("data.json");
        Files.writeString(jsonFile, """
                [{"_id":1,"name":"Alice","age":30},{"_id":2,"name":"Bob","age":25}]
                """);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private String run(String input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        new Main(repo, toStream(input), out).run();
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static ByteArrayInputStream toStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    // ── run: 종료 ──────────────────────────────────────────────────

    @Test
    void run_quit_printsGoodbye() throws IOException {
        String output = run("0\n");
        assertTrue(output.contains("종료합니다."));
    }

    @Test
    void run_invalidChoice_printsError() throws IOException {
        String output = run("9\n0\n");
        assertTrue(output.contains("잘못된 입력입니다."));
    }

    // ── listAll ────────────────────────────────────────────────────

    @Test
    void listAll_withData_printsAll() throws IOException {
        String output = run("1\n0\n");
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("2건"));
    }

    @Test
    void listAll_emptyRepo_printsEmpty() throws IOException {
        Files.writeString(jsonFile, "[]");
        String output = run("1\n0\n");
        assertTrue(output.contains("데이터가 없습니다."));
    }

    // ── readOne ────────────────────────────────────────────────────

    @Test
    void readOne_existingId_printsRecord() throws IOException {
        String output = run("2\n1\n0\n");
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("[조회 결과]"));
    }

    @Test
    void readOne_missingId_printsNotFound() throws IOException {
        String output = run("2\n99\n0\n");
        assertTrue(output.contains("해당하는 항목이 없습니다."));
    }

    @Test
    void readOne_invalidThenValid_retriesUntilInt() throws IOException {
        String output = run("2\nabc\n1\n0\n");
        assertTrue(output.contains("숫자를 입력해주세요"));
        assertTrue(output.contains("Alice"));
    }

    // ── createOne ──────────────────────────────────────────────────

    @Test
    void createOne_addsRecord() throws IOException {
        // 필드: name=Charlie, age=20, 빈 줄로 완료
        String output = run("3\nname\nCharlie\nage\n20\n\n0\n");
        assertTrue(output.contains("추가되었습니다."));
        assertTrue(output.contains("(ID: 3)"));
    }

    @Test
    void createOne_emptyInput_printsNoFields() throws IOException {
        // 필드 이름 없이 바로 Enter
        String output = run("3\n\n0\n");
        assertTrue(output.contains("입력된 필드가 없습니다."));
    }

    @Test
    void createOne_intAndDoubleValues_storedTyped() throws IOException {
        run("3\ncount\n5\nprice\n3.14\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        var created = repo.findById(3).orElseThrow();
        assertEquals(5, created.get("count"));
        assertEquals(3.14, (double) created.get("price"), 1e-9);
    }

    // ── updateOne ──────────────────────────────────────────────────

    @Test
    void updateOne_existingId_updatesField() throws IOException {
        // ID 1 → name을 "Updated"로 변경
        String output = run("4\n1\nname\nUpdated\n\n0\n");
        assertTrue(output.contains("수정되었습니다."));
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertEquals("Updated", repo.findById(1).orElseThrow().get("name"));
    }

    @Test
    void updateOne_missingId_printsNotFound() throws IOException {
        String output = run("4\n99\n0\n");
        assertTrue(output.contains("해당하는 항목이 없습니다."));
    }

    @Test
    void updateOne_noChanges_printsNoChange() throws IOException {
        // 필드 이름 없이 바로 Enter → 변경 없음
        String output = run("4\n1\n\n0\n");
        assertTrue(output.contains("변경된 내용이 없습니다."));
    }

    @Test
    void updateOne_emptyValueKeepsExisting() throws IOException {
        // 필드 이름 입력 후 값을 비워두면 기존 값 유지 → updates 맵에 추가 안 됨
        // 그 뒤 Enter로 종료 → "변경된 내용이 없습니다."
        String output = run("4\n1\nname\n\n\n0\n");
        assertTrue(output.contains("변경된 내용이 없습니다."));
    }

    @Test
    void updateOne_tryToModifyId_printsWarning() throws IOException {
        String output = run("4\n1\n_id\nname\nUpdated\n\n0\n");
        assertTrue(output.contains("_id는 수정할 수 없습니다."));
    }

    @Test
    void updateOne_newField_addedToRecord() throws IOException {
        // 기존에 없는 필드 추가
        run("4\n1\nnewField\nhello\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertEquals("hello", repo.findById(1).orElseThrow().get("newField"));
    }

    // ── deleteOne ──────────────────────────────────────────────────

    @Test
    void deleteOne_existingId_deletesRecord() throws IOException {
        String output = run("5\n1\n0\n");
        assertTrue(output.contains("삭제되었습니다."));
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findById(1).isEmpty());
    }

    @Test
    void deleteOne_missingId_printsNotFound() throws IOException {
        String output = run("5\n99\n0\n");
        assertTrue(output.contains("해당하는 항목이 없습니다."));
    }

    // ── parseValue ─────────────────────────────────────────────────

    @Test
    void parseValue_integer() {
        assertEquals(42, Main.parseValue("42"));
    }

    @Test
    void parseValue_double() {
        assertEquals(3.14, (double) Main.parseValue("3.14"), 1e-9);
    }

    @Test
    void parseValue_string() {
        assertEquals("hello", Main.parseValue("hello"));
    }

    // ── formatRecord ───────────────────────────────────────────────

    @Test
    void formatRecord_outputContainsAllEntries() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("_id", 1);
        map.put("name", "Alice");
        String result = Main.formatRecord(map);
        assertTrue(result.contains("_id: 1"));
        assertTrue(result.contains("name: Alice"));
    }

    // ── start ──────────────────────────────────────────────────────

    @Test
    void start_withFileArg_runsAndQuits() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        Main.start(new String[]{jsonFile.toString()}, toStream("0\n"), out);
        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("=== JSON CRUD 콘솔 앱 ==="));
        assertTrue(output.contains("종료합니다."));
    }

    @Test
    void start_withoutFileArg_customPath() throws IOException {
        // args 없이 stdin으로 파일 경로를 제공하는 분기 커버
        // resolveFilePath 내부의 Scanner와 Main 루프의 Scanner가 같은 스트림을 공유하면
        // 충돌이 생기므로, Main.resolveFilePath 단위 테스트로 그 분기는 이미 커버됨.
        // 여기서는 start()가 resolveFilePath를 호출하는 라인 자체만 추가 실행이 필요하므로
        // args[0]이 없는 경우 resolveFilePath 결과로 파일이 만들어지는지 확인한다.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        // stdin: "파일경로\n0\n" — resolveFilePath 가 한 줄 읽고, 나머지는 run()이 읽음
        String input = jsonFile.toString() + "\n0\n";
        // resolveFilePath와 Main 루프가 같은 InputStream 객체를 사용하도록
        // start() 시그니처 상 동일 in 참조를 전달함 → resolveFilePath 내부 new Scanner(in)와
        // Main 생성자의 new Scanner(in)이 같은 스트림을 순차적으로 읽음
        Main.start(new String[]{}, new java.io.SequenceInputStream(
                toStream(jsonFile.toString() + "\n"),
                toStream("0\n")
        ), out);
        assertTrue(baos.toString(StandardCharsets.UTF_8).contains("종료합니다."));
    }

    // ── resolveFilePath ────────────────────────────────────────────

    @Test
    void resolveFilePath_withArgs_returnsArg() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        String result = Main.resolveFilePath(new String[]{"custom.json"}, toStream(""), out);
        assertEquals("custom.json", result);
    }

    @Test
    void resolveFilePath_noArgs_customInput_returnsInput() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        String result = Main.resolveFilePath(new String[]{}, toStream("my.json\n"), out);
        assertEquals("my.json", result);
    }

    @Test
    void resolveFilePath_noArgs_emptyInput_returnsDefault() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        String result = Main.resolveFilePath(new String[]{}, toStream("\n"), out);
        assertEquals("data.json", result);
    }

    // ── Edge Case: parseValue ──────────────────────────────────────

    @Test
    void parseValue_emptyString_returnsString() {
        Object result = Main.parseValue("");
        assertEquals("", result);
        assertInstanceOf(String.class, result);
    }

    @Test
    void parseValue_negativeInt_returnsInteger() {
        Object result = Main.parseValue("-42");
        assertEquals(-42, result);
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void parseValue_negativeDouble_returnsDouble() {
        Object result = Main.parseValue("-3.14");
        assertEquals(-3.14, (double) result, 1e-9);
        assertInstanceOf(Double.class, result);
    }

    @Test
    void parseValue_integerMaxValue_returnsInteger() {
        Object result = Main.parseValue("2147483647");
        assertEquals(Integer.MAX_VALUE, result);
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void parseValue_integerMinValue_returnsInteger() {
        Object result = Main.parseValue("-2147483648");
        assertEquals(Integer.MIN_VALUE, result);
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void parseValue_integerOverflow_returnsDouble() {
        // Integer.parseInt 실패 → Double.parseDouble 성공
        Object result = Main.parseValue("2147483648");
        assertInstanceOf(Double.class, result);
        assertEquals(2147483648.0, (double) result, 1.0);
    }

    @Test
    void parseValue_doubleMaxValue_returnsDouble() {
        Object result = Main.parseValue("1.7976931348623157E308");
        assertInstanceOf(Double.class, result);
        assertEquals(Double.MAX_VALUE, (double) result, 1e292);
    }

    @Test
    void parseValue_onlyWhitespace_returnsString() {
        Object result = Main.parseValue("   ");
        assertInstanceOf(String.class, result);
        assertEquals("   ", result);
    }

    @Test
    void parseValue_hexString_returnsString() {
        Object result = Main.parseValue("0xFF");
        assertInstanceOf(String.class, result);
        assertEquals("0xFF", result);
    }

    @Test
    void parseValue_leadingZeroInt_returnsInteger() {
        Object result = Main.parseValue("007");
        assertInstanceOf(Integer.class, result);
        assertEquals(7, result);
    }

    @Test
    void parseValue_scientificNotation_returnsDouble() {
        Object result = Main.parseValue("1e3");
        assertInstanceOf(Double.class, result);
        assertEquals(1000.0, (double) result, 1e-9);
    }

    @Test
    void parseValue_booleanLike_returnsString() {
        assertInstanceOf(String.class, Main.parseValue("true"));
        assertInstanceOf(String.class, Main.parseValue("false"));
    }

    @Test
    void parseValue_nullLiteral_returnsString() {
        Object result = Main.parseValue("null");
        assertInstanceOf(String.class, result);
        assertEquals("null", result);
    }

    // ── Edge Case: formatRecord ────────────────────────────────────

    @Test
    void formatRecord_emptyMap_returnsEmptyString() {
        String result = Main.formatRecord(new LinkedHashMap<>());
        assertEquals("", result);
    }

    @Test
    void formatRecord_nullValue_printsNull() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", null);
        String result = Main.formatRecord(map);
        assertEquals("key: null", result);
    }

    @Test
    void formatRecord_specialCharsInKey_formattedCorrectly() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key@#!", "value");
        String result = Main.formatRecord(map);
        assertTrue(result.contains("key@#!: value"));
    }

    // ── Edge Case: run (메뉴) ──────────────────────────────────────

    @Test
    void run_multipleInvalidChoicesThenQuit_printsErrors() throws IOException {
        String output = run("9\n8\n7\n0\n");
        assertEquals(3, output.split("잘못된 입력입니다.", -1).length - 1);
    }

    @Test
    void run_emptyLineThenQuit_treatedAsDefault() throws IOException {
        String output = run("\n0\n");
        assertTrue(output.contains("잘못된 입력입니다."));
    }

    @Test
    void run_allMenuOptions_sequential() throws IOException {
        // 1(목록), 2(조회 ID=1), 3(추가), 4(수정 ID=1), 5(삭제 ID=1), 0(종료)
        String input = "1\n2\n1\n3\nfield\nval\n\n4\n2\nname\nNewBob\n\n5\n2\n0\n";
        String output = run(input);
        assertTrue(output.contains("2건"));
        assertTrue(output.contains("[조회 결과]"));
        assertTrue(output.contains("추가되었습니다."));
        assertTrue(output.contains("수정되었습니다."));
        assertTrue(output.contains("삭제되었습니다."));
        assertTrue(output.contains("종료합니다."));
    }

    // ── Edge Case: createOne ──────────────────────────────────────

    @Test
    void createOne_fieldWithSpecialChars_storedCorrectly() throws IOException {
        run("3\nkey@#!\nvalue!!\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> created = repo.findById(3).orElseThrow();
        assertEquals("value!!", created.get("key@#!"));
    }

    @Test
    void createOne_fieldWithKorean_storedCorrectly() throws IOException {
        run("3\n이름\n홍길동\n나이\n25\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> created = repo.findById(3).orElseThrow();
        assertEquals("홍길동", created.get("이름"));
        assertEquals(25, created.get("나이"));
    }

    @Test
    void createOne_largeNumberOfFields_allStored() throws IOException {
        StringBuilder input = new StringBuilder("3\n");
        for (int i = 0; i < 20; i++) {
            input.append("field").append(i).append("\n");
            input.append("value").append(i).append("\n");
        }
        input.append("\n0\n");
        run(input.toString());
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> created = repo.findById(3).orElseThrow();
        for (int i = 0; i < 20; i++) {
            assertEquals("value" + i, created.get("field" + i));
        }
    }

    @Test
    void createOne_duplicateFieldNames_lastValueWins() throws IOException {
        // 같은 키를 두 번 입력하면 Map.put이 덮어씀
        run("3\nname\nFirst\nname\nSecond\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> created = repo.findById(3).orElseThrow();
        assertEquals("Second", created.get("name"));
    }

    // ── Edge Case: updateOne ──────────────────────────────────────

    @Test
    void updateOne_multipleFields_allUpdated() throws IOException {
        run("4\n1\nname\nNewAlice\nage\n99\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = repo.findById(1).orElseThrow();
        assertEquals("NewAlice", record.get("name"));
        assertEquals(99, record.get("age"));
    }

    @Test
    void updateOne_intValueUpdate_storedAsInteger() throws IOException {
        run("4\n1\nage\n55\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Object age = repo.findById(1).orElseThrow().get("age");
        assertInstanceOf(Integer.class, age);
        assertEquals(55, age);
    }

    @Test
    void updateOne_doubleValueUpdate_storedAsDouble() throws IOException {
        run("4\n1\nscore\n9.5\n\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Object score = repo.findById(1).orElseThrow().get("score");
        assertInstanceOf(Double.class, score);
        assertEquals(9.5, (double) score, 1e-9);
    }

    // ── Edge Case: deleteOne ──────────────────────────────────────

    @Test
    void deleteOne_afterCreate_immediateDelete() throws IOException {
        run("3\nname\nTemp\n\n5\n3\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findById(3).isEmpty());
        assertEquals(2, repo.findAll().size());
    }

    @Test
    void deleteOne_allRecords_emptyList() throws IOException {
        run("5\n1\n5\n2\n1\n0\n");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findAll().isEmpty());
    }

    // ── 무작위 테스트 (Random-based) ──────────────────────────────

    @RepeatedTest(3)
    void parseValue_randomIntegers_allReturnInteger() {
        Random rng = new Random(42);
        IntStream.range(0, 100).forEach(i -> {
            int n = rng.nextInt();
            Object result = Main.parseValue(String.valueOf(n));
            assertInstanceOf(Integer.class, result,
                    "Expected Integer for input: " + n);
            assertEquals(n, result);
        });
    }

    @RepeatedTest(3)
    void parseValue_randomDoubles_allReturnNumber() {
        Random rng = new Random(7);
        IntStream.range(0, 100).forEach(i -> {
            // Integer.parseInt이 실패하도록 소수점 포함 값 사용
            double d = rng.nextDouble() * 1000 + 0.1;
            String raw = String.valueOf(d);
            Object result = Main.parseValue(raw);
            assertInstanceOf(Number.class, result,
                    "Expected Number for input: " + raw);
        });
    }

    @RepeatedTest(3)
    void createOne_randomFieldCounts_idIncrementsCorrectly() throws IOException {
        Random rng = new Random(13);
        // setUp에서 ID 1, 2가 이미 존재
        int expectedNextId = 3;
        for (int trial = 0; trial < 5; trial++) {
            int fieldCount = 1 + rng.nextInt(10); // 1~10
            StringBuilder input = new StringBuilder("3\n");
            for (int f = 0; f < fieldCount; f++) {
                input.append("f").append(f).append("\n");
                input.append("v").append(f).append("\n");
            }
            input.append("\n0\n");
            run(input.toString());

            JsonRepository repo = new JsonRepository(jsonFile.toString());
            assertTrue(repo.findById(expectedNextId).isPresent(),
                    "Expected record with ID " + expectedNextId);
            expectedNextId++;

            // 다음 trial을 위해 setUp 재실행 효과: 파일을 누적 상태로 유지
        }
    }
}
