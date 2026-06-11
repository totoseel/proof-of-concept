package org.example;

import org.example.repository.JsonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
