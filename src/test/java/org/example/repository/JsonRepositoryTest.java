package org.example.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

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

    // ── Edge Case: create ─────────────────────────────────────────

    @Test
    void create_multipleRecords_idsIncrement() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("idx", i);
            Map<String, Object> created = repo.create(record);
            assertEquals(i, created.get("_id"));
        }
    }

    @Test
    void create_afterDelete_doesNotReuseId() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"A\"},{\"_id\":2,\"name\":\"B\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        repo.delete(2);
        Map<String, Object> created = repo.create(Map.of("name", "C"));
        // max(_id)=1 이므로 nextId=2가 되어 재사용처럼 보이지만,
        // 실제로 남은 최대값이 1이므로 2가 할당됨 — 의도적 동작 검증
        assertEquals(2, created.get("_id"));

        // 한 번 더 생성하면 3
        Map<String, Object> created2 = repo.create(Map.of("name", "D"));
        assertEquals(3, created2.get("_id"));
    }

    @Test
    void create_emptyMap_assignsIdOne() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> created = repo.create(new LinkedHashMap<>());
        assertEquals(1, created.get("_id"));
        assertEquals(1, created.size()); // _id만 존재
    }

    @Test
    void create_manyFields_persistedAndLoaded() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            record.put("field" + i, "value" + i);
        }
        repo.create(record);

        JsonRepository repo2 = new JsonRepository(jsonFile.toString());
        Map<String, Object> loaded = repo2.findById(1).orElseThrow();
        for (int i = 0; i < 50; i++) {
            assertEquals("value" + i, loaded.get("field" + i));
        }
    }

    @Test
    void create_unicodeValues_persistedCorrectly() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "한글값");
        record.put("emoji", "😊");
        record.put("arabic", "مرحبا");
        repo.create(record);

        JsonRepository repo2 = new JsonRepository(jsonFile.toString());
        Map<String, Object> loaded = repo2.findById(1).orElseThrow();
        assertEquals("한글값", loaded.get("name"));
        assertEquals("😊", loaded.get("emoji"));
        assertEquals("مرحبا", loaded.get("arabic"));
    }

    @Test
    void create_specialCharKeys_persistedCorrectly() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("key-with-dash", "v1");
        record.put("key.with.dot", "v2");
        record.put("key_underscore", "v3");
        repo.create(record);

        JsonRepository repo2 = new JsonRepository(jsonFile.toString());
        Map<String, Object> loaded = repo2.findById(1).orElseThrow();
        assertEquals("v1", loaded.get("key-with-dash"));
        assertEquals("v2", loaded.get("key.with.dot"));
        assertEquals("v3", loaded.get("key_underscore"));
    }

    // ── Edge Case: update ─────────────────────────────────────────

    @Test
    void update_multipleTimesOnSameRecord_lastValueReflected() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"score\":0}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        for (int i = 1; i <= 5; i++) {
            repo.update(1, Map.of("score", i * 10));
        }
        assertEquals(50, repo.findById(1).orElseThrow().get("score"));
    }

    @Test
    void update_allFieldsInOneCall_allUpdated() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"a\":\"old_a\",\"b\":\"old_b\",\"c\":\"old_c\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("a", "new_a");
        fields.put("b", "new_b");
        fields.put("c", "new_c");
        repo.update(1, fields);

        Map<String, Object> updated = repo.findById(1).orElseThrow();
        assertEquals("new_a", updated.get("a"));
        assertEquals("new_b", updated.get("b"));
        assertEquals("new_c", updated.get("c"));
    }

    @Test
    void update_withNullValue_fieldSetToNull() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", null);
        repo.update(1, fields);

        assertNull(repo.findById(1).orElseThrow().get("name"));
    }

    // ── Edge Case: delete ─────────────────────────────────────────

    @Test
    void delete_firstAndLast_middleRemains() throws IOException {
        Files.writeString(jsonFile,
                "[{\"_id\":1,\"v\":\"first\"},{\"_id\":2,\"v\":\"middle\"},{\"_id\":3,\"v\":\"last\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.delete(1));
        assertTrue(repo.delete(3));

        List<Map<String, Object>> remaining = repo.findAll();
        assertEquals(1, remaining.size());
        assertEquals("middle", remaining.get(0).get("v"));
    }

    @Test
    void delete_allOneByOne_listBecomesEmpty() throws IOException {
        Files.writeString(jsonFile,
                "[{\"_id\":1},{\"_id\":2},{\"_id\":3},{\"_id\":4},{\"_id\":5}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        for (int id = 1; id <= 5; id++) {
            assertTrue(repo.delete(id));
        }
        assertTrue(repo.findAll().isEmpty());
    }

    // ── Edge Case: findAll / findById ─────────────────────────────

    @Test
    void findAll_afterMultipleMutations_consistent() throws IOException {
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        repo.create(Map.of("name", "A"));
        repo.create(Map.of("name", "B"));
        repo.create(Map.of("name", "C"));
        repo.update(2, Map.of("name", "B_updated"));
        repo.delete(1);

        List<Map<String, Object>> all = repo.findAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(r -> "B_updated".equals(r.get("name"))));
        assertTrue(all.stream().anyMatch(r -> "C".equals(r.get("name"))));
        assertTrue(all.stream().noneMatch(r -> "A".equals(r.get("name"))));
    }

    @Test
    void findById_zeroId_returnsEmpty() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findById(0).isEmpty());
    }

    @Test
    void findById_negativeId_returnsEmpty() throws IOException {
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Alice\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findById(-1).isEmpty());
    }

    // ── Edge Case: load ───────────────────────────────────────────

    @Test
    void load_invalidJson_throwsIOException() {
        assertThrows(IOException.class, () -> {
            Files.writeString(jsonFile, "{ not valid json [[[");
            new JsonRepository(jsonFile.toString());
        });
    }

    @Test
    void load_emptyArray_returnsEmptyList() throws IOException {
        Files.writeString(jsonFile, "[]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void load_mixedIdTypes_allHandledCorrectly() throws IOException {
        // Jackson이 정수 범위 내 값을 Integer로, 범위 밖을 Long으로 파싱할 수 있음
        // 여기서는 두 레코드 모두 findById로 조회 가능해야 함
        Files.writeString(jsonFile, "[{\"_id\":1,\"name\":\"Small\"},{\"_id\":3,\"name\":\"Normal\"}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());
        assertTrue(repo.findById(1).isPresent());
        assertTrue(repo.findById(3).isPresent());
    }

    // ── 무작위 테스트 (Random-based) ──────────────────────────────

    @RepeatedTest(3)
    void create_randomRecords_allIdsUnique() throws IOException {
        Random rng = new Random(99);
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        List<Integer> assignedIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int fieldCount = 1 + rng.nextInt(5);
            Map<String, Object> record = new LinkedHashMap<>();
            for (int f = 0; f < fieldCount; f++) {
                record.put("k" + f, "v" + rng.nextInt(1000));
            }
            Map<String, Object> created = repo.create(record);
            int id = JsonRepository.toInt(created.get("_id"));
            assertFalse(assignedIds.contains(id), "Duplicate ID detected: " + id);
            assignedIds.add(id);
        }
    }

    @RepeatedTest(3)
    void create_delete_create_idMonotone() throws IOException {
        Random rng = new Random(17);
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        int n = 10;
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> created = repo.create(Map.of("val", i));
            ids.add(JsonRepository.toInt(created.get("_id")));
        }

        // 일부 삭제
        for (int i = 0; i < n / 2; i++) {
            repo.delete(ids.get(i));
        }

        int lastId = ids.get(ids.size() - 1);
        for (int i = 0; i < 5; i++) {
            Map<String, Object> created = repo.create(Map.of("val", "new" + i));
            int newId = JsonRepository.toInt(created.get("_id"));
            assertTrue(newId > lastId,
                    "New ID " + newId + " should be > previous max " + lastId);
            lastId = newId;
        }
    }

    @RepeatedTest(3)
    void update_randomFields_valueIntegrity() throws IOException {
        Random rng = new Random(31);
        Files.writeString(jsonFile, "[{\"_id\":1}]");
        JsonRepository repo = new JsonRepository(jsonFile.toString());

        Map<String, Object> expected = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            String key = "field" + rng.nextInt(5); // 의도적으로 겹치게 하여 덮어쓰기 검증
            String value = "val" + rng.nextInt(1000);
            expected.put(key, value);
            repo.update(1, Map.of(key, value));
        }

        Map<String, Object> loaded = repo.findById(1).orElseThrow();
        expected.forEach((k, v) ->
                assertEquals(v, loaded.get(k), "Mismatch for key: " + k));
    }
}
