package org.example.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonRepositoryTest {

    @TempDir
    Path tempDir;

    private Path jsonFile;

    @BeforeEach
    void setUp() {
        jsonFile = tempDir.resolve("test.json");
    }

    // ── 생성자 / load ──────────────────────────────────────────────

    @Test
    void load_emptyFile_startsEmpty() throws IOException {
        Files.writeString(jsonFile, "");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void load_nonExistentFile_startsEmpty() throws IOException {
        JsonRepository repo = new JsonRepository(tempDir.resolve("missing.json").toString());
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void load_existingRecordsWithId_parsedCorrectly() throws IOException {
        Files.writeString(jsonFile, """
                [{"_id":1,"name":"Alice"},{"_id":2,"name":"Bob"}]
                """);
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertEquals(2, repo.findAll().size());
        assertEquals("Alice", repo.findById(1).orElseThrow().get("name"));
    }

    @Test
    void load_recordWithoutId_autoAssignsId() throws IOException {
        Files.writeString(jsonFile, """
                [{"_id":5,"name":"Alice"},{"name":"Bob"}]
                """);
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        // Bob에게 maxId+1 = 6 이 부여되어야 함
        assertTrue(repo.findById(6).isPresent());
        assertEquals("Bob", repo.findById(6).orElseThrow().get("name"));
    }

    @Test
    void load_allRecordsWithoutId_autoAssignsSequentially() throws IOException {
        Files.writeString(jsonFile, """
                [{"name":"Alice"},{"name":"Bob"}]
                """);
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        // 모두 _id 없음 → 1, 2 부여
        assertTrue(repo.findById(1).isPresent());
        assertTrue(repo.findById(2).isPresent());
    }

    // ── findAll ────────────────────────────────────────────────────

    @Test
    void findAll_returnsDefensiveCopy() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"x\":\"a\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        List<Map<String, Object>> list = repo.findAll();
        list.clear();
        assertEquals(1, repo.findAll().size());
    }

    // ── findById ──────────────────────────────────────────────────

    @Test
    void findById_existingId_returnsRecord() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":3,\"val\":\"hello\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Optional<Map<String, Object>> result = repo.findById(3);
        assertTrue(result.isPresent());
        assertEquals("hello", result.get().get("val"));
    }

    @Test
    void findById_missingId_returnsEmpty() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findById(999).isEmpty());
    }

    // ── create ────────────────────────────────────────────────────

    @Test
    void create_onEmptyRepo_assignsIdOne() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "Charlie");

        Map<String, Object> created = repo.create(record);

        assertEquals(1, created.get("_id"));
        assertEquals("Charlie", created.get("name"));
    }

    @Test
    void create_incrementsIdFromMax() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":10,\"name\":\"X\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "Y");
        Map<String, Object> created = repo.create(record);

        assertEquals(11, created.get("_id"));
    }

    @Test
    void create_idFieldInInput_isIgnoredAndReassigned() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("_id", 999);
        record.put("name", "Z");

        Map<String, Object> created = repo.create(record);

        assertEquals(1, created.get("_id"));   // 999 무시 → 1 부여
        assertEquals("Z", created.get("name"));
    }

    @Test
    void create_idIsFirstKey() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "A");
        record.put("age", 20);

        Map<String, Object> created = repo.create(record);

        assertEquals("_id", created.keySet().iterator().next());
    }

    @Test
    void create_persistedToDisk() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "Persisted");
        repo.create(record);

        // 새 인스턴스로 읽어도 데이터가 있어야 함
        JsonRepository repo2 = new JsonRepository(jsonFile.toString());
        assertEquals(1, repo2.findAll().size());
        assertEquals("Persisted", repo2.findById(1).orElseThrow().get("name"));
    }

    // ── update ────────────────────────────────────────────────────

    @Test
    void update_existingId_modifiesFields() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Old\",\"age\":10}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "New");
        boolean result = repo.update(1, fields);

        assertTrue(result);
        assertEquals("New", repo.findById(1).orElseThrow().get("name"));
        assertEquals(10, repo.findById(1).orElseThrow().get("age")); // 나머지 유지
    }

    @Test
    void update_addsNewField() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", "a@b.com");
        repo.update(1, fields);

        assertEquals("a@b.com", repo.findById(1).orElseThrow().get("email"));
    }

    @Test
    void update_idFieldIgnored() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_id", 999);
        fields.put("name", "Bob");
        repo.update(1, fields);

        assertEquals(1, repo.findById(1).orElseThrow().get("_id")); // _id 불변
        assertEquals("Bob", repo.findById(1).orElseThrow().get("name"));
    }

    @Test
    void update_missingId_emptyRepo_returnsFalse() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        boolean result = repo.update(42, Map.of("x", "y"));
        assertFalse(result);
    }

    @Test
    void update_missingId_nonEmptyRepo_returnsFalse() throws IOException {
        // 루프가 실제로 실행되지만 일치하는 id 없음 → for 루프 끝 라인 커버
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        boolean result = repo.update(99, Map.of("name", "X"));
        assertFalse(result);
    }

    @Test
    void update_persistedToDisk() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Before\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        repo.update(1, Map.of("name", "After"));

        JsonRepository repo2 = new JsonRepository(jsonFile.toString());
        assertEquals("After", repo2.findById(1).orElseThrow().get("name"));
    }

    // ── delete ────────────────────────────────────────────────────

    @Test
    void delete_existingId_removesRecord() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"},{\"_id\":2,\"name\":\"Bob\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        boolean result = repo.delete(1);

        assertTrue(result);
        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById(1).isEmpty());
    }

    @Test
    void delete_missingId_returnsFalse() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertFalse(repo.delete(99));
    }

    @Test
    void delete_persistedToDisk() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        repo.delete(1);

        JsonRepository repo2 = new JsonRepository(jsonFile.toString());
        assertTrue(repo2.findAll().isEmpty());
    }

    // ── toInt (Long, Number 분기) ──────────────────────────────────

    @Test
    void load_idStoredAsLong_handledCorrectly() throws IOException {
        // Jackson은 큰 정수를 Long으로 읽을 수 있음 — findById가 정상 동작해야 함
        Files.writeString(jsonFile, "[{\"_id\":2147483648,\"name\":\"BigId\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        // 2147483648L.intValue() == -2147483648; findById에서 같은 변환이 일어나 일치해야 함
        int castId = (int) 2147483648L;
        assertTrue(repo.findById(castId).isPresent());
    }

    // ── toInt 직접 분기 커버 ───────────────────────────────────────

    @Test
    void toInt_integer() {
        assertEquals(42, JsonRepository.toInt(42));
    }

    @Test
    void toInt_long() {
        assertEquals(100, JsonRepository.toInt(100L));
    }

    @Test
    void toInt_otherNumber_usesNumberIntValue() {
        // Float은 Number의 서브타입이지만 Integer/Long이 아님 → Number 분기 실행
        assertEquals(3, JsonRepository.toInt(3.9f));
    }

    @Test
    void toInt_null_returnsZero() {
        assertEquals(0, JsonRepository.toInt(null));
    }

    @Test
    void toInt_string_returnsZero() {
        assertEquals(0, JsonRepository.toInt("notANumber"));
    }
}
